'use strict';

/*
 * The index of a file: preamble, functions, bindings, and the positions where symbols appear.
 *
 * This is not a parser for the language and does not want to become one. It is a linear scan over a
 * copy of the text where comments and string contents have been replaced with spaces: it costs one
 * pass over the characters plus a few regexes, and on a thousand-line file it is measured in tenths
 * of a millisecond. The result is cached per document version, so typing recomputes nothing until
 * you stop typing.
 */

const api = require('./api');
const docs = require('./docs');

/** uri -> { version, index } */
const cache = new Map();

/**
 * Replaces comments and string contents with spaces, leaving newlines and lengths where they are:
 * offsets into the masked text are the same as in the original.
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
                if (chars[i] === '\\') {
                    chars[i] = ' ';
                    i++;
                    if (i < chars.length && chars[i] !== '\n') chars[i] = ' ';
                    i++;
                    continue;
                }
                // "${...}" is code inside a string: the placeholder stays visible, so the symbols
                // written in it are seen by the scans exactly like the ones outside
                if (quote === '"' && chars[i] === '$' && chars[i + 1] === '{') {
                    chars[i] = ' ';
                    chars[i + 1] = ' ';
                    i += 2;
                    i = skipPlaceholder(chars, i);
                    continue;
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

/**
 * Walks a `${...}` to its closing brace, leaving what is inside alone and blanking the brace itself.
 * Braces inside a single-quoted string do not count, the way the tokenizer reads them.
 */
function skipPlaceholder(chars, from) {
    let depth = 1;
    let inQuote = false;
    let i = from;
    while (i < chars.length) {
        const c = chars[i];
        if (c === "'") inQuote = !inQuote;
        else if (!inQuote && c === '{') depth++;
        else if (!inQuote && c === '}') {
            depth--;
            if (depth === 0) {
                chars[i] = ' ';
                return i + 1;
            }
        }
        i++;
    }
    return i;
}

const DOC_RE = /^[ \t]*\/\/\//;
const COMMENT_RE = /^[ \t]*\/\//;

/**
 * The doc blocks of the file, keyed by the line of the declaration they sit on: a run of `///`
 * lines belongs to the first line under it that is neither blank nor another comment.
 *
 * It reads the original text and not the masked one, because masking is exactly what erases
 * comments - and here the comment is the content.
 */
function docBlocks(text) {
    const lines = text.split('\n');
    const blocks = new Map();
    let run = null;
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (DOC_RE.test(line)) {
            if (!run) run = { lines: [], startLine: i };
            run.lines.push(line);
            run.endLine = i;
            continue;
        }
        if (!run) continue;
        if (line.trim() === '' || COMMENT_RE.test(line)) {
            // a blank line or an ordinary comment cuts the block off from what follows: a doc
            // that is not glued to its declaration documents nothing
            run = null;
            continue;
        }
        blocks.set(i, { doc: docs.parse(run.lines), startLine: run.startLine, endLine: run.endLine });
        run = null;
    }
    return blocks;
}

/** Table of line start offsets, to convert offset -> (line, column) in O(log n). */
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
/* `fn(x, y) -> ...`: a function without a name is a value, and its parameters are names like any other */
const LAMBDA_RE = /(^|[^\w.])fn[ \t\n]*\(([^)]*)\)/g;
/*
 * Every name the arrow binds that no other scan sees: the parameters of `nome(a, b) -> corpo`, which
 * defines a function without `fn`, and the ones a `match` arm introduces (`d -> ...`, `d: num -> ...`).
 *
 * They only ever silence a diagnostic, so catching one name too many is the safe direction.
 */
