'use strict';

/*
 * L'indice di un file: preambolo, funzioni, binding, e le posizioni in cui compaiono i simboli.
 *
 * Non è un parser del linguaggio e non vuole diventarlo. È una scansione lineare su una copia del
 * testo in cui commenti e contenuto delle stringhe sono stati sostituiti con spazi: costa una
 * passata sui caratteri più qualche regex, e su un file da mille righe si misura in decimi di
 * millisecondo. Il risultato viene tenuto in cache per versione del documento, quindi digitare non
 * ricalcola niente finché non si smette di digitare.
 */

const api = require('./api');

/** uri → { version, index } */
const cache = new Map();

/**
 * Sostituisce con spazi i commenti e il contenuto delle stringhe, lasciando le andate a capo e le
 * lunghezze dove sono: gli offset del testo mascherato sono gli stessi dell'originale.
 */
function mask(text) {
    const chars = text.split('');
    let i = 0;
    while (i < chars.length) {
        const c = chars[i];
        if (c === '/' && chars[i + 1] === '/') {
            while (i < chars.length && chars[i] !== '\n') chars[i++] = ' ';
        } else if (c === "'" || c === '"') {
            const quote = c;
            i++;
            while (i < chars.length && chars[i] !== quote) {
                if (chars[i] === '\\' && quote === "'") {
                    chars[i] = ' ';
                    i++;
                }
                if (i < chars.length && chars[i] !== '\n') chars[i] = ' ';
                i++;
            }
            if (i < chars.length) i++;
        } else {
            i++;
        }
    }
    return chars.join('');
}

/** Tabella degli offset di inizio riga, per convertire offset → (riga, colonna) in O(log n). */
function lineStarts(text) {
    const starts = [0];
    for (let i = 0; i < text.length; i++) {
        if (text[i] === '\n') starts.push(i + 1);
    }
    return starts;
}

function positionAt(starts, offset) {
    let low = 0;
    let high = starts.length - 1;
    while (low < high) {
        const mid = (low + high + 1) >> 1;
        if (starts[mid] <= offset) low = mid;
        else high = mid - 1;
    }
    return { line: low, character: offset - starts[low] };
}

