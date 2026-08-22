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
const docview = require('../src/docview');
const diagnostics = require('../src/diagnostics');
const codeactions = require('../src/codeactions');

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
    assert.ok(api.symbolsOf('core.mc.item').includes('component'), 'symbols from Components are resolved');
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

test('every symbol is documented, with a real signature and at least one example', () => {
    const bad = [];
    const check = (label, info) => {
        const name = label.split('.').pop();
        if (!info.doc) bad.push(label + ': no description');
        if (info.signatures.includes(name + '(...)')) bad.push(label + ': no real signature');
        if (!info.examples || !info.examples.length) bad.push(label + ': no example');
        if (!info.returns) bad.push(label + ': no return value');
        // a function taking no arguments has no parameters to describe: only the others must
        const takesArguments = info.signatures.some((s) => !/\(\s*\)$/.test(s));
        if (takesArguments && (!info.params || !info.params.length)) bad.push(label + ': no parameters');
    };
    for (const moduleName of api.moduleNames()) {
        for (const symbol of api.symbolsOf(moduleName)) {
            check(moduleName + '.' + symbol, api.symbolInfo(moduleName, symbol));
        }
    }
    for (const name of api.prelude()) {
        const info = api.preludeInfo(name);
        assert.ok(info, 'prelude.' + name + ': not documented');
        check('prelude.' + name, info);
    }
    assert.deepStrictEqual(bad, []);
});

