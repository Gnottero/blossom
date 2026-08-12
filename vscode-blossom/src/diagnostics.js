'use strict';

/*
 * Le diagnostiche: quello che si può dire di un file senza eseguirlo.
 *
 * Il criterio è uno solo — segnalare solo ciò di cui l'estensione è certa. Un falso positivo su un
 * linguaggio dinamico costa più di un errore mancato: chi scrive impara a ignorare le sottolineature
 * e da quel momento anche quelle vere non servono a niente. Per questo non c'è nessun controllo di
 * tipi, nessun controllo di arità sulle funzioni dell'utente, e i simboli non risolti sono un
 * avviso solo quando sono chiamati.
 */

const vscode = require('vscode');
const path = require('path');
const api = require('./api');
const analyzer = require('./analyzer');

const SOURCE = 'blossom';

function rangeOf(start, length) {
    return new vscode.Range(start.line, start.character, start.line, start.character + length);
}

function make(range, message, severity, code) {
    const diagnostic = new vscode.Diagnostic(range, message, severity);
    diagnostic.source = SOURCE;
    if (code) diagnostic.code = code;
    return diagnostic;
}

/** Il nome che un modulo dovrebbe avere, dedotto dalla sua posizione dentro `src/`. */
function expectedModuleName(document) {
    const filePath = document.uri.fsPath;
    const parts = filePath.split(path.sep);
    const srcIndex = parts.lastIndexOf('src');
    if (srcIndex === -1 || srcIndex === parts.length - 1) return null;
    return parts
        .slice(srcIndex + 1)
        .join('.')
        .replace(/\.bs$/, '');
}