const IMPORT_RE = /(^|\n)[ \t]*import[ \t]+(["'])([^"']*)\2[ \t]+as[ \t]+([A-Za-z_]\w*)/g;
const MODULE_RE = /(^|\n)[ \t]*module[ \t]+([A-Za-z_][\w.]*)/;
const FUNCTION_RE = /(^|[^\w.])(pub[ \t\n]+)?fn[ \t\n]+([A-Za-z_]\w*)[ \t\n]*\(([^)]*)\)/g;
const LET_RE = /(^|[^\w.])let[ \t\n]+(mut[ \t\n]+)?([A-Za-z_]\w*)/g;
const MEMBER_RE = /\b([A-Za-z_]\w*)[ \t]*\.[ \t]*([A-Za-z_]\w*)/g;
const BARE_CALL_RE = /(^|[^\w.:'"])([a-z_]\w*)[ \t]*\(/g;

/** Analizza `text` e restituisce l'indice. */
function analyze(text) {
    const masked = mask(text);
    const starts = lineStarts(text);
    const at = (offset) => positionAt(starts, offset);

    const index = {
        masked,
        lineStarts: starts,
        module: null,
        imports: new Map(),
        duplicateAliases: [],
        functions: [],
        bindings: [],
        defined: new Set(),
        members: [],
        bareCalls: []
    };

    // dove finisce il preambolo: `module math.vector;` e `import "core.list" as list;` contengono
    // punti che non sono accessi a un modulo, e vanno esclusi dalle scansioni che seguono
    let preambleEnd = 0;

    const moduleMatch = MODULE_RE.exec(masked);
    if (moduleMatch) {
        preambleEnd = moduleMatch.index + moduleMatch[0].length;
        const offset = moduleMatch.index + moduleMatch[1].length;
        index.module = {
            name: moduleMatch[2],
            start: at(masked.indexOf(moduleMatch[2], offset)),
            length: moduleMatch[2].length
        };
    }

    IMPORT_RE.lastIndex = 0;
    let match;
    while ((match = IMPORT_RE.exec(masked)) !== null) {
        // la maschera conserva le lunghezze: il target si legge dal testo vero, agli stessi offset
        const targetOffset = match.index + match[0].indexOf(match[2]) + 1;
        const target = text.substr(targetOffset, match[3].length);
        const alias = match[4];
        const declaration = {
            alias,
            target,
            targetStart: at(targetOffset),
            targetLength: target.length,
            aliasStart: at(match.index + match[0].length - alias.length)
        };
        preambleEnd = Math.max(preambleEnd, match.index + match[0].length);
        if (index.imports.has(alias)) index.duplicateAliases.push(declaration);
        else index.imports.set(alias, declaration);
    }

    FUNCTION_RE.lastIndex = 0;
    while ((match = FUNCTION_RE.exec(masked)) !== null) {
        const name = match[3];
        const nameOffset = match.index + match[0].indexOf(name, match[1].length);
        const params = match[4]
            .split(',')
            .map((raw) => raw.trim())
            .filter(Boolean)
            .map((raw) => {
                const [paramName, type] = raw.split(':').map((part) => part.trim());
                return { name: paramName.replace(/^\.\.\./, ''), type: type || null, variadic: raw.startsWith('...') };
            });
        index.functions.push({
            name,
            params,
            exported: Boolean(match[2]),
            start: at(nameOffset),
            length: name.length
        });
        index.defined.add(name);
        for (const param of params) index.defined.add(param.name);
    }

    LET_RE.lastIndex = 0;
    while ((match = LET_RE.exec(masked)) !== null) {
        const name = match[3];
        const nameOffset = match.index + match[0].lastIndexOf(name);
        index.bindings.push({
            name,
            mutable: Boolean(match[2]),
            start: at(nameOffset),
            length: name.length
        });
        index.defined.add(name);
    }

    MEMBER_RE.lastIndex = preambleEnd;
    while ((match = MEMBER_RE.exec(masked)) !== null) {
        const memberOffset = match.index + match[0].length - match[2].length;
        index.members.push({
            alias: match[1],
            symbol: match[2],
            aliasStart: at(match.index),
            symbolStart: at(memberOffset),
            symbolLength: match[2].length
        });
    }

    BARE_CALL_RE.lastIndex = preambleEnd;
    while ((match = BARE_CALL_RE.exec(masked)) !== null) {
        const nameOffset = match.index + match[1].length;
        index.bareCalls.push({ name: match[2], start: at(nameOffset), length: match[2].length });
    }

    return index;
}

/** L'indice del documento, ricalcolato solo se la versione è cambiata. */
function indexOf(document) {
    const key = document.uri.toString();
    const cached = cache.get(key);
    if (cached && cached.version === document.version) return cached.index;
    const index = analyze(document.getText());
    cache.set(key, { version: document.version, index });
    return index;
}

function forget(document) {
    cache.delete(document.uri.toString());
}

/** Il modulo (built-in o del progetto) a cui un alias importato punta, nella forma `core.list`. */
function targetModule(index, alias) {
    const declaration = index.imports.get(alias);
    if (!declaration) return null;
    return declaration.target.replace(/\//g, '.');
}

/** I simboli di libreria visibili in questo file, come mappa alias → nome modulo built-in. */
function builtinAliases(index) {
    const aliases = new Map();
    for (const alias of index.imports.keys()) {
        const target = targetModule(index, alias);
        if (target && api.moduleOf(target)) aliases.set(alias, target);
    }
    return aliases;
}

module.exports = { analyze, indexOf, forget, targetModule, builtinAliases, mask };