test('the examples call the symbol they document', () => {
    const bad = [];
    for (const moduleName of api.moduleNames()) {
        const alias = api.moduleOf(moduleName).alias;
        for (const symbol of api.symbolsOf(moduleName)) {
            const info = api.symbolInfo(moduleName, symbol);
            const call = new RegExp('(^|[^\\w.])' + symbol + '\\s*\\(');
            const shown = info.examples.some((e) => call.test(e) || e.includes(alias + '.' + symbol));
            if (!shown) bad.push(moduleName + '.' + symbol);
        }
    }
    assert.deepStrictEqual(bad, []);
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

test('what is inside ${...} is code, and a single quote is not', () => {
    const index = analyzer.analyze(
        'module a;\nimport "core.list" as list;\nprint("how many: ${list.size(x)}");\nprint(\'${list.nope(x)}\');\n'
    );
    assert.deepStrictEqual(index.members.map((m) => m.alias + '.' + m.symbol), ['list.size']);
});

test('the parameters of a lambda and the names an arm binds are declared names', () => {
    const index = analyzer.analyze(
        'module a;\nlet f = fn(size, other = 1) -> size;\nmatch(v, d: num -> d, else -> 0);\n'
    );
    for (const name of ['size', 'other', 'd']) {
        assert.ok(index.defined.has(name), name + ' is not declared');
    }
});

test('a lambda parameter shadowing a library name is not reported', () => {
    assert.deepStrictEqual(
        codes('module a;\nlet f = fn(size) -> size(1);\n'),
        []
    );
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

test('a name of the language is not a variable, a parameter or a function', () => {
    const variable = diagnostics.analyzeDocument(document('module a;\nlet match = 1;\n'));
    assert.ok(variable.some((d) => d.code === 'reserved-name' && /a variable/.test(d.message)));

    const parameter = diagnostics.analyzeDocument(document('module a;\nfn f(print) -> print;\n'));
    assert.ok(parameter.some((d) => d.code === 'reserved-name' && /a parameter/.test(d.message)));

    const lambda = diagnostics.analyzeDocument(document('module a;\nlet f = fn(loop) -> loop;\n'));
    assert.ok(lambda.some((d) => d.code === 'reserved-name' && /a parameter/.test(d.message)));
});

test('the names the tokenizer builds stay free', () => {
    // `l`, `m` and `concat` are in the prelude but nobody writes them: they remain usable as names
    assert.deepStrictEqual(codes('module a;\nlet concat = 1;\nfn f(l, m) -> l;\n'), []);
});

test('the same parameter twice is reported once, on the second', () => {
    const found = diagnostics.analyzeDocument(document('module a;\nfn f(x, x) -> x;\n'))
        .filter((d) => d.code === 'duplicate-parameter');
    assert.strictEqual(found.length, 1);
    assert.strictEqual(found[0].range.start.character, 8);
});

test('a call written after a value, and a lambda after a pipe, say nothing', () => {
    // `f(1)(2)` is a call on what `f(1)` gave back, and `x |> fn(a) -> a` needs no parentheses
    assert.deepStrictEqual(codes('module a;\nfn add(a) -> fn(b) -> a + b;\nfn f() -> add(1)(2);\n'), []);
    assert.deepStrictEqual(
        codes('module a;\nimport "core.list" as list;\nfn f(xs) -> xs |> list.map(fn(x) -> x * 2);\n'),
        []
    );
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

test('one quick fix removes every unused import at once', () => {
    const doc = document(
        'module a;\nimport "core.math" as math;\nimport "core.list" as list;\nimport "core.map" as map;\nfn f() -> map.keys({});\n'
    );
    const found = diagnostics.analyzeDocument(doc).filter((d) => d.code === 'unused-import');
    assert.strictEqual(found.length, 2);

    const actions = codeactions.provider.provideCodeActions(doc, found[0].range, { diagnostics: found });
    const all = actions.find((a) => a.title === 'Remove all 2 unused imports' && a.kind === 'QuickFix');
    assert.ok(all, 'the fix for every unused import is missing');
    assert.strictEqual(all.edit.edits.length, 2);
    assert.deepStrictEqual(all.edit.edits.map((e) => e.range.start.line), [1, 2]);
    assert.deepStrictEqual(all.edit.edits.map((e) => e.range.end.line), [2, 3]);
    assert.strictEqual(all.diagnostics.length, 2);

    // the same removal is offered as a fix-all source action, for `source.fixAll` on save
    const fixAll = actions.find((a) => a.kind === 'SourceFixAll');
    assert.ok(fixAll, 'the source.fixAll action is missing');
    assert.strictEqual(fixAll.edit.edits.length, 2);

    // the per-import fix stays: fixing only the one under the cursor is still possible
    assert.strictEqual(actions.filter((a) => a.title === 'Remove the unused import').length, 2);
});

test('a single unused import gets no "remove all" quick fix', () => {
    const doc = document('module a;\nimport "core.math" as math;\nfn f() -> 1;\n');
    const found = diagnostics.analyzeDocument(doc).filter((d) => d.code === 'unused-import');
    const actions = codeactions.provider.provideCodeActions(doc, found[0].range, { diagnostics: found });
    assert.ok(!actions.some((a) => /^Remove all/.test(a.title) && a.kind === 'QuickFix'));
    assert.ok(actions.some((a) => a.kind === 'SourceFixAll'));
});

test('the fix-all source action is offered even without diagnostics in context', () => {
    const doc = document('module a;\nimport "core.math" as math;\nfn f() -> 1;\n');
    const actions = codeactions.provider.provideCodeActions(doc, null, {
        diagnostics: [],
        only: stub.CodeActionKind.SourceFixAll
    });
    assert.strictEqual(actions.length, 1);
    assert.strictEqual(actions[0].kind, 'SourceFixAll');
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

test('application events, declared and raised, are not checked', () => {
    const declared = codes(
        "module a;\nimport \"core.mc.event\" as event;\nevent.declare('my_event', handle);\n"
    );
    assert.ok(!declared.includes('unknown-event'));
    const raised = codes(
        "module a;\nimport \"core.mc.event\" as event;\nevent.emit('my_event', null, 1);\n"
    );
    assert.ok(!raised.includes('unknown-event'));
});

test('an event named with double quotes is checked too', () => {
    const found = codes(
        'module a;\nimport "core.mc.event" as event;\nevent.on("player_connect", f);\n'
    );
    assert.ok(found.includes('unknown-event'));
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

test('hovering a library symbol shows parameters, return value and an example', () => {
    const doc = document('module a;\nimport "core.math" as math;\nmath.sqrt(16);\n');
    const card = requireHover(doc, new stub.Position(2, 7)).contents.value;
    assert.ok(/```blossom\nsqrt\(x\)\n```/.test(card), 'no signature');
    assert.ok(card.includes('**Parameters**') && card.includes('`x`'), 'no parameters');
    assert.ok(card.includes('**Returns**'), 'no return value');
    assert.ok(card.includes('**Example') && card.includes('math.sqrt(16)'), 'no example');
    assert.ok(card.includes('`core.math`'), 'no module');
});

test('hovering a function of the prelude works without an import', () => {
    const doc = document('module a;\nfn f() -> loop(3, print(_));\n');
    const card = requireHover(doc, new stub.Position(1, 12)).contents.value;
    assert.ok(card.includes('loop(times, expression)'), 'no signature');
    assert.ok(card.includes('**Example'), 'no example');
    assert.ok(card.includes('no import needed'), 'not marked as prelude');
});

test('hovering a constant of the language says what it is', () => {
    const doc = document('module a;\nlet x = pi;\n');
    const card = requireHover(doc, new stub.Position(1, 9)).contents.value;
    assert.ok(card.includes('3.141592653589793'));
});

test('hovering a name the loop binds says it is zero outside a loop', () => {
    const doc = document('module a;\nlet x = _i;\n');
    const card = requireHover(doc, new stub.Position(1, 9)).contents.value;
    assert.ok(/Zero outside a loop/.test(card));
});

test('hovering an event shows the handler to write', () => {
    const doc = document("module a;\nimport \"core.mc.event\" as event;\nevent.on('player_jumps', f);\n");
    const card = requireHover(doc, new stub.Position(2, 12)).contents.value;
    assert.ok(card.includes('fn handle_player_jumps(player)'), 'no handler');
    assert.ok(card.includes("event.on('player_jumps'"), 'no registration');
});

test('the call containing the cursor is recognised', () => {
    const text = 'module a;\nimport "core.math" as math;\nmath.max(1, 2';
    const call = providers.enclosingCall(analyzer.mask(text), text.length);
    assert.deepStrictEqual(call, { alias: 'math', name: 'max', argument: 1, piped: false });
});

test('through a pipe the argument being typed is one further along', () => {
    const text = 'module a;\nimport "core.list" as list;\nnumbers |> list.map(';
    const call = providers.enclosingCall(analyzer.mask(text), text.length);
    assert.deepStrictEqual(call, { alias: 'list', name: 'map', argument: 1, piped: true });
});

test('the api viewer has a page per module, with every function on it', () => {
    const data = api.load();
    const page = docview.renderPage(data, 'core.io', 'nonce');
    for (const moduleName of Object.keys(data.modules)) {
        assert.ok(page.includes('id="module-' + moduleName + '"'), 'no page for ' + moduleName);
    }
    for (const symbol of Object.keys(data.modules['core.io'].symbols)) {
        assert.ok(page.includes('data-name="' + symbol + '"'), 'no card for io.' + symbol);
    }
    assert.ok(page.includes('import "core.io" as io;'), 'no import line');
    assert.ok(page.includes('id="module-events"'), 'no events page');
    assert.ok(page.includes('id="module-prelude"'), 'no prelude page');
});

test('the api viewer writes calls the way they are typed, with the alias', () => {
    const page = docview.modulePage('core.http', api.load().modules['core.http']);
    assert.ok(page.includes('http.get(url)'), 'signature without the alias');
    assert.ok(!/&lt;script/.test(page), 'unescaped markup');
});

test('the api tree lists the modules and their functions', () => {
    const tree = new docview.ApiTree();
    const modules = tree.getChildren();
    assert.ok(modules.some((entry) => entry.module === 'core.http'), 'core.http is not in the tree');
    const symbols = tree.getChildren({ module: 'core.http' });
    assert.deepStrictEqual(symbols[0], { module: 'core.http', symbol: 'decode_url' });
    const item = tree.getTreeItem(symbols[0]);
    assert.strictEqual(item.command.command, 'blossom.openApiDocs');
    assert.deepStrictEqual(item.command.arguments, ['core.http', 'decode_url']);
});

/** The hover provider, reached the same way as the completion one. */
function requireHover(doc, position) {
    let captured = null;
    const original = stub.languages.registerHoverProvider;
    stub.languages.registerHoverProvider = (selector, provider) => {
        captured = provider;
        return { dispose() {} };
    };
    providers.register({ subscriptions: [] });
    stub.languages.registerHoverProvider = original;
    const hover = captured.provideHover(doc, position);
    assert.ok(hover, 'no hover here');
    return hover;
}

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


// ------------------------------------------------------------ doc comments

console.log('doc comments');

const docs = require('../src/docs');

const DOCUMENTED = [
    'module main;',
    '',
    '/// Doubles a number.',
    '///',
    '/// The long story, which',
    '/// spans two lines.',
    '///',
    '/// @param x  the number to double',
    '/// @returns  twice x',
    '/// @throws value_exception when x is not a number',
    '/// @example double(21);',
    'pub fn double(x: num) -> x * 2;',
    '',
    '/// How many times it ran.',
    'let mut runs = 0;',
    '',
    'fn undocumented(a, b) -> a + b;',
    ''
].join('\n');

test('a doc block is parsed into summary, body and tags', () => {
    const index = analyzer.analyze(DOCUMENTED);
    const fn = index.functions.find((f) => f.name === 'double');
    assert.strictEqual(fn.doc.summary, 'Doubles a number.');
    assert.strictEqual(fn.doc.body.join(' '), 'The long story, which spans two lines.');
    assert.deepStrictEqual(fn.doc.params, [{ name: 'x', text: 'the number to double' }]);
    assert.strictEqual(fn.doc.returns.text, 'twice x');
    assert.strictEqual(fn.doc.throws[0].type, 'value_exception');
    assert.strictEqual(fn.doc.examples[0].text, 'double(21);');
});

test('a binding takes its doc too, and a plain comment does not', () => {
    const index = analyzer.analyze(DOCUMENTED);
    assert.strictEqual(index.bindings.find((b) => b.name === 'runs').doc.summary, 'How many times it ran.');
    assert.strictEqual(index.functions.find((f) => f.name === 'undocumented').doc, null);
});

test('a doc block detached by a blank line documents nothing', () => {
    const index = analyzer.analyze('module main;\n\n/// Floating.\n\npub fn f() -> 1;\n');
    assert.strictEqual(index.functions[0].doc, null);
});

test('the hover of a local function shows the doc', () => {
    const doc = document(DOCUMENTED);
    const hover = requireHover(doc, new stub.Position(11, 8));
    const text = hover.contents.value;
    assert.ok(text.includes('pub fn double(x: num)'), text);
    assert.ok(text.includes('Doubles a number.'), text);
    assert.ok(text.includes('`x` — the number to double'), text);
    assert.ok(text.includes('**Returns** twice x'), text);
});

test('the completion of a local function carries the doc', () => {
    const doc = document(DOCUMENTED + '\nfn caller() -> d');
    const items = requireCompletion(doc, new stub.Position(18, 16));
    const item = items.find((i) => i.label === 'double');
    assert.ok(item.documentation.value.includes('Doubles a number.'), item.documentation.value);
});

test('the template has one hole per parameter, and tabstops only when asked', () => {
    const target = { name: 'brew', params: [{ name: 'input', type: null }, { name: 'fuel', type: 'num' }] };
    const snippet = docs.template(target, '');
    assert.ok(snippet.includes('@param input ${2:'), snippet);
    assert.ok(snippet.includes('@param fuel ${3:a num}'), snippet);
    assert.ok(snippet.includes('@returns ${4:'), snippet);
    const plain = docs.template(target, '    ', false);
    assert.ok(!plain.includes('${'), plain);
    assert.ok(plain.split('\n').every((line) => line === '' || line.startsWith('    ')), plain);
});

test('the code action offers to document an undocumented declaration, and only that one', () => {
    const doc = document(DOCUMENTED);
    const undocumented = codeactions.provider.provideCodeActions(
        doc, new stub.Range(16, 0, 16, 0), { diagnostics: [] }
    );
    assert.ok(undocumented.some((a) => a.title === "Write the doc comment of 'undocumented'"), 'missing action');
    const documented = codeactions.provider.provideCodeActions(
        doc, new stub.Range(11, 0, 11, 0), { diagnostics: [] }
    );
    assert.ok(!documented.some((a) => a.title.startsWith('Write the doc comment')), 'offered twice');
});

console.log(failures ? `\n${failures} tests failed` : '\nall tests passed');
process.exit(failures ? 1 : 0);