const ARROW_RE = /(^|[^\w.])([A-Za-z_]\w*)[ \t]*(?::[ \t]*[A-Za-z_]\w*)?[ \t\n]*->/g;
const MEMBER_RE = /\b([A-Za-z_]\w*)[ \t]*\.[ \t]*([A-Za-z_]\w*)/g;
const BARE_CALL_RE = /(^|[^\w.:'"])([a-z_]\w*)[ \t]*\(/g;
const IDENTIFIER_RE = /[A-Za-z_]\w*/g;

/** Analyzes `text` and returns the index. */
function analyze(text) {
    const masked = mask(text);
    const starts = lineStarts(text);
    const at = (offset) => positionAt(starts, offset);

    const blocks = docBlocks(text);
    const documentation = (position) => blocks.get(position.line) || null;

    const index = {
        masked,
        lineStarts: starts,
        docBlocks: blocks,
        module: null,
        imports: new Map(),
        duplicateAliases: [],
        functions: [],
        bindings: [],
        defined: new Set(),
        members: [],
        bareCalls: [],
        preambleEnd: 0,
        aliasUses: new Map()
    };

    // where the preamble ends: `module math.vector;` and `import "core.list" as list;` contain dots
    // that are not module accesses, and must be left out of the scans that follow
    let preambleEnd = 0;

    const moduleMatch = MODULE_RE.exec(masked);
    if (moduleMatch) {
        preambleEnd = moduleMatch.index + moduleMatch[0].length;
        const offset = moduleMatch.index + moduleMatch[1].length;
        const modulePosition = at(masked.indexOf(moduleMatch[2], offset));
        const moduleBlock = documentation(modulePosition);
        index.module = {
            name: moduleMatch[2],
            start: modulePosition,
            length: moduleMatch[2].length,
            doc: moduleBlock ? moduleBlock.doc : null,
            docRange: moduleBlock ? { startLine: moduleBlock.startLine, endLine: moduleBlock.endLine } : null
        };
    }

    IMPORT_RE.lastIndex = 0;
    let match;
    while ((match = IMPORT_RE.exec(masked)) !== null) {
        // the mask preserves lengths: the target is read from the real text, at the same offsets
        const targetOffset = match.index + match[0].indexOf(match[2]) + 1;
        const target = text.substr(targetOffset, match[3].length);
        const alias = match[4];
        // the whole statement, semicolon included: it is what a quick fix has to delete
        const statementStart = match.index + match[1].length;
        const semicolon = masked.indexOf(';', match.index + match[0].length);
        const statementEnd = semicolon === -1 ? match.index + match[0].length : semicolon + 1;
        const declaration = {
            alias,
            target,
            targetStart: at(targetOffset),
            targetLength: target.length,
            aliasStart: at(match.index + match[0].length - alias.length),
            statementStart: at(statementStart),
            statementEnd: at(statementEnd)
        };
        preambleEnd = Math.max(preambleEnd, statementEnd);
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
        const start = at(nameOffset);
        const block = documentation(start);
        index.functions.push({
            name,
            params,
            exported: Boolean(match[2]),
            start,
            length: name.length,
            doc: block ? block.doc : null,
            docRange: block ? { startLine: block.startLine, endLine: block.endLine } : null
        });
        index.defined.add(name);
        for (const param of params) index.defined.add(param.name);
    }

    LAMBDA_RE.lastIndex = 0;
    while ((match = LAMBDA_RE.exec(masked)) !== null) {
        for (const raw of match[2].split(',')) {
            const name = raw.trim().replace(/^\.\.\./, '').split(/[:=]/)[0].trim();
            if (/^[A-Za-z_]\w*$/.test(name)) index.defined.add(name);
        }
    }

    ARROW_RE.lastIndex = 0;
    while ((match = ARROW_RE.exec(masked)) !== null) index.defined.add(match[2]);

    LET_RE.lastIndex = 0;
    while ((match = LET_RE.exec(masked)) !== null) {
        const name = match[3];
        const nameOffset = match.index + match[0].lastIndexOf(name);
        const bindingStart = at(nameOffset);
        const bindingBlock = documentation(bindingStart);
        index.bindings.push({
            name,
            mutable: Boolean(match[2]),
            start: bindingStart,
            length: name.length,
            doc: bindingBlock ? bindingBlock.doc : null,
            docRange: bindingBlock
                ? { startLine: bindingBlock.startLine, endLine: bindingBlock.endLine }
                : null
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

    index.preambleEnd = preambleEnd;

    // how many times each imported alias is named in the body. `alias.symbol` is the common case, but
    // an alias can also travel on its own - passed to a function, or stored in a binding - so any
    // occurrence of the bare identifier counts. Better to stay quiet than to report a used import.
    if (index.imports.size) {
        for (const alias of index.imports.keys()) index.aliasUses.set(alias, 0);
        IDENTIFIER_RE.lastIndex = preambleEnd;
        while ((match = IDENTIFIER_RE.exec(masked)) !== null) {
            const seen = index.aliasUses.get(match[0]);
            if (seen !== undefined) index.aliasUses.set(match[0], seen + 1);
        }
    }

    return index;
}

/** The index of the document, recomputed only when the version changed. */
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

/** The module (built-in or from the project) an imported alias points to, in the form `core.list`. */
function targetModule(index, alias) {
    const declaration = index.imports.get(alias);
    if (!declaration) return null;
    return declaration.target.replace(/\//g, '.');
}

/** The library symbols visible in this file, as a map alias -> built-in module name. */
function builtinAliases(index) {
    const aliases = new Map();
    for (const alias of index.imports.keys()) {
        const target = targetModule(index, alias);
        if (target && api.moduleOf(target)) aliases.set(alias, target);
    }
    return aliases;
}

/** The function or binding declared on that line, or the one whose doc block covers it. */
function declarationAt(index, line) {
    const covers = (item) => item.start.line === line ||
        (item.docRange && line >= item.docRange.startLine && line <= item.docRange.endLine);
    return index.functions.find(covers) || index.bindings.find(covers) || null;
}

module.exports = {
    analyze, indexOf, forget, targetModule, builtinAliases, mask, docBlocks, declarationAt
};
