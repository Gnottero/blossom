'use strict';

/*
 * The quick fixes. There are few on purpose: only the ones where the fix is obvious and mechanical,
 * that is, the ones the extension can apply without guessing what the author meant.
 */

const vscode = require('vscode');
const api = require('./api');
const analyzer = require('./analyzer');

/** The line where a new import goes: after the last one, or after the module declaration. */
function importInsertionLine(document, index) {
    if (index.imports.size) {
        return Math.max(...[...index.imports.values()].map((d) => d.aliasStart.line)) + 1;
    }
    if (index.module) return index.module.start.line + 1;
    return 0;
}

/** A free alias for `moduleName`: `core.mc.text` -> `text`, and `mc_text` when that one is taken. */
function freeAlias(moduleName, taken) {
    const parts = moduleName.split('.');
    const candidates = [parts[parts.length - 1], parts.slice(1).join('_'), parts.join('_')];
    for (const candidate of candidates) {
        if (!taken.has(candidate)) return candidate;
    }
    return parts.join('_') + '_module';
}

const provider = {
    provideCodeActions(document, range, context) {
        const index = analyzer.indexOf(document);
        const actions = [];

        for (const diagnostic of context.diagnostics) {
            if (diagnostic.source !== 'blossom') continue;

            if (diagnostic.code === 'missing-import') {
                const name = document.getText(diagnostic.range);
                const moduleName = api.homeModuleOf(name);
                if (!moduleName) continue;
                const alias = freeAlias(moduleName, new Set(index.imports.keys()));
                const line = importInsertionLine(document, index);
                const action = new vscode.CodeAction(
                    `import "${moduleName}" as ${alias};`,
                    vscode.CodeActionKind.QuickFix
                );
                action.edit = new vscode.WorkspaceEdit();
                const blank = index.imports.size ? '' : '\n';
                action.edit.insert(
                    document.uri,
                    new vscode.Position(line, 0),
                    `${index.module && !index.imports.size ? '\n' : ''}import "${moduleName}" as ${alias};\n${blank}`
                );
                action.edit.replace(document.uri, diagnostic.range, `${alias}.${name}`);
                action.diagnostics = [diagnostic];
                action.isPreferred = true;
                actions.push(action);
            }

            if (diagnostic.code === 'unused-import') {
                // the whole line goes, newline included: what is left is the preamble without a hole
                const action = new vscode.CodeAction('Remove the unused import', vscode.CodeActionKind.QuickFix);
                action.edit = new vscode.WorkspaceEdit();
                action.edit.replace(
                    document.uri,
                    new vscode.Range(diagnostic.range.start.line, 0, diagnostic.range.end.line + 1, 0),
                    ''
                );
                action.diagnostics = [diagnostic];
                action.isPreferred = true;
                actions.push(action);
            }

            if (diagnostic.code === 'unknown-symbol' || diagnostic.code === 'unknown-event' ||
                diagnostic.code === 'unknown-module' || diagnostic.code === 'unknown-alias') {
                const suggestion = /Did you mean '([^']+)'\?/.exec(diagnostic.message);
                if (!suggestion) continue;
                const action = new vscode.CodeAction(
                    `Replace with '${suggestion[1]}'`,
                    vscode.CodeActionKind.QuickFix
                );
                action.edit = new vscode.WorkspaceEdit();
                action.edit.replace(document.uri, diagnostic.range, suggestion[1]);
                action.diagnostics = [diagnostic];
                action.isPreferred = true;
                actions.push(action);
            }

            if (diagnostic.code === 'module-name-mismatch') {
                const expected = /makes it '([^']+)'/.exec(diagnostic.message);
                if (!expected) continue;
                const action = new vscode.CodeAction(
                    `Rename the module to '${expected[1]}'`,
                    vscode.CodeActionKind.QuickFix
                );
                action.edit = new vscode.WorkspaceEdit();
                action.edit.replace(document.uri, diagnostic.range, expected[1]);
                action.diagnostics = [diagnostic];
                actions.push(action);
            }
        }
        return actions;
    }
};

function register(context) {
    context.subscriptions.push(
        vscode.languages.registerCodeActionsProvider({ language: 'blossom' }, provider, {
            providedCodeActionKinds: [vscode.CodeActionKind.QuickFix]
        })
    );
}

module.exports = { register };
