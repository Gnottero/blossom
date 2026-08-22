'use strict';

/*
 * The diagnostics: what can be said about a file without running it.
 *
 * There is a single criterion - report only what the extension is sure about. On a dynamic language
 * a false positive costs more than a missed error: the author learns to ignore the squiggles, and
 * from then on even the true ones are worth nothing. That is why there is no type checking, no arity
 * checking on the functions of the user, and unresolved symbols are a warning only when called.
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

/** The name a module should have, deduced from its position inside `src/`. */
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

    // --- preamble --------------------------------------------------------

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

    for (const declaration of index.imports.values()) {
        if (index.aliasUses.get(declaration.alias)) continue;
        const diagnostic = make(
            new vscode.Range(
                declaration.statementStart.line,
                declaration.statementStart.character,
                declaration.statementEnd.line,
                declaration.statementEnd.character
            ),
            `Nothing in this file uses '${declaration.alias}'.`,
            vscode.DiagnosticSeverity.Warning,
            'unused-import'
        );
        // greys the line out instead of underlining it: an unused import is untidy, not broken
        diagnostic.tags = [vscode.DiagnosticTag.Unnecessary];
        diagnostics.push(diagnostic);
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

    // --- symbol usage ----------------------------------------------------

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

    // --- names of the language ------------------------------------------
    //
    // `print`, `if`, `match` and the rest cannot become a name of the author: the parser refuses
    // `let match = 1` and `fn f(print) -> ...` the moment it reads the line. Reporting it here says
    // it before the load does, and the rule is exactly the one of `StdLib.isReservedAsAName`.

    const reservedSet = new Set(api.reserved());
    const reservedWhat = (name, what) => `'${name}' is part of the language and cannot be the name ` +
        `of ${what}. Pick another one.`;

    for (const declaration of index.functions) {
        if (reservedSet.has(declaration.name)) {
            diagnostics.push(make(
                rangeOf(declaration.start, declaration.length),
                reservedWhat(declaration.name, 'a function'),
                vscode.DiagnosticSeverity.Error,
                'reserved-name'
            ));
        }
    }

    for (const binding of index.bindings) {
        if (reservedSet.has(binding.name)) {
            diagnostics.push(make(
                rangeOf(binding.start, binding.length),
                reservedWhat(binding.name, 'a variable'),
                vscode.DiagnosticSeverity.Error,
                'reserved-name'
            ));
        }
    }

    for (const signature of [...index.functions, ...index.lambdas]) {
        const seen = new Set();
        for (const param of signature.params) {
            if (reservedSet.has(param.name)) {
                diagnostics.push(make(
                    rangeOf(param.start, param.length),
                    reservedWhat(param.name, 'a parameter'),
                    vscode.DiagnosticSeverity.Error,
                    'reserved-name'
                ));
                continue;
            }
            // a parameter declared twice binds the argument to one name only: the parser refuses it
            if (seen.has(param.name)) {
                diagnostics.push(make(
                    rangeOf(param.start, param.length),
                    `Parameter '${param.name}' is declared twice in the same signature.`,
                    vscode.DiagnosticSeverity.Error,
                    'duplicate-parameter'
                ));
            }
            seen.add(param.name);
        }
    }

    const preludeSet = new Set(api.prelude());
    for (const call of index.bareCalls) {
        if (preludeSet.has(call.name) || index.defined.has(call.name)) continue;
        const home = api.homeModuleOf(call.name);
        if (!home) continue; // a function of the user defined elsewhere: none of our business
        const alias = home.split('.').pop();
        diagnostics.push(make(
            rangeOf(call.start, call.length),
            `'${call.name}' lives in '${home}' and nothing here imports it. ` +
            `Add: import "${home}" as ${alias};`,
            vscode.DiagnosticSeverity.Warning,
            'missing-import'
        ));
    }

    // --- events ----------------------------------------------------------

    const eventAlias = [...aliases.entries()].filter(([, name]) => name === 'core.mc.event').map(([a]) => a);
    if (eventAlias.length) {
        // `declare` and `emit` are left out on purpose: those are the ones that also take the name
        // of an application event, which no catalogue knows about
        const pattern = new RegExp(
            `\\b(${eventAlias.join('|')})[ \\t]*\\.[ \\t]*(on|off|describe|listeners)[ \\t]*\\([ \\t]*(['"])([^'"]*)\\3`,
            'g'
        );
        while ((match = pattern.exec(text)) !== null) {
            // the mask blanks comments and strings: if a quote is still there, it is real code
            // match[0] ends with 'name': the opening quote sits two characters before the name
            const quoteOffset = match.index + match[0].length - match[4].length - 2;
            if (masked[quoteOffset] !== match[3]) continue;
            if (api.eventInfo(match[4])) continue;
            const suggestion = api.suggest(match[4], api.events().map((event) => event.name));
            const start = document.positionAt(quoteOffset + 1);
            diagnostics.push(make(
                new vscode.Range(start, document.positionAt(quoteOffset + 1 + match[4].length)),
                `There is no event called '${match[4]}'.` + (suggestion ? ` Did you mean '${suggestion}'?` : ''),
                vscode.DiagnosticSeverity.Warning,
                'unknown-event'
            ));
        }
    }

    return diagnostics;
}

/** Registers the check, debounced: analysis happens when you stop typing, not while you type. */
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
