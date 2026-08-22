'use strict';

/*
 * The editor providers: completion, hover, signature, symbols, definition.
 *
 * They all work on the index of the current document (analyzer.js) and on the generated data
 * (api.js). No separate process, no language server: a `.bs` file is small, and scanning it costs
 * less than the protocol it would take to avoid the scan.
 */

const vscode = require('vscode');
const path = require('path');
const api = require('./api');
const analyzer = require('./analyzer');
const docs = require('./docs');

const SELECTOR = { language: 'blossom', scheme: 'file' };

/** The constants of the language: few enough to be written here, and they never change. */
const CONSTANTS = {
    true: 'The true value. Any number other than zero counts as true too.',
    false: 'The false value — like `0`, the empty string, the empty list and `null`.',
    null: 'The absence of a value. Reading a key that is not there gives this back.',
    pi: 'The ratio between a circumference and its diameter, `3.141592653589793`.',
    euler: 'The base of natural logarithms, `2.718281828459045`.'
};

/*
 * The names a loop binds by itself. Outside a loop they are worth zero rather than being an error,
 * so writing one where no loop ever runs is quiet: the hover says what it is, which is the only
 * warning there is going to be.
 */
const LOOP_VARIABLES = {
    _: 'The element the loop is on — the index in `loop`, the item in `for` and `map`. Zero outside a loop.',
    _i: 'The position of `_`, counting from zero. Zero outside a loop.',
    _a: 'The accumulator carried from one round to the next, in the functions that fold. Zero outside a loop.'
};

/*
 * The card of a function: signatures, what it does, its parameters, what it gives back, and an
 * example. The example is the part that is worth the space - a signature says the shape of a call,
 * a line of real code says what it is for - so it is always there and always last, right above the
 * note, which is where the surprises live.
 */
function describeSymbol(info, footer, withSignatures = true) {
    const markdown = new vscode.MarkdownString();
    // in signature help the signature is already the title of the popup: repeating it is noise
    if (withSignatures) markdown.appendCodeblock(info.signatures.join('\n'), 'blossom');
    if (info.doc) markdown.appendMarkdown('\n' + info.doc + '\n');
    if (info.params && info.params.length) {
        markdown.appendMarkdown('\n**Parameters**\n\n');
        for (const [name, description] of info.params) {
            markdown.appendMarkdown(`- \`${name}\` — ${description}\n`);
        }
    }
    if (info.returns) markdown.appendMarkdown(`\n**Returns** ${info.returns}\n`);
    if (info.examples && info.examples.length) {
        markdown.appendMarkdown(`\n**Example${info.examples.length === 1 ? '' : 's'}**\n`);
        markdown.appendCodeblock(info.examples.join('\n\n'), 'blossom');
    }
    if (info.note) markdown.appendMarkdown(`\n${info.note.replace(/\n/g, ' ')}\n`);
    if (footer) markdown.appendMarkdown(`\n${footer}`);
    return markdown;
}

/** The markdown shown for a library symbol. */
function symbolMarkdown(moduleName, symbol) {
    const info = api.symbolInfo(moduleName, symbol);
    if (!info) return null;
    return describeSymbol(info, `\`${moduleName}\``);
}

/** The markdown shown for a function of the prelude, which needs no import. */
function preludeMarkdown(name) {
    const info = api.preludeInfo(name);
    if (!info) return null;
    return describeSymbol(info, '`prelude` — always available, no import needed');
}

function eventMarkdown(event) {
    const markdown = new vscode.MarkdownString();
    markdown.appendCodeblock(`${event.name}(${event.params.join(', ')})`, 'blossom');
    markdown.appendMarkdown(
        `\n${event.arity} parameter${event.arity === 1 ? '' : 's'}` +
        (event.cancellable ? ', cancellable: returning `true` stops the action' : '') + '\n'
    );
    // what the event actually is, taken from docs/api/core.mc.event.md by the generator
    if (event.doc) markdown.appendMarkdown('\n' + event.doc + '\n');
    // the handler written out: the signature has to match, and the check happens at registration
    const params = event.params.length ? event.params : Array.from({ length: event.arity }, (_, i) => `arg${i + 1}`);
    const handler = `handle_${event.name}`;
    markdown.appendMarkdown('\n**Example**\n');
    markdown.appendCodeblock(
        `fn ${handler}(${params.join(', ')}) -> (\n` +
        `    print('${event.name}');\n` +
        (event.cancellable ? '    false           // true = the action is cancelled\n' : '') +
        `);\n\n` +
        `event.on('${event.name}', ${handler});`,
        'blossom'
    );
    return markdown;
}

/** The signature of a function declared in the file. */
function localSignature(fn) {
    const params = fn.params.map((p) => (p.type ? `${p.name}: ${p.type}` : p.name)).join(', ');
    return `${fn.exported ? 'pub ' : ''}fn ${fn.name}(${params})`;
}

