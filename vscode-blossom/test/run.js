'use strict';

/*
 * I test dell'estensione: girano con `node test/run.js`, senza vscode e senza npm install.
 * `require('vscode')` viene servito dallo stub in test/vscode-stub.js.
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

/** Un finto TextDocument: quel poco che i provider usano davvero. */
let documentVersion = 0;
function document(text, fsPath = '/mondo/blossom/app.bs') {
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

// ----------------------------------------------------------------- i dati

console.log('dati generati');

test('i moduli del core e del layer mc ci sono tutti', () => {
    const names = api.moduleNames();
    for (const expected of ['core.math', 'core.list', 'core.system', 'core.mc.block', 'core.mc.event']) {
        assert.ok(names.includes(expected), 'manca ' + expected);
    }
});

test('i simboli sono quelli registrati nelle sorgenti', () => {
    assert.ok(api.symbolsOf('core.math').includes('sqrt'));
    assert.ok(api.symbolsOf('core.mc.block').includes('set'));
    assert.ok(api.symbolsOf('core.mc.item').includes('item_component'), 'i simboli di Components sono risolti');
    assert.ok(api.symbolsOf('core.mc.text').includes('hud'), 'i simboli di HudApi sono risolti');
    assert.strictEqual(api.homeModuleOf('sqrt'), 'core.math');
    assert.strictEqual(api.homeModuleOf('non_esiste_davvero'), null);
});

test('gli eventi hanno nome, arita e parametri', () => {
    const event = api.eventInfo('player_connects');
    assert.deepStrictEqual(event.params, ['player']);
    assert.strictEqual(event.arity, 1);
    assert.ok(api.eventInfo('bell_rings').cancellable);
});

test('il suggerimento fuzzy trova il nome vicino e tace su quelli lontani', () => {
    assert.strictEqual(api.suggest('sqtr', api.symbolsOf('core.math')), 'sqrt');
    assert.strictEqual(api.suggest('zzzzzzzz', api.symbolsOf('core.math')), null);
});

// ------------------------------------------------------------- l'analisi

console.log('analisi del file');

const esempio = `module main;

import "core.list" as list;
import "core.mc.event" as event;

let mut contatore = 0;
let soglia = 10;

fn privata(x: num) -> x * 2;

pub fn conta(giocatore) -> (
    contatore += 1;
    list.map([1, 2], privata(_))
);

event.on('player_connects', conta);
`;

test('preambolo, funzioni e binding vengono letti', () => {
    const index = analyzer.analyze(esempio);
    assert.strictEqual(index.module.name, 'main');
    assert.deepStrictEqual([...index.imports.keys()], ['list', 'event']);
    assert.strictEqual(index.imports.get('list').target, 'core.list');
    assert.deepStrictEqual(index.functions.map((f) => f.name), ['privata', 'conta']);
    assert.strictEqual(index.functions[0].params[0].type, 'num');
    assert.strictEqual(index.functions[1].exported, true);
    assert.deepStrictEqual(index.bindings.map((b) => b.name), ['contatore', 'soglia']);
    assert.strictEqual(index.bindings[0].mutable, true);
});

test('commenti e stringhe non producono simboli', () => {
    const index = analyzer.analyze(
        "module a;\n// fn finta(x) -> 1;\nlet s = 'fn ancheQui(y) -> 2; list.inesistente';\n"
    );
    assert.deepStrictEqual(index.functions, []);
    assert.deepStrictEqual(index.members, []);
    assert.deepStrictEqual(index.bindings.map((b) => b.name), ['s']);
});

test('la maschera conserva lunghezze e andate a capo', () => {
    const text = "let a = 'x\\ny';\n// commento\n";
    const masked = analyzer.mask(text);
    assert.strictEqual(masked.length, text.length);
    assert.strictEqual(masked.split('\n').length, text.split('\n').length);
});

test('le posizioni dei simboli puntano dove devono', () => {
    const index = analyzer.analyze(esempio);
    const conta = index.functions.find((f) => f.name === 'conta');
    const line = esempio.split('\n')[conta.start.line];
    assert.strictEqual(line.substr(conta.start.character, 5), 'conta');
});

// -------------------------------------------------------- le diagnostiche

console.log('diagnostiche');

test('un file corretto non produce niente', () => {
    assert.deepStrictEqual(codes(esempio), []);
});

test('import senza alias', () => {
    assert.ok(codes('module a;\nimport "core.list";\n').includes('import-without-alias'));
});

test('alias duplicato', () => {
    const found = codes('module a;\nimport "core.list" as x;\nimport "core.map" as x;\n');
    assert.ok(found.includes('duplicate-alias'));
});

test('modulo built-in inesistente, con suggerimento', () => {
    const found = diagnostics.analyzeDocument(document('module a;\nimport "core.lst" as l;\nl.map([], _);\n'));
    assert.ok(found.some((d) => d.code === 'unknown-module' && /core\.list/.test(d.message)));
});

test('simbolo che il modulo non esporta, con suggerimento', () => {
    const found = diagnostics.analyzeDocument(
        document('module a;\nimport "core.math" as math;\nmath.sqtr(4);\n')
    );
    assert.ok(found.some((d) => d.code === 'unknown-symbol' && /sqrt/.test(d.message)));
});

test('funzione di libreria usata senza importarla', () => {
    const found = diagnostics.analyzeDocument(document('module a;\nfn f() -> sqrt(4);\n'));
    assert.ok(found.some((d) => d.code === 'missing-import' && /core\.math/.test(d.message)));
});

test('una funzione dell utente non e un import mancante', () => {
    assert.deepStrictEqual(codes('module a;\nfn mia() -> 1;\nfn altra() -> mia();\n'), []);
});

test('un parametro che ombreggia un nome di libreria non viene segnalato', () => {
    assert.deepStrictEqual(codes('module a;\nfn f(min) -> min(1);\n'), []);
});

test('evento inesistente, con suggerimento', () => {
    const found = diagnostics.analyzeDocument(
        document("module a;\nimport \"core.mc.event\" as event;\nevent.on('player_connect', f);\n")
    );
    assert.ok(found.some((d) => d.code === 'unknown-event' && /player_connects/.test(d.message)));
});

test('gli eventi personalizzati di signal_event non vengono controllati', () => {
    const found = codes(
        "module a;\nimport \"core.mc.event\" as event;\nevent.signal_event('mio_evento', 1);\n"
    );
    assert.ok(!found.includes('unknown-event'));
});

test('il nome del modulo deve combaciare col percorso dentro src/', () => {
    assert.ok(codes('module sbagliato;\n', '/app/src/math/vector.bs').includes('module-name-mismatch'));
    assert.deepStrictEqual(codes('module math.vector;\n', '/app/src/math/vector.bs'), []);
});

test('fuori da src/ il nome del modulo non viene giudicato', () => {
    assert.deepStrictEqual(codes('module qualunque;\n', '/mondo/blossom/app.bs'), []);
});

// ---------------------------------------------------------- i provider

console.log('provider');

test('il completamento dopo un alias elenca i simboli del modulo', () => {
    const doc = document('module a;\nimport "core.math" as math;\nmath.\n');
    const list = requireCompletion(doc, new stub.Position(2, 5));
    assert.ok(list.some((item) => item.label === 'sqrt'));
});

test('il completamento dentro import elenca i moduli', () => {
    const doc = document('module a;\nimport "\n');
    const list = requireCompletion(doc, new stub.Position(1, 8));
    assert.ok(list.some((item) => item.label === 'core.mc.entity'));
});

test('la chiamata che contiene il cursore viene riconosciuta', () => {
    const text = 'module a;\nimport "core.math" as math;\nmath.max(1, 2';
    const call = providers.enclosingCall(analyzer.mask(text), text.length);
    assert.deepStrictEqual(call, { alias: 'math', name: 'max', argument: 1 });
});

/** Il provider di completamento è interno al modulo: lo si raggiunge registrandolo su uno stub. */
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

console.log(failures ? `\n${failures} test falliti` : '\ntutti i test passati');
process.exit(failures ? 1 : 0);
