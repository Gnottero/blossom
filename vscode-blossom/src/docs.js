'use strict';

/*
 * Doc comments: `///` lines above a declaration, with `@tags` inside them.
 *
 *     /// What the function is for, in one line.
 *     ///
 *     /// The rest of the story, as long as it needs to be.
 *     ///
 *     /// @param height  how many blocks tall
 *     /// @returns the score, zero when the tower does not count
 *     /// @throws value_exception when the material is not in the catalogue
 *     /// @example score(12, 'stone');
 *     /// @see tower.registry
 *     /// @deprecated use score_v2
 *     pub fn score(height, material) -> ...;
 *
 * They are ordinary comments for the language - nothing to change in the interpreter - and the
 * editor is what gives them a meaning. This module is the whole convention: parsing, rendering and
 * the template a command writes for you.
 */

const TAG_RE = /^@(param|returns?|throws?|example|see|deprecated)\b[ \t]*(.*)$/;

/** The tags, in the order they are rendered, whatever order they were written in. */
const TAG_ORDER = ['param', 'returns', 'throws', 'example', 'see', 'deprecated'];

/**
 * Parses the lines of a doc block (with or without their `///`) into a structure.
 * Unknown tags are kept as free text, so a typo is visible instead of disappearing.
 */
function parse(lines) {
    const doc = {
        summary: '',
        body: [],
        params: [],
        returns: null,
        throws: [],
        examples: [],
        see: [],
        deprecated: null,
        unknownTags: []
    };
    let current = null;    // the tag being continued by an indented line
    const text = [];

    for (const raw of lines) {
        const line = raw.replace(/^[ \t]*\/\/\/[ \t]?/, '').replace(/\s+$/, '');
        const trimmed = line.trim();
        const tag = TAG_RE.exec(trimmed);
        if (tag) {
            current = pushTag(doc, tag[1], tag[2].trim());
            continue;
        }
        if (/^@\w+/.test(trimmed)) {
            doc.unknownTags.push(trimmed);
            current = null;
            continue;
        }
        if (current) {
            // a line under a tag continues it, so a long description can be wrapped
            if (trimmed === '') {
                current = null;
            } else {
                current.text = current.text ? `${current.text} ${trimmed}` : trimmed;
                continue;
            }
        }
        text.push(line);
    }

    while (text.length && text[0].trim() === '') text.shift();
    while (text.length && text[text.length - 1].trim() === '') text.pop();
    const blank = text.findIndex((line) => line.trim() === '');
    if (blank === -1) {
        doc.summary = text.join(' ').trim();
    } else {
        doc.summary = text.slice(0, blank).join(' ').trim();
        doc.body = text.slice(blank + 1);
    }
    return doc;
}

function pushTag(doc, tag, rest) {
    if (tag === 'param') {
        const match = /^([A-Za-z_]\w*)[ \t]*(.*)$/.exec(rest);
        const entry = { name: match ? match[1] : '', text: match ? match[2].trim() : rest };
        doc.params.push(entry);
        return entry;
    }
    if (tag === 'return' || tag === 'returns') {
        doc.returns = { text: rest };
        return doc.returns;
    }
    if (tag === 'throw' || tag === 'throws') {
        const match = /^([a-z_]\w*)[ \t]+(.*)$/.exec(rest);
        const entry = match ? { type: match[1], text: match[2].trim() } : { type: null, text: rest };
        doc.throws.push(entry);
        return entry;
    }
    if (tag === 'example') {
        const entry = { text: rest };
        doc.examples.push(entry);
        return entry;
    }
    if (tag === 'see') {
        const entry = { text: rest };
        doc.see.push(entry);
        return entry;
    }
    // deprecated
    doc.deprecated = { text: rest };
    return doc.deprecated;
}

/** Whether the block says anything at all: an empty `///` block is the same as no doc. */
function isEmpty(doc) {
    return !doc || (!doc.summary && !doc.body.length && !doc.params.length && !doc.returns &&
        !doc.throws.length && !doc.examples.length && !doc.see.length && !doc.deprecated);
}

/**
 * The markdown of a parsed block, to append after the signature in a hover or a completion.
 * `appendTo` is anything with `appendMarkdown` and `appendCodeblock` (a vscode.MarkdownString).
 */
function render(doc, appendTo) {
    if (isEmpty(doc)) return appendTo;
    if (doc.deprecated) {
        appendTo.appendMarkdown(`\n**Deprecated** — ${doc.deprecated.text || 'do not use it any more'}\n`);
    }
    if (doc.summary) appendTo.appendMarkdown(`\n${doc.summary}\n`);
    if (doc.body.length) appendTo.appendMarkdown(`\n${doc.body.join('\n')}\n`);
    if (doc.params.length) {
        appendTo.appendMarkdown('\n');
        for (const param of doc.params) {
            appendTo.appendMarkdown(`- \`${param.name}\` — ${param.text || '?'}\n`);
        }
    }
    if (doc.returns) appendTo.appendMarkdown(`\n**Returns** ${doc.returns.text || '?'}\n`);
    for (const thrown of doc.throws) {
        appendTo.appendMarkdown(`\n**Throws** ${thrown.type ? `\`${thrown.type}\` ` : ''}${thrown.text}\n`);
    }
    for (const example of doc.examples) {
        if (example.text) appendTo.appendCodeblock(example.text, 'blossom');
    }
    for (const reference of doc.see) {
        appendTo.appendMarkdown(`\nSee ${reference.text}\n`);
    }
    if (doc.unknownTags.length) {
        appendTo.appendMarkdown(`\n${doc.unknownTags.map((t) => `\`${t}\``).join(' ')}\n`);
    }
    return appendTo;
}

/** The doc of one parameter, for the signature help. */
function paramDoc(doc, name) {
    if (!doc) return null;
    const found = doc.params.find((param) => param.name === name);
    return found && found.text ? found.text : null;
}

/**
 * The template for a declaration, as a snippet: one tabstop for the summary and one per tag, so
 * writing it is tab-tab-tab and not a hunt for the right line.
 *
 * `target` is a function of the index (`{ name, params }`) or a binding (`{ name, mutable }`).
 */
function template(target, indent = '', snippet = true) {
    const isFunction = Array.isArray(target.params);
    let stop = 1;
    // with `snippet`, every hole is a tabstop: writing the doc is tab, type, tab. Without it the
    // same text goes in as plain placeholders, because a WorkspaceEdit cannot carry tabstops
    const hole = (text) => (snippet ? `\${${stop++}:${text}}` : text);

    const lines = [];
    lines.push(`/// ${hole(isFunction ? `what ${target.name} does` : `what ${target.name} holds`)}`);
    if (isFunction) {
        lines.push('///');
        for (const param of target.params) {
            lines.push(`/// @param ${param.name} ${hole(param.type ? `a ${param.type}` : 'what it is')}`);
        }
        lines.push(`/// @returns ${hole('what comes back')}`);
    }
    return lines.map((line) => indent + line).join('\n') + '\n' + indent;
}

module.exports = { parse, render, isEmpty, paramDoc, template, TAG_ORDER };
