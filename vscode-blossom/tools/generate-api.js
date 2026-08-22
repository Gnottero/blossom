#!/usr/bin/env node
/*
 * Generates `data/api.json` by reading the sources of the mod directly.
 *
 * The extension must never contain a hand-written list of functions: that goes stale with the first
 * symbol added. There are three sources of truth, all in the repo:
 *
 *   StdLib.java      the `core.*` modules and the prelude
 *   BlossomApi.java  the `core.mc.*` modules (with the Set<String> fields spread over api/ classes)
 *   Event.java       the catalogue of events, with arity and parameters from the javadoc
 *
 * Signatures and short descriptions come from the documentation in docs/api/.
 *
 * Usage:  node tools/generate-api.js [repo-path]
 */

'use strict';

const fs = require('fs');
const path = require('path');

const REPO = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const OUT = path.join(__dirname, '..', 'data', 'api.json');

// ---------------------------------------------------------------- helpers

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

/**
 * A java source without its comments, string literals left alone.
 *
 * The scans below read lists of names written as literals, and a comment that quotes one - the line
 * of `PRELUDE` that explains what the tokenizer builds writes `"...${...}..."` - would otherwise end
 * up in the api as a symbol nobody can call. Block comments go too, because the prose is
 * italian and an apostrophe there would read as the start of a literal; the catalogue of the events
 * needs its javadoc and reads the file with `read`.
 */
function readJava(file) {
    const source = read(file);
    let out = '';
    let i = 0;
    while (i < source.length) {
        const c = source[i];
        if (c === '"' || c === "'") {
            const quote = c;
            out += c;
            i++;
            while (i < source.length) {
                out += source[i];
                if (source[i] === '\\') {
                    out += source[i + 1] || '';
                    i += 2;
                    continue;
                }
                i++;
                if (source[i - 1] === quote) break;
            }
            continue;
        }
        if (c === '/' && source[i + 1] === '/') {
            while (i < source.length && source[i] !== '\n') i++;
            continue;
        }
        if (c === '/' && source[i + 1] === '*') {
            const end = source.indexOf('*/', i + 2);
            i = end === -1 ? source.length : end + 2;
            continue;
        }
        out += c;
        i++;
    }
    return out;
}

function fail(message) {
    console.error('generate-api: ' + message);
    process.exit(1);
}

// ---------------------------------- index of the Set<String> and Map<String, String>

/** `Class.FIELD` -> raw text of the initialiser, to resolve cross references */
const fields = new Map();

const FIELD_RE =
    /(?:public|private|protected)?\s*static\s+final\s+(?:java\.util\.)?Set<String>\s+(\w+)\s*=\s*([\s\S]*?);\s*\n/g;

/*
 * A module whose public names differ from the implementations declares them as a map, `public name`
 * -> `builtin name` (see StdLib.implementationOf). Only the keys are the API: `core.http` exports
 * `get`, which is registered as `http_get` because the flat map of builtins already has a `get`.
 */
const MAP_FIELD_RE =
    /(?:public|private|protected)?\s*static\s+final\s+(?:java\.util\.)?Map<String,\s*String>\s+(\w+)\s*=\s*([\s\S]*?);\s*\n/g;

for (const file of [
    ...walk(path.join(REPO, 'blossom-core', 'src', 'main', 'java')),
    ...walk(path.join(REPO, 'blossom', 'src', 'main', 'java'))
]) {
    const source = readJava(file);
    const className = path.basename(file, '.java');
    let match;
    while ((match = FIELD_RE.exec(source)) !== null) {
        fields.set(className + '.' + match[1], match[2]);
    }
    while ((match = MAP_FIELD_RE.exec(source)) !== null) {
        const literals = (match[2].match(/"([^"\\]*)"/g) || []).map((literal) => literal.slice(1, -1));
        // one literal in two is a key, and the keys are the names written in a script
        const keys = literals.filter((_, index) => index % 2 === 0);
        fields.set(className + '.' + match[1], keys.map((key) => `"${key}"`).join(', '));
    }
}

/**
 * Resolves an initialiser into a list of symbols: the string literals it contains, plus the symbols
 * of the fields it mentions (`Components.MODULE.stream()`, `Io.MODULE.toArray(...)`).
 */
function resolveSymbols(expression, seen = new Set()) {
    const symbols = new Set();
    for (const literal of expression.match(/"([^"\\]*)"/g) || []) {
        symbols.add(literal.slice(1, -1));
    }
    // lookahead: the references overlap (`com.gnottero.blossom.api.Components.MODULE`), and a normal
    // match would consume `api.Components`, leaving out the pair that matters
    for (const reference of expression.matchAll(/(?=\b(\w+)\.(\w+)\b)/g)) {
        const key = reference[1] + '.' + reference[2];
        if (!fields.has(key) || seen.has(key)) continue;
        seen.add(key);
        for (const symbol of resolveSymbols(fields.get(key), seen)) symbols.add(symbol);
    }
    return symbols;
}

// ----------------------------------------------------------- the modules

