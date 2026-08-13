'use strict';

/*
 * The extension tests: they run with `node test/run.js`, without vscode and without npm install.
 * `require('vscode')` is served by the stub in test/vscode-stub.js.
 */

const Module = require('module');
const path = require('path');
const assert = require('assert');

const stub = require('./vscode-stub');
const originalResolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) {
    if (request === 'vscode') return require.resolve('./vscode-stub');
    return originalResolve.call(this, request, ...rest);
};
require.cache[require.resolve('./vscode-stub')] = { id: 'vscode', exports: stub, loaded: true };

const api = require('../src/api');
const analyzer = require('../src/analyzer');
const providers = require('../src/providers');
const diagnostics = require('../src/diagnostics');

let failures = 0;
function test(name, body) {
    try {
        body();
        console.log('  ok   ' + name);
    } catch (error) {
        failures++;
        console.log('  FAIL ' + name + '\n       ' + error.message);
    }
}

/** A fake TextDocument: the little the providers actually use. */
let documentVersion = 0;
function document(text, fsPath = '/world/blossom/app.bs') {
    documentVersion++;
    const lines = text.split('\n');
    const starts = [0];
    for (let i = 0; i < lines.length; i++) starts.push(starts[i] + lines[i].length + 1);
    return {
        uri: stub.Uri.file(fsPath),
        version: documentVersion,
        languageId: 'blossom',
        getText: (range) => {
            if (!range) return text;
            return lines[range.start.line].slice(range.start.character, range.end.character);
        },
        lineAt: (line) => ({ text: lines[line] }),
        offsetAt: (position) => starts[position.line] + position.character,
        positionAt: (offset) => {
            let line = 0;
            while (line + 1 < starts.length && starts[line + 1] <= offset) line++;
            return new stub.Position(line, offset - starts[line]);
        },
        getWordRangeAtPosition: (position) => {
            const line = lines[position.line];
            let start = position.character;
            let end = position.character;
            while (start > 0 && /\w/.test(line[start - 1])) start--;
            while (end < line.length && /\w/.test(line[end])) end++;
            return start === end ? null : new stub.Range(position.line, start, position.line, end);
        }
    };
}

const codes = (text, fsPath) =>
    diagnostics.analyzeDocument(document(text, fsPath)).map((d) => d.code);

// -------------------------------------------------------------- the data

console.log('generated data');

test('the core and mc modules are all there', () => {
    const names = api.moduleNames();
    for (const expected of ['core.math', 'core.list', 'core.system', 'core.mc.block', 'core.mc.event']) {
        assert.ok(names.includes(expected), 'missing ' + expected);
    }
});

test('the symbols are the ones registered in the sources', () => {
    assert.ok(api.symbolsOf('core.math').includes('sqrt'));
    assert.ok(api.symbolsOf('core.mc.block').includes('set'));
    assert.ok(api.symbolsOf('core.mc.item').includes('item_component'), 'symbols from Components are resolved');
    assert.ok(api.symbolsOf('core.mc.text').includes('hud'), 'symbols from HudApi are resolved');
    assert.strictEqual(api.homeModuleOf('sqrt'), 'core.math');
    assert.strictEqual(api.homeModuleOf('really_does_not_exist'), null);
});

test('events carry name, arity and parameters', () => {
    const event = api.eventInfo('player_connects');
    assert.deepStrictEqual(event.params, ['player']);
    assert.strictEqual(event.arity, 1);
    assert.ok(api.eventInfo('bell_rings').cancellable);
});

test('the fuzzy suggestion finds the near name and stays quiet on far ones', () => {
    assert.strictEqual(api.suggest('sqtr', api.symbolsOf('core.math')), 'sqrt');
    assert.strictEqual(api.suggest('zzzzzzzz', api.symbolsOf('core.math')), null);
});

// ---------------------------------------------------------- the analysis

console.log('file analysis');

const sample = `module main;

import "core.list" as list;
import "core.mc.event" as event;

let mut counter = 0;
let threshold = 10;

fn private_one(x: num) -> x * 2;

pub fn count(player) -> (
    counter += 1;
    list.map([1, 2], private_one(_))
);

event.on('player_connects', count);
`;

test('preamble, functions and bindings are read', () => {
    const index = analyzer.analyze(sample);
    assert.strictEqual(index.module.name, 'main');
    assert.deepStrictEqual([...index.imports.keys()], ['list', 'event']);
    assert.strictEqual(index.imports.get('list').target, 'core.list');
    assert.deepStrictEqual(index.functions.map((f) => f.name), ['private_one', 'count']);
    assert.strictEqual(index.functions[0].params[0].type, 'num');
    assert.strictEqual(index.functions[1].exported, true);
    assert.deepStrictEqual(index.bindings.map((b) => b.name), ['counter', 'threshold']);
    assert.strictEqual(index.bindings[0].mutable, true);
});

test('comments and strings produce no symbols', () => {
    const index = analyzer.analyze(
        "module a;\n// fn fake(x) -> 1;\nlet s = 'fn hereToo(y) -> 2; list.nonexistent';\n"
    );
    assert.deepStrictEqual(index.functions, []);
    assert.deepStrictEqual(index.members, []);
    assert.deepStrictEqual(index.bindings.map((b) => b.name), ['s']);
});

test('the mask keeps lengths and newlines', () => {
    const text = "let a = 'x\\ny';\n// comment\n";
    const masked = analyzer.mask(text);
    assert.strictEqual(masked.length, text.length);
    assert.strictEqual(masked.split('\n').length, text.split('\n').length);
});

