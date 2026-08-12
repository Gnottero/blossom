#!/usr/bin/env node
/*
 * Genera `data/api.json` leggendo direttamente le sorgenti del mod.
 *
 * L'estensione non deve mai contenere un elenco di funzioni scritto a mano: quello si disallinea al
 * primo simbolo aggiunto. Le fonti di verità sono tre, tutte nel repo:
 *
 *   StdLib.java      i moduli `core.*` e il prelude
 *   BlossomApi.java  i moduli `core.mc.*` (con i Set<String> sparsi nelle classi api/)
 *   Event.java       il catalogo degli eventi, con arità e parametri dal javadoc
 *
 * Le firme e le descrizioni brevi vengono dalla documentazione in docs/api/.
 *
 * Uso:  node tools/generate-api.js [percorso-repo]
 */

'use strict';

const fs = require('fs');
const path = require('path');

const REPO = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const OUT = path.join(__dirname, '..', 'data', 'api.json');

// ---------------------------------------------------------------- utility

function walk(dir, out = []) {
    if (!fs.existsSync(dir)) return out;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) walk(full, out);
        else if (entry.name.endsWith('.java')) out.push(full);
    }
    return out;
}

function read(file) {
    return fs.readFileSync(file, 'utf8');
}

function fail(message) {
    console.error('generate-api: ' + message);
    process.exit(1);
}

// ------------------------------------------------- indice dei Set<String>

/** `Classe.CAMPO` → testo grezzo dell'inizializzatore, per risolvere i riferimenti incrociati */
const fields = new Map();

const FIELD_RE =
    /(?:public|private|protected)?\s*static\s+final\s+(?:java\.util\.)?Set<String>\s+(\w+)\s*=\s*([\s\S]*?);\s*\n/g;

for (const file of [
    ...walk(path.join(REPO, 'blossom-core', 'src', 'main', 'java')),
    ...walk(path.join(REPO, 'blossom', 'src', 'main', 'java'))
]) {
    const source = read(file);
    const className = path.basename(file, '.java');
    let match;
    while ((match = FIELD_RE.exec(source)) !== null) {
        fields.set(className + '.' + match[1], match[2]);
    }
}

/**
 * Risolve un inizializzatore in un elenco di simboli: le stringhe letterali che contiene, più i
 * simboli dei campi che cita (`Components.MODULE.stream()`, `Io.MODULE.toArray(...)`).
 */
function resolveSymbols(expression, seen = new Set()) {
    const symbols = new Set();
    for (const literal of expression.match(/"([^"\\]*)"/g) || []) {
        symbols.add(literal.slice(1, -1));
    }
    // lookahead: i riferimenti si sovrappongono (`com.gnottero.blossom.api.Components.MODULE`), e una
    // match normale consumerebbe `api.Components` lasciando fuori la coppia che conta
    for (const reference of expression.matchAll(/(?=\b(\w+)\.(\w+)\b)/g)) {
        const key = reference[1] + '.' + reference[2];
        if (!fields.has(key) || seen.has(key)) continue;
        seen.add(key);
        for (const symbol of resolveSymbols(fields.get(key), seen)) symbols.add(symbol);
    }
    return symbols;
}

// ------------------------------------------------------------- i moduli

const stdlib = read(path.join(REPO, 'blossom-core/src/main/java/blossom/language/StdLib.java'));

/** nome modulo → Set di simboli */
const modules = new Map();

for (const match of stdlib.matchAll(/\bmodule\("([^"]+)"\s*,([\s\S]*?)\);/g)) {
    modules.set(match[1], resolveSymbols(match[2]));
}
if (modules.size === 0) fail('nessun modulo core.* trovato in StdLib.java');

const preludeMatch = stdlib.match(/PRELUDE\s*=\s*Set\.of\(([\s\S]*?)\);/);
if (!preludeMatch) fail('PRELUDE non trovato in StdLib.java');
const prelude = [...resolveSymbols(preludeMatch[1])].filter((name) => name !== 'l' && name !== 'm');

const apiFile = path.join(REPO, 'blossom/src/main/java/com/gnottero/blossom/BlossomApi.java');
if (fs.existsSync(apiFile)) {
    const api = read(apiFile);
    for (const match of api.matchAll(/registerModule\("([^"]+)"\s*,\s*(\w+)\)/g)) {
        const raw = fields.get('BlossomApi.' + match[2]);
        if (!raw) fail('campo BlossomApi.' + match[2] + ' non trovato');
        modules.set(match[1], resolveSymbols(raw));
    }
}

// ------------------------------------------- descrizioni e firme dai docs

const DOCS = path.join(REPO, 'docs', 'api');