const stdlib = readJava(path.join(REPO, 'blossom-core/src/main/java/blossom/language/StdLib.java'));

/** nome modulo → Set di simboli */
const modules = new Map();

for (const match of stdlib.matchAll(/\bmodule\("([^"]+)"\s*,([\s\S]*?)\);/g)) {
    const initialiser = match[2];
    // a module declared with a map has its public names as keys and the builtins as values: one
    // literal in two is the API, the other is where it is implemented
    if (/java\.util\.Map\.(ofEntries|of)\(/.test(initialiser)) {
        const literals = (initialiser.match(/"([^"\\]*)"/g) || []).map((l) => l.slice(1, -1));
        modules.set(match[1], new Set(literals.filter((_, index) => index % 2 === 0)));
        continue;
    }
    modules.set(match[1], resolveSymbols(initialiser));
}
if (modules.size === 0) fail('no core.* module found in StdLib.java');

const preludeMatch = stdlib.match(/PRELUDE\s*=\s*Set\.of\(([\s\S]*?)\);/);
if (!preludeMatch) fail('PRELUDE not found in StdLib.java');
const prelude = [...resolveSymbols(preludeMatch[1])].filter((name) => name !== 'l' && name !== 'm');

const apiFile = path.join(REPO, 'blossom/src/main/java/com/gnottero/blossom/BlossomApi.java');
if (fs.existsSync(apiFile)) {
    const api = readJava(apiFile);
    for (const match of api.matchAll(/registerModule\("([^"]+)"\s*,\s*(\w+)\)/g)) {
        const raw = fields.get('BlossomApi.' + match[2]);
        if (!raw) fail('field BlossomApi.' + match[2] + ' not found');
        modules.set(match[1], resolveSymbols(raw));
    }
    /*
     * A module of the core the game layer adds symbols to, instead of declaring its own: `core.io`
     * gets `read_nbt`, `write_nbt` and `create_datapack`, which need Minecraft but are not a second
     * way of opening a file. The extension has to show them under `core.io`, or completion would
     * hide three functions that exist.
     */
    for (const match of api.matchAll(/extendModule\("([^"]+)"\s*,\s*(\w+)\)/g)) {
        const raw = fields.get('BlossomApi.' + match[2]);
        if (!raw) fail('field BlossomApi.' + match[2] + ' not found');
        const existing = modules.get(match[1]);
        if (!existing) fail('extendModule("' + match[1] + '") but that module was never declared');
        for (const symbol of resolveSymbols(raw)) existing.add(symbol);
    }
}

// ------------------------------------ descriptions and signatures from docs

const DOCS = path.join(REPO, 'docs', 'api');

/** module name -> one-line description, from the table in Overview.md */
const moduleDocs = new Map();
const overview = path.join(DOCS, 'Overview.md');
if (fs.existsSync(overview)) {
    for (const match of read(overview).matchAll(/^\|\s*\[`([^`]+)`\][^|]*\|\s*([^|]+?)\s*\|/gm)) {
        moduleDocs.set(match[1], match[2]);
    }
}

/**
 * Extracts from the page of a module, for every symbol, the signatures the documentation shows and
 * the sentence around them. It is not a parser: the documentation writes calls as inline code, and
 * that convention is enough to get real signatures instead of `name(...)`.
 */
function docsFor(moduleName, symbols) {
    const page = path.join(DOCS, moduleName + '.md');
    const found = new Map();
    if (!fs.existsSync(page)) return found;
    const text = read(page);

    /*
     * The signatures: `name(arg, arg)` inside inline code.
     *
     * Since the api pages write a call with the alias of its module — `math.sqrt(x)`, not `sqrt(x)`,
     * so that a heading says on its own which module it belongs to — the prefix is optional here and
     * dropped when it is there.
     */
    for (const match of text.matchAll(/`(?:[a-z_][\w]*\.)?([a-z_][\w]*)\(([^`()]*)\)`/g)) {
        if (!symbols.has(match[1])) continue;
        const entry = found.get(match[1]) || { signatures: [], doc: '' };
        const signature = match[1] + '(' + match[2].trim() + ')';
        if (!entry.signatures.includes(signature)) entry.signatures.push(signature);
        found.set(match[1], entry);
    }

    // the description: the paragraph where the symbol appears for the first time
    const paragraphs = text.split(/\n\s*\n/);
    const alias = moduleName.split('.').pop();
    for (const symbol of symbols) {
        const escaped = symbol.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const mention = new RegExp('`(?:' + alias + '\\.)?' + escaped + '[`(]');
        const paragraph = paragraphs.find((p) => !p.startsWith('```') && mention.test(p));
        if (!paragraph) continue;
        const entry = found.get(symbol) || { signatures: [], doc: '' };
        entry.doc = paragraph.replace(/\s*\n\s*/g, ' ').trim().slice(0, 400);
        found.set(symbol, entry);
    }
    return found;
}

// ------------------------------------------------------------ the events

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

// the parameters the javadoc does not declare live in the table in the documentation
const eventDocs = path.join(DOCS, 'core.mc.event.md');
if (fs.existsSync(eventDocs)) {
    const source = read(eventDocs);
    const byName = new Map(events.map((event) => [event.name, event]));
    for (const row of source.matchAll(/^\|\s*`(\w+)`\s*\|\s*`?([^|`]*)`?\s*\|/gm)) {
        const event = byName.get(row[1]);
        if (!event || event.params.length) continue;
        event.params = row[2].split(',').map((p) => p.trim()).filter((p) => p && p !== '...');
    }

    /*
     * The prose of an event: the `#### ` section that names it, down to the next heading.
     *
     * One heading can name more than one event — the pistons, the chunks — and then the same text
     * belongs to all of them: they were written together because they are only understandable
     * together, and splitting the paragraph to give each one a third of it would help nobody.
     */
    for (const section of source.matchAll(/^#### (.+)\n([\s\S]*?)(?=\n#{1,4} |$)/gm)) {
        const named = [...section[1].matchAll(/`(\w+)\(/g)].map((m) => m[1]);
        const body = section[2].trim();
        if (!body) continue;
        for (const name of named) {
            const event = byName.get(name);
            if (event) event.doc = body;
        }
    }
}

// -------------------------------------------------------------- the handbook

/*
 * The prose the editor shows on hover — what a function does, its parameters, what it gives back
 * and at least one example — lives in `data/handbook/<module>.json`, written by hand.
 *
 * The list of symbols still comes only from the sources: the handbook adds description to names it
 * does not decide. That is why a key nobody exports is an error here and not a silent extra entry:
 * a renamed function has to make the generator complain, otherwise the documentation of a symbol
 * that no longer exists stays around for months.
 */

const HANDBOOK = path.join(__dirname, '..', 'data', 'handbook');

function handbookFor(name) {
    const file = path.join(HANDBOOK, name + '.json');
    if (!fs.existsSync(file)) return {};
    try {
        return JSON.parse(read(file));
    } catch (error) {
        fail(`data/handbook/${name}.json is not valid json: ${error.message}`);
    }
}

/** Puts together what the docs scraped and what the handbook says, the handbook winning. */
function describe(symbol, scraped, written) {
    const entry = {
        signatures: scraped.signatures && scraped.signatures.length ? scraped.signatures : [symbol + '(...)'],
        doc: scraped.doc || ''
    };
    if (!written) return entry;
    if (written.signatures && written.signatures.length) entry.signatures = written.signatures;
    if (written.summary) entry.doc = written.summary;
    if (written.params && written.params.length) entry.params = written.params;
    if (written.returns) entry.returns = written.returns;
    if (written.examples && written.examples.length) entry.examples = written.examples;
    if (written.note) entry.note = written.note;
    return entry;
}

const missing = [];
const orphan = [];

// --------------------------------------------------------------- the json

const preludeHandbook = handbookFor('prelude');
const preludeSymbols = {};
for (const name of prelude.sort()) {
    if (!preludeHandbook[name]) missing.push('prelude.' + name);
    preludeSymbols[name] = describe(name, {}, preludeHandbook[name]);
}
for (const key of Object.keys(preludeHandbook)) {
    if (!key.startsWith('$') && !prelude.includes(key)) orphan.push('prelude.' + key);
}

const data = {
    generated: new Date().toISOString().slice(0, 10),
    prelude,
    preludeSymbols,
    constants: ['true', 'false', 'null', 'pi', 'euler'],
    types: ['num', 'str', 'bool', 'list', 'map', 'func', 'null', 'any', 'entity', 'block', 'text', 'nbt', 'screen'],
    keywords: ['module', 'import', 'as', 'pub', 'fn', 'let', 'mut', 'else'],
    modules: {},
    events
};

for (const [name, symbols] of modules) {
    const docs = docsFor(name, symbols);
    const written = handbookFor(name);
    const entry = { doc: moduleDocs.get(name) || '', alias: written.$alias || name.split('.').pop(), symbols: {} };
    for (const symbol of [...symbols].sort()) {
        if (!written[symbol]) missing.push(name + '.' + symbol);
        entry.symbols[symbol] = describe(symbol, docs.get(symbol) || {}, written[symbol]);
    }
    for (const key of Object.keys(written)) {
        if (!key.startsWith('$') && !symbols.has(key)) orphan.push(name + '.' + key);
    }
    data.modules[name] = entry;
}

if (orphan.length) {
    fail(
        'the handbook documents symbols that no module exports any more:\n  ' + orphan.join('\n  ') +
        '\nremove them, or fix the name.'
    );
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(data) + '\n');

const symbolCount = Object.values(data.modules).reduce((n, m) => n + Object.keys(m.symbols).length, 0);
console.log(
    `data/api.json: ${Object.keys(data.modules).length} modules, ${symbolCount} symbols, ` +
    `${events.length} events, ${prelude.length} in the prelude`
);
if (missing.length) {
    console.log(`handbook: ${missing.length} symbols without documentation`);
    for (const name of missing) console.log('  ' + name);
} else {
    console.log('handbook: every symbol documented, with parameters, return value and example');
}
