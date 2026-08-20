'use strict';

/*
 * Entry point. Activation has to cost next to nothing: it registers the providers and that is all -
 * the API data is read on the first request, and the index of a file on its first use.
 */

const vscode = require('vscode');
const path = require('path');
const api = require('./api');
const analyzer = require('./analyzer');
const providers = require('./providers');
const diagnostics = require('./diagnostics');
const codeactions = require('./codeactions');
const docview = require('./docview');
const docs = require('./docs');

function activate(context) {
    providers.register(context);
    diagnostics.register(context);
    codeactions.register(context);
    docview.register(context);

    context.subscriptions.push(
        vscode.commands.registerCommand('blossom.openDocs', () => openDocs()),
        vscode.commands.registerCommand('blossom.copyRunCommand', () => copyRunCommand()),
        vscode.commands.registerCommand('blossom.generateDoc', () => generateDoc())
    );
}

/**
 * Opens the documentation of the module under the cursor, scrolled to the function under it.
 *
 * <p>The page comes from the data bundled with the extension, so it works without the repo; the
 * markdown of `docs/api/` is preferred when the workspace does have it, because that is the page the
 * author is editing.
 */
async function openDocs() {
    const editor = vscode.window.activeTextEditor;
    let moduleName = null;
    let symbol = null;
    if (editor && editor.document.languageId === 'blossom') {
        const index = analyzer.indexOf(editor.document);
        const position = editor.selection.active;
        const range = editor.document.getWordRangeAtPosition(position);
        const word = range ? editor.document.getText(range) : '';
        moduleName = analyzer.targetModule(index, word) ||
            (api.homeModuleOf(word) || null);
        if (moduleName && api.symbolInfo(moduleName, word)) symbol = word;
        if (!moduleName) {
            const before = editor.document.lineAt(position.line).text.slice(0, range ? range.start.character : 0);
            const memberOf = /([A-Za-z_]\w*)\s*\.\s*$/.exec(before);
            if (memberOf) moduleName = analyzer.targetModule(index, memberOf[1]);
        }
    }
    if (moduleName && !api.moduleOf(moduleName)) moduleName = null;

    if (moduleName) {
        const found = await vscode.workspace.findFiles(`**/docs/api/${moduleName}.md`, null, 1);
        if (found.length) {
            await vscode.commands.executeCommand('markdown.showPreview', found[0]);
            return;
        }
    }
    await docview.open(moduleName, symbol);
}

/**
 * Writes the doc comment of the declaration the cursor is on - or of the first one below it, which
 * is where the cursor ends up after typing a function. The template goes in as a snippet, so the
 * summary and every `@param` are tabstops.
 */
async function generateDoc() {
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.document.languageId !== 'blossom') return;
    const index = analyzer.indexOf(editor.document);
    const line = editor.selection.active.line;

    const declarations = [...index.functions, ...index.bindings]
        .sort((a, b) => a.start.line - b.start.line);
    const target = declarations.find((item) => item.start.line === line) ||
        declarations.find((item) => item.docRange &&
            line >= item.docRange.startLine && line <= item.docRange.endLine) ||
        declarations.find((item) => item.start.line > line);
    if (!target) {
        vscode.window.setStatusBarMessage('Blossom: no declaration to document here', 4000);
        return;
    }
    if (target.doc && !docs.isEmpty(target.doc)) {
        vscode.window.setStatusBarMessage(`Blossom: '${target.name}' is already documented`, 4000);
        return;
    }
    const text = editor.document.lineAt(target.start.line).text;
    const indent = /^[ \t]*/.exec(text)[0];
    await editor.insertSnippet(
        new vscode.SnippetString(docs.template(target, indent, true)),
        new vscode.Position(target.start.line, 0)
    );
}

/** Copies to the clipboard the command that loads the open app, the one you type every time. */
async function copyRunCommand() {
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.document.languageId !== 'blossom') return;
    const index = analyzer.indexOf(editor.document);
    const fsPath = editor.document.uri.fsPath;
    const parts = fsPath.split(path.sep);
    const srcIndex = parts.lastIndexOf('src');
    // a pack loads under the name of its folder, a single file under its own name
    const app = srcIndex > 0
        ? parts[srcIndex - 1]
        : path.basename(fsPath, '.bs');
    const command = `/blossom script load ${app}`;
    await vscode.env.clipboard.writeText(command);
    vscode.window.setStatusBarMessage(
        `${command}${index.module ? `   (module ${index.module.name})` : ''}`, 4000
    );
}

function deactivate() {}

module.exports = { activate, deactivate };