function analyzeDocument(document) {
    const index = analyzer.indexOf(document);
    const text = document.getText();
    const masked = index.masked;
    const diagnostics = [];
    const aliases = analyzer.builtinAliases(index);

    // --- preambolo -------------------------------------------------------

    for (const duplicate of index.duplicateAliases) {
        diagnostics.push(make(
            rangeOf(duplicate.aliasStart, duplicate.alias.length),
            `Alias '${duplicate.alias}' is already used by another import in this file.`,
            vscode.DiagnosticSeverity.Error,
            'duplicate-alias'
        ));
    }

    const NO_ALIAS_RE = /(^|\n)[ \t]*import[ \t]+(["'])[^\n]*?\2[ \t]*;/g;
    let match;
    while ((match = NO_ALIAS_RE.exec(masked)) !== null) {
        const offset = match.index + match[1].length;
        const position = document.positionAt(offset);
        diagnostics.push(make(
            new vscode.Range(position, document.positionAt(match.index + match[0].length)),
            'Every import needs an alias: write import "..." as <alias>;',
            vscode.DiagnosticSeverity.Error,
            'import-without-alias'
        ));
    }

    for (const declaration of index.imports.values()) {
        const target = declaration.target.replace(/\//g, '.');
        if (!target.startsWith('core.') || api.moduleOf(target)) continue;
        const suggestion = api.suggest(target, api.moduleNames());
        diagnostics.push(make(
            rangeOf(declaration.targetStart, declaration.targetLength),
            `There is no built-in module '${target}'.` + (suggestion ? ` Did you mean '${suggestion}'?` : ''),
            vscode.DiagnosticSeverity.Error,
            'unknown-module'
        ));
    }

    const expected = expectedModuleName(document);
    if (index.module && expected && index.module.name !== expected) {
        diagnostics.push(make(
            rangeOf(index.module.start, index.module.length),
            `Module declared as '${index.module.name}' but its position under src/ makes it '${expected}'. ` +
            'The loader checks this before running anything.',
            vscode.DiagnosticSeverity.Warning,
            'module-name-mismatch'
        ));
    }

    // --- uso dei simboli -------------------------------------------------

    const knownAliases = new Set(index.imports.keys());

    for (const member of index.members) {
        const moduleName = aliases.get(member.alias);
        if (moduleName) {
            if (api.symbolInfo(moduleName, member.symbol)) continue;
            const suggestion = api.suggest(member.symbol, api.symbolsOf(moduleName));
            diagnostics.push(make(
                rangeOf(member.symbolStart, member.symbolLength),
                `Module '${moduleName}' does not export '${member.symbol}'.` +
                (suggestion ? ` Did you mean '${suggestion}'?` : ''),
                vscode.DiagnosticSeverity.Error,
                'unknown-symbol'
            ));
            continue;
        }
        if (!knownAliases.has(member.alias) && !index.defined.has(member.alias)) {
            const suggestion = api.suggest(member.alias, [...knownAliases]);
            diagnostics.push(make(
                rangeOf(member.aliasStart, member.alias.length),
                `No import in this file declares the alias '${member.alias}'.` +
                (suggestion ? ` Did you mean '${suggestion}'?` : ''),
                vscode.DiagnosticSeverity.Warning,
                'unknown-alias'
            ));
        }
    }

    const preludeSet = new Set(api.prelude());
    for (const call of index.bareCalls) {
        if (preludeSet.has(call.name) || index.defined.has(call.name)) continue;
        const home = api.homeModuleOf(call.name);
        if (!home) continue; // funzione dell'utente definita altrove: non è affare nostro
        const alias = home.split('.').pop();
        diagnostics.push(make(
            rangeOf(call.start, call.length),
            `'${call.name}' lives in '${home}' and nothing here imports it. ` +
            `Add: import "${home}" as ${alias};`,
            vscode.DiagnosticSeverity.Warning,
            'missing-import'
        ));
    }

    // --- eventi ----------------------------------------------------------

    const eventAlias = [...aliases.entries()].filter(([, name]) => name === 'core.mc.event').map(([a]) => a);
    if (eventAlias.length) {
        const pattern = new RegExp(
            `\\b(${eventAlias.join('|')})[ \\t]*\\.[ \\t]*(on|off|describe|signal_event)[ \\t]*\\([ \\t]*'([^']*)'`,
            'g'
        );
        while ((match = pattern.exec(text)) !== null) {
            // la maschera azzera commenti e stringhe: se lì c'è ancora un apice, è codice vero
            // match[0] finisce con 'nome': l'apice di apertura sta due caratteri prima del nome
            const quoteOffset = match.index + match[0].length - match[3].length - 2;
            if (masked[quoteOffset] !== "'") continue;
            if (match[2] === 'signal_event') continue; // gli eventi personalizzati non stanno nel catalogo
            if (api.eventInfo(match[3])) continue;
            const suggestion = api.suggest(match[3], api.events().map((event) => event.name));
            const start = document.positionAt(quoteOffset + 1);
            diagnostics.push(make(
                new vscode.Range(start, document.positionAt(quoteOffset + 1 + match[3].length)),
                `There is no event called '${match[3]}'.` + (suggestion ? ` Did you mean '${suggestion}'?` : ''),
                vscode.DiagnosticSeverity.Warning,
                'unknown-event'
            ));
        }
    }

    return diagnostics;
}

/** Registra il controllo, debounced: si analizza quando si smette di scrivere, non mentre si scrive. */
function register(context) {
    const collection = vscode.languages.createDiagnosticCollection('blossom');
    context.subscriptions.push(collection);

    const timers = new Map();
    const DEBOUNCE_MS = 300;

    function refresh(document) {
        if (document.languageId !== 'blossom') return;
        if (!vscode.workspace.getConfiguration('blossom').get('diagnostics.enabled', true)) {
            collection.delete(document.uri);
            return;
        }
        collection.set(document.uri, analyzeDocument(document));
    }

    function schedule(document) {
        const key = document.uri.toString();
        clearTimeout(timers.get(key));
        timers.set(key, setTimeout(() => {
            timers.delete(key);
            refresh(document);
        }, DEBOUNCE_MS));
    }

    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument(refresh),
        vscode.workspace.onDidChangeTextDocument((event) => schedule(event.document)),
        vscode.workspace.onDidSaveTextDocument(refresh),
        vscode.workspace.onDidCloseTextDocument((document) => {
            collection.delete(document.uri);
            analyzer.forget(document);
        })
    );
    vscode.workspace.textDocuments.forEach(refresh);
}

module.exports = { register, analyzeDocument };