/** nome modulo → descrizione di una riga, dalla tabella di Overview.md */
const moduleDocs = new Map();
const overview = path.join(DOCS, 'Overview.md');
if (fs.existsSync(overview)) {
    for (const match of read(overview).matchAll(/^\|\s*\[`([^`]+)`\][^|]*\|\s*([^|]+?)\s*\|/gm)) {
        moduleDocs.set(match[1], match[2]);
    }
}

/**
 * Estrae dalla pagina di un modulo, per ogni simbolo, le firme che la documentazione mostra e la
 * frase che le sta intorno. Non è un parser: la documentazione scrive le chiamate in codice inline,
 * e quella convenzione basta per avere firme vere invece di `nome(...)`.
 */
function docsFor(moduleName, symbols) {
    const page = path.join(DOCS, moduleName + '.md');
    const found = new Map();
    if (!fs.existsSync(page)) return found;
    const text = read(page);

    // le firme: `nome(arg, arg)` dentro codice inline
    for (const match of text.matchAll(/`([a-z_][\w]*)\(([^`()]*)\)`/g)) {
        if (!symbols.has(match[1])) continue;
        const entry = found.get(match[1]) || { signatures: [], doc: '' };
        const signature = match[1] + '(' + match[2].trim() + ')';
        if (!entry.signatures.includes(signature)) entry.signatures.push(signature);
        found.set(match[1], entry);
    }

    // la descrizione: il paragrafo in cui il simbolo compare per la prima volta
    const paragraphs = text.split(/\n\s*\n/);
    for (const symbol of symbols) {
        const mention = new RegExp('`' + symbol.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[`(]');
        const paragraph = paragraphs.find((p) => !p.startsWith('```') && mention.test(p));
        if (!paragraph) continue;
        const entry = found.get(symbol) || { signatures: [], doc: '' };
        entry.doc = paragraph.replace(/\s*\n\s*/g, ' ').trim().slice(0, 400);
        found.set(symbol, entry);
    }
    return found;
}

// ------------------------------------------------------------- gli eventi

const events = [];
const eventFile = path.join(REPO, 'blossom/src/main/java/com/gnottero/blossom/event/Event.java');
if (fs.existsSync(eventFile)) {
    const source = read(eventFile);
    const EVENT_RE =
        /(?:\/\*\*\s*\{@code\s*\(([^)]*)\)\}\s*\*\/\s*)?public static final Event \w+ = new Event\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(true|false)/g;
    for (const match of source.matchAll(EVENT_RE)) {
        events.push({
            name: match[2],
            params: (match[1] || '').split(',').map((p) => p.trim()).filter(Boolean),
            arity: Number(match[3]),
            cancellable: match[4] === 'true'
        });
    }
    events.sort((a, b) => a.name.localeCompare(b.name));
}

// i parametri che il javadoc non dichiara stanno nella tabella della documentazione
const eventDocs = path.join(DOCS, 'core.mc.event.md');
if (fs.existsSync(eventDocs)) {
    const byName = new Map(events.map((event) => [event.name, event]));
    for (const row of read(eventDocs).matchAll(/^\|\s*`(\w+)`\s*\|\s*`?([^|`]*)`?\s*\|/gm)) {
        const event = byName.get(row[1]);
        if (!event || event.params.length) continue;
        event.params = row[2].split(',').map((p) => p.trim()).filter((p) => p && p !== '...');
    }
}

// -------------------------------------------------------------- il json

const data = {
    generated: new Date().toISOString().slice(0, 10),
    prelude: prelude.sort(),
    constants: ['true', 'false', 'null', 'pi', 'euler'],
    types: ['num', 'str', 'bool', 'list', 'map', 'func', 'null', 'any', 'entity', 'block', 'text', 'nbt', 'screen'],
    keywords: ['module', 'import', 'as', 'pub', 'fn', 'let', 'mut'],
    modules: {},
    events
};

for (const [name, symbols] of modules) {
    const docs = docsFor(name, symbols);
    const entry = { doc: moduleDocs.get(name) || '', symbols: {} };
    for (const symbol of [...symbols].sort()) {
        const info = docs.get(symbol) || {};
        entry.symbols[symbol] = {
            signatures: info.signatures && info.signatures.length ? info.signatures : [symbol + '(...)'],
            doc: info.doc || ''
        };
    }
    data.modules[name] = entry;
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(data) + '\n');

const symbolCount = Object.values(data.modules).reduce((n, m) => n + Object.keys(m.symbols).length, 0);
console.log(
    `data/api.json: ${Object.keys(data.modules).length} moduli, ${symbolCount} simboli, ` +
    `${events.length} eventi, ${prelude.length} nel prelude`
);