test('symbol positions point where they should', () => {
    const index = analyzer.analyze(sample);
    const count = index.functions.find((f) => f.name === 'count');
    const line = sample.split('\n')[count.start.line];
    assert.strictEqual(line.substr(count.start.character, 5), 'count');
});

// ------------------------------------------------------- the diagnostics

console.log('diagnostics');

test('a correct file produces nothing', () => {
    assert.deepStrictEqual(codes(sample), []);
});

test('import without an alias', () => {
    assert.ok(codes('module a;\nimport "core.list";\n').includes('import-without-alias'));
});

test('duplicate alias', () => {
    const found = codes('module a;\nimport "core.list" as x;\nimport "core.map" as x;\n');
    assert.ok(found.includes('duplicate-alias'));
});

test('built-in module that does not exist, with a suggestion', () => {
    const found = diagnostics.analyzeDocument(document('module a;\nimport "core.lst" as l;\nl.map([], _);\n'));
    assert.ok(found.some((d) => d.code === 'unknown-module' && /core\.list/.test(d.message)));
});

test('symbol the module does not export, with a suggestion', () => {
    const found = diagnostics.analyzeDocument(
        document('module a;\nimport "core.math" as math;\nmath.sqtr(4);\n')
    );
    assert.ok(found.some((d) => d.code === 'unknown-symbol' && /sqrt/.test(d.message)));
});

test('library function used without importing it', () => {
    const found = diagnostics.analyzeDocument(document('module a;\nfn f() -> sqrt(4);\n'));
    assert.ok(found.some((d) => d.code === 'missing-import' && /core\.math/.test(d.message)));
});

test('a function of the user is not a missing import', () => {
    assert.deepStrictEqual(codes('module a;\nfn mine() -> 1;\nfn other() -> mine();\n'), []);
});

test('a parameter shadowing a library name is not reported', () => {
    assert.deepStrictEqual(codes('module a;\nfn f(min) -> min(1);\n'), []);
});

test('an import nothing uses is reported', () => {
    const found = diagnostics.analyzeDocument(
        document('module a;\nimport "core.math" as math;\nimport "core.list" as list;\nfn f() -> list.first([1], _);\n')
    );
    const unused = found.filter((d) => d.code === 'unused-import');
    assert.strictEqual(unused.length, 1);
    assert.ok(/'math'/.test(unused[0].message));
    assert.deepStrictEqual(unused[0].tags, [stub.DiagnosticTag.Unnecessary]);
    // the whole statement is greyed out, semicolon included
    assert.strictEqual(unused[0].range.start.line, 1);
    assert.strictEqual(unused[0].range.start.character, 0);
    assert.strictEqual(unused[0].range.end.character, 'import "core.math" as math;'.length);
});

test('an alias named only in a comment or a string counts as unused', () => {
    const found = codes("module a;\nimport \"core.math\" as math;\n// math.sqrt(4);\nlet s = 'math.sqrt';\n");
    assert.ok(found.includes('unused-import'));
});

test('an alias passed around as a value is used', () => {
    const found = codes('module a;\nimport "core.math" as math;\nfn f(g) -> g(1);\nfn h() -> f(math);\n');
    assert.ok(!found.includes('unused-import'));
});

test('event that does not exist, with a suggestion', () => {
    const found = diagnostics.analyzeDocument(
        document("module a;\nimport \"core.mc.event\" as event;\nevent.on('player_connect', f);\n")
    );
    assert.ok(found.some((d) => d.code === 'unknown-event' && /player_connects/.test(d.message)));
});

test('custom events from signal_event are not checked', () => {
    const found = codes(
        "module a;\nimport \"core.mc.event\" as event;\nevent.signal_event('my_event', 1);\n"
    );
    assert.ok(!found.includes('unknown-event'));
});

test('the module name must match the path under src/', () => {
    assert.ok(codes('module wrong;\n', '/app/src/math/vector.bs').includes('module-name-mismatch'));
    assert.deepStrictEqual(codes('module math.vector;\n', '/app/src/math/vector.bs'), []);
});

test('outside src/ the module name is not judged', () => {
    assert.deepStrictEqual(codes('module whatever;\n', '/world/blossom/app.bs'), []);
});

// ----------------------------------------------------------- the providers

console.log('providers');

test('completion after an alias lists the symbols of the module', () => {
    const doc = document('module a;\nimport "core.math" as math;\nmath.\n');
    const list = requireCompletion(doc, new stub.Position(2, 5));
    assert.ok(list.some((item) => item.label === 'sqrt'));
});

test('completion inside an import lists the modules', () => {
    const doc = document('module a;\nimport "\n');
    const list = requireCompletion(doc, new stub.Position(1, 8));
    assert.ok(list.some((item) => item.label === 'core.mc.entity'));
});

test('the call containing the cursor is recognised', () => {
    const text = 'module a;\nimport "core.math" as math;\nmath.max(1, 2';
    const call = providers.enclosingCall(analyzer.mask(text), text.length);
    assert.deepStrictEqual(call, { alias: 'math', name: 'max', argument: 1 });
});

/** The completion provider is internal to its module: it is reached by registering it on a stub. */
function requireCompletion(doc, position) {
    let captured = null;
    const original = stub.languages.registerCompletionItemProvider;
    stub.languages.registerCompletionItemProvider = (selector, provider) => {
        captured = provider;
        return { dispose() {} };
    };
    providers.register({ subscriptions: [] });
    stub.languages.registerCompletionItemProvider = original;
    return captured.provideCompletionItems(doc, position);
}

console.log(failures ? `\n${failures} tests failed` : '\nall tests passed');
process.exit(failures ? 1 : 0);