// ------------------------------------------------------------------ completion

const completion = {
    provideCompletionItems(document, position) {
        const index = analyzer.indexOf(document);
        const line = document.lineAt(position.line).text;
        const before = line.slice(0, position.character);
        const items = [];

        // import "..."  ->  the built-in modules
        const importPrefix = /(^|\s)import\s+(["'])([^"']*)$/.exec(before);
        if (importPrefix) {
            for (const name of api.moduleNames()) {
                const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Module);
                const module = api.moduleOf(name);
                item.detail = module.doc;
                item.documentation = new vscode.MarkdownString(
                    `${Object.keys(module.symbols).length} symbols`
                );
                items.push(item);
            }
            return items;
        }

        // alias....  ->  the symbols of the module
        const memberPrefix = /([A-Za-z_]\w*)\s*\.\s*(\w*)$/.exec(before);
        if (memberPrefix) {
            const alias = memberPrefix[1];
            const target = analyzer.targetModule(index, alias);
            if (target && api.moduleOf(target)) {
                for (const symbol of api.symbolsOf(target)) {
                    const item = new vscode.CompletionItem(symbol, vscode.CompletionItemKind.Function);
                    const info = api.symbolInfo(target, symbol);
                    item.detail = info.signatures[0];
                    item.documentation = symbolMarkdown(target, symbol);
                    item.insertText = new vscode.SnippetString(`${symbol}($0)`);
                    items.push(item);
                }
            }
            return items;
        }

        // inside the quotes of an event handler  ->  the event names
        const eventPrefix = /\.\s*(on|off|describe|declare|emit)\s*\(\s*(["'])([^"']*)$/.exec(before);
        if (eventPrefix) {
            for (const event of api.events()) {
                const item = new vscode.CompletionItem(event.name, vscode.CompletionItemKind.Event);
                item.detail = `(${event.params.join(', ')})`;
                item.documentation = eventMarkdown(event);
                items.push(item);
            }
            return items;
        }

        // otherwise: whatever is visible here
        for (const keyword of api.keywords()) {
            items.push(new vscode.CompletionItem(keyword, vscode.CompletionItemKind.Keyword));
        }
        for (const name of api.prelude()) {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Function);
            const info = api.preludeInfo(name);
            item.detail = info ? info.signatures[0] : 'prelude';
            item.documentation = preludeMarkdown(name);
            items.push(item);
        }
        for (const name of api.constants()) {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Constant);
            if (CONSTANTS[name]) item.documentation = new vscode.MarkdownString(CONSTANTS[name]);
            items.push(item);
        }
        for (const [alias, declaration] of index.imports) {
            const item = new vscode.CompletionItem(alias, vscode.CompletionItemKind.Module);
            item.detail = declaration.target;
            items.push(item);
        }
        for (const fn of index.functions) {
            const item = new vscode.CompletionItem(fn.name, vscode.CompletionItemKind.Function);
            item.detail = localSignature(fn);
            if (!docs.isEmpty(fn.doc)) item.documentation = docs.render(fn.doc, new vscode.MarkdownString());
            item.insertText = new vscode.SnippetString(
                `${fn.name}(${fn.params.map((p, i) => `\${${i + 1}:${p.name}}`).join(', ')})`
            );
            items.push(item);
        }
        for (const binding of index.bindings) {
            const item = new vscode.CompletionItem(binding.name, vscode.CompletionItemKind.Variable);
            item.detail = binding.mutable ? 'let mut' : 'let';
            if (!docs.isEmpty(binding.doc)) {
                item.documentation = docs.render(binding.doc, new vscode.MarkdownString());
            }
            items.push(item);
        }
        for (const implicit of ['_', '_i', '_a']) {
            const item = new vscode.CompletionItem(implicit, vscode.CompletionItemKind.Variable);
            item.detail = { _: 'current element', _i: 'index', _a: 'accumulator' }[implicit];
            items.push(item);
        }
        return items;
    }
};

// ---------------------------------------------------------------------- hover

const hover = {
    provideHover(document, position) {
        const index = analyzer.indexOf(document);
        const range = document.getWordRangeAtPosition(position);
        if (!range) return null;
        const word = document.getText(range);
        const line = document.lineAt(position.line).text;

        // alias.symbol
        const before = line.slice(0, range.start.character);
        const memberOf = /([A-Za-z_]\w*)\s*\.\s*$/.exec(before);
        if (memberOf) {
            const target = analyzer.targetModule(index, memberOf[1]);
            if (target) {
                const markdown = symbolMarkdown(target, word);
                if (markdown) return new vscode.Hover(markdown, range);
            }
        }

        // an alias
        const declaration = index.imports.get(word);
        if (declaration && line.slice(range.end.character).startsWith('.')) {
            const target = declaration.target.replace(/\//g, '.');
            const module = api.moduleOf(target);
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(`import "${declaration.target}" as ${word};`, 'blossom');
            if (module) markdown.appendMarkdown('\n' + module.doc);
            return new vscode.Hover(markdown, range);
        }

        // the name of this module, when it carries a doc block
        if (index.module && index.module.name.split('.').pop() === word && index.module.doc) {
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(`module ${index.module.name};`, 'blossom');
            docs.render(index.module.doc, markdown);
            return new vscode.Hover(markdown, range);
        }

        // an event, inside a string
        const event = api.eventInfo(word);
        if (event && /['"]$/.test(before)) return new vscode.Hover(eventMarkdown(event), range);

        // a function or a binding of the file
        const fn = index.functions.find((f) => f.name === word);
        if (fn) {
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(localSignature(fn), 'blossom');
            docs.render(fn.doc, markdown);
            return new vscode.Hover(markdown, range);
        }
        const binding = index.bindings.find((b) => b.name === word);
        if (binding) {
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(`let ${binding.mutable ? 'mut ' : ''}${binding.name}`, 'blossom');
            docs.render(binding.doc, markdown);
            return new vscode.Hover(markdown, range);
        }

        // a function of the prelude: it wins over the modules, because here it needs no import
        const preludeCard = preludeMarkdown(word);
        if (preludeCard) return new vscode.Hover(preludeCard, range);

        // a name the loop binds
        const loopVariable = LOOP_VARIABLES[word];
        if (loopVariable) {
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(word, 'blossom');
            markdown.appendMarkdown('\n' + loopVariable);
            return new vscode.Hover(markdown, range);
        }

        // a constant of the language
        const constant = CONSTANTS[word];
        if (constant) {
            const markdown = new vscode.MarkdownString();
            markdown.appendCodeblock(word, 'blossom');
            markdown.appendMarkdown('\n' + constant);
            return new vscode.Hover(markdown, range);
        }

        // a library symbol used without importing it: saying where it lives is half the work
        const home = api.homeModuleOf(word);
        if (home) {
            const markdown = symbolMarkdown(home, word);
            markdown.appendMarkdown(`\n\nNot imported here: \`import "${home}" as ${home.split('.').pop()};\``);
            return new vscode.Hover(markdown, range);
        }
        return null;
    }
};

// ------------------------------------------------------------------- call signature

/** Walks back from the cursor to the call that contains it, counting parentheses. */
function enclosingCall(text, offset) {
    let depth = 0;
    let commas = 0;
    for (let i = offset - 1; i >= 0; i--) {
        const c = text[i];
        if (c === ')') depth++;
        else if (c === '(') {
            if (depth === 0) {
                const start = text.slice(0, i);
                const head = /([A-Za-z_]\w*)(?:\s*\.\s*([A-Za-z_]\w*))?\s*$/.exec(start);
                if (!head) return null;
                // `x |> list.map(f)` is `list.map(x, f)`: what is being typed is one argument further
                const piped = /\|>\s*$/.test(start.slice(0, head.index));
                const argument = commas + (piped ? 1 : 0);
                return head[2]
                    ? { alias: head[1], name: head[2], argument, piped }
                    : { alias: null, name: head[1], argument, piped };
            }
            depth--;
        } else if (c === ',' && depth === 0) commas++;
        else if (c === ';' && depth === 0) return null;
    }
    return null;
}

const signatureHelp = {
    provideSignatureHelp(document, position) {
        const index = analyzer.indexOf(document);
        const offset = document.offsetAt(position);
        const call = enclosingCall(index.masked, offset);
        if (!call) return null;

        const signatures = [];
        /** One entry per signature of a library symbol, with the description of each parameter. */
        const libraryInfo = (info, footer) => {
            for (const signature of info.signatures) {
                const item = new vscode.SignatureInformation(signature);
                const inside = signature.slice(signature.indexOf('(') + 1, signature.lastIndexOf(')'));
                item.parameters = inside
                    .split(',')
                    .map((p) => p.trim())
                    .filter(Boolean)
                    .map((p) => {
                        // the parameter is documented under its own name, or inside a group
                        // ('x, y, z' on the same line), which is how the optional triples read
                        const described = (info.params || []).find(
                            ([name]) => name === p || name.split(/,\s*/).includes(p)
                        );
                        return described
                            ? new vscode.ParameterInformation(p, new vscode.MarkdownString(described[1]))
                            : new vscode.ParameterInformation(p);
                    });
                item.documentation = describeSymbol(info, footer, false);
                signatures.push(item);
            }
        };

        if (call.alias) {
            const target = analyzer.targetModule(index, call.alias);
            const info = target && api.symbolInfo(target, call.name);
            if (info) libraryInfo(info, `\`${target}\``);
        } else {
            const fn = index.functions.find((f) => f.name === call.name);
            if (fn) {
                const item = new vscode.SignatureInformation(localSignature(fn));
                item.parameters = fn.params.map((p) => {
                    const label = p.type ? `${p.name}: ${p.type}` : p.name;
                    const described = docs.paramDoc(fn.doc, p.name);
                    return described
                        ? new vscode.ParameterInformation(label, new vscode.MarkdownString(described))
                        : new vscode.ParameterInformation(label);
                });
                if (!docs.isEmpty(fn.doc)) {
                    item.documentation = docs.render(fn.doc, new vscode.MarkdownString());
                }
                signatures.push(item);
            } else {
                const info = api.preludeInfo(call.name);
                if (info) libraryInfo(info, '`prelude` — always available, no import needed');
            }
        }
        if (!signatures.length) return null;

        const help = new vscode.SignatureHelp();
        help.signatures = signatures;
        help.activeSignature = 0;
        help.activeParameter = Math.min(call.argument, Math.max(0, signatures[0].parameters.length - 1));
        return help;
    }
};

// -------------------------------------------------------- symbols and definition

const documentSymbols = {
    provideDocumentSymbols(document) {
        const index = analyzer.indexOf(document);
        const symbols = [];
        if (index.module) {
            symbols.push(new vscode.SymbolInformation(
                index.module.name,
                vscode.SymbolKind.Namespace,
                '',
                new vscode.Location(document.uri, new vscode.Position(index.module.start.line, 0))
            ));
        }
        for (const fn of index.functions) {
            symbols.push(new vscode.SymbolInformation(
                fn.name,
                vscode.SymbolKind.Function,
                fn.exported ? 'pub' : '',
                new vscode.Location(document.uri, new vscode.Position(fn.start.line, fn.start.character))
            ));
        }
        for (const binding of index.bindings) {
            symbols.push(new vscode.SymbolInformation(
                binding.name,
                binding.mutable ? vscode.SymbolKind.Variable : vscode.SymbolKind.Constant,
                '',
                new vscode.Location(document.uri, new vscode.Position(binding.start.line, binding.start.character))
            ));
        }
        return symbols;
    }
};

/** The file of a project module, looked up next to this one inside `src/`. */
async function moduleFile(document, target) {
    const parts = document.uri.fsPath.split(path.sep);
    const srcIndex = parts.lastIndexOf('src');
    if (srcIndex === -1) return null;
    const root = parts.slice(0, srcIndex + 1).join(path.sep);
    const candidate = vscode.Uri.file(path.join(root, ...target.split(/[./]/)) + '.bs');
    try {
        await vscode.workspace.fs.stat(candidate);
        return candidate;
    } catch {
        return null;
    }
}

const definition = {
    async provideDefinition(document, position) {
        const index = analyzer.indexOf(document);
        const range = document.getWordRangeAtPosition(position);
        if (!range) return null;
        const word = document.getText(range);

        // on an alias, or on the target of an import: open the file of the module
        const declaration = index.imports.get(word);
        if (declaration) {
            const file = await moduleFile(document, declaration.target);
            if (file) return new vscode.Location(file, new vscode.Position(0, 0));
        }

        // on a symbol of a project module: its definition, in the right file
        const before = document.lineAt(position.line).text.slice(0, range.start.character);
        const memberOf = /([A-Za-z_]\w*)\s*\.\s*$/.exec(before);
        if (memberOf) {
            const imported = index.imports.get(memberOf[1]);
            if (imported) {
                const file = await moduleFile(document, imported.target);
                if (file) {
                    const target = await vscode.workspace.openTextDocument(file);
                    const targetIndex = analyzer.indexOf(target);
                    const fn = targetIndex.functions.find((f) => f.name === word);
                    if (fn) {
                        return new vscode.Location(file, new vscode.Position(fn.start.line, fn.start.character));
                    }
                }
            }
            return null;
        }

        const fn = index.functions.find((f) => f.name === word);
        if (fn) return new vscode.Location(document.uri, new vscode.Position(fn.start.line, fn.start.character));
        const binding = index.bindings.find((b) => b.name === word);
        if (binding) {
            return new vscode.Location(document.uri, new vscode.Position(binding.start.line, binding.start.character));
        }
        return null;
    }
};

function register(context) {
    context.subscriptions.push(
        vscode.languages.registerCompletionItemProvider(SELECTOR, completion, '.', '"', "'"),
        vscode.languages.registerHoverProvider(SELECTOR, hover),
        vscode.languages.registerSignatureHelpProvider(SELECTOR, signatureHelp, '(', ','),
        vscode.languages.registerDocumentSymbolProvider(SELECTOR, documentSymbols),
        vscode.languages.registerDefinitionProvider(SELECTOR, definition)
    );
}

module.exports = { register, enclosingCall };
