'use strict';

/*
 * The minimum of the vscode API needed to run the tests outside the editor: the extension has no
 * dependencies, and neither do the tests. What lives here is only the shape of the types the code
 * builds.
 */

class Position {
    constructor(line, character) {
        this.line = line;
        this.character = character;
    }
}

class Range {
    constructor(a, b, c, d) {
        if (a instanceof Position) {
            this.start = a;
            this.end = b;
        } else {
            this.start = new Position(a, b);
            this.end = new Position(c, d);
        }
    }
}

class Diagnostic {
    constructor(range, message, severity) {
        this.range = range;
        this.message = message;
        this.severity = severity;
    }
}

class MarkdownString {
    constructor(value = '') {
        this.value = value;
    }
    appendMarkdown(text) {
        this.value += text;
        return this;
    }
    appendCodeblock(code, language) {
        this.value += '\n```' + (language || '') + '\n' + code + '\n```\n';
        return this;
    }
}

class CompletionItem {
    constructor(label, kind) {
        this.label = label;
        this.kind = kind;
    }
}

class SnippetString {
    constructor(value) {
        this.value = value;
    }
}

class SignatureInformation {
    constructor(label) {
        this.label = label;
        this.parameters = [];
    }
}

class ParameterInformation {
    constructor(label, documentation) {
        this.label = label;
        this.documentation = documentation;
    }
}

class SignatureHelp {
    constructor() {
        this.signatures = [];
        this.activeSignature = 0;
        this.activeParameter = 0;
    }
}

class Hover {
    constructor(contents, range) {
        this.contents = contents;
        this.range = range;
    }
}

class Location {
    constructor(uri, position) {
        this.uri = uri;
        this.range = position;
    }
}

class SymbolInformation {
    constructor(name, kind, containerName, location) {
        this.name = name;
        this.kind = kind;
        this.containerName = containerName;
        this.location = location;
    }
}

class CodeAction {
    constructor(title, kind) {
        this.title = title;
        this.kind = kind;
    }
}

class WorkspaceEdit {
    constructor() {
        this.edits = [];
    }
    insert(uri, position, text) {
        this.edits.push({ kind: 'insert', uri, position, text });
    }
    replace(uri, range, text) {
        this.edits.push({ kind: 'replace', uri, range, text });
    }
}

class TreeItem {
    constructor(label, collapsibleState) {
        this.label = label;
        this.collapsibleState = collapsibleState;
    }
}

class ThemeIcon {
    constructor(id) {
        this.id = id;
    }
}

const kinds = new Proxy({}, { get: (_, name) => name });

module.exports = {
    Position,
    Range,
    Diagnostic,
    DiagnosticSeverity: { Error: 0, Warning: 1, Information: 2, Hint: 3 },
    DiagnosticTag: { Unnecessary: 1, Deprecated: 2 },
    MarkdownString,
    CompletionItem,
    CompletionItemKind: kinds,
    SnippetString,
    SignatureInformation,
    ParameterInformation,
    SignatureHelp,
    Hover,
    Location,
    SymbolInformation,
    SymbolKind: kinds,
    CodeAction,
    CodeActionKind: kinds,
    WorkspaceEdit,
    TreeItem,
    TreeItemCollapsibleState: { None: 0, Collapsed: 1, Expanded: 2 },
    ThemeIcon,
    ViewColumn: { Beside: -2, Active: -1, One: 1 },
    Uri: { file: (p) => ({ fsPath: p, toString: () => 'file://' + p }) },
    languages: {
        createDiagnosticCollection: () => ({ set() {}, delete() {} }),
        registerCompletionItemProvider: () => ({ dispose() {} }),
        registerHoverProvider: () => ({ dispose() {} }),
        registerSignatureHelpProvider: () => ({ dispose() {} }),
        registerDocumentSymbolProvider: () => ({ dispose() {} }),
        registerDefinitionProvider: () => ({ dispose() {} }),
        registerCodeActionsProvider: () => ({ dispose() {} })
    },
    workspace: {
        getConfiguration: () => ({ get: (_, fallback) => fallback }),
        onDidOpenTextDocument: () => ({ dispose() {} }),
        onDidChangeTextDocument: () => ({ dispose() {} }),
        onDidSaveTextDocument: () => ({ dispose() {} }),
        onDidCloseTextDocument: () => ({ dispose() {} }),
        textDocuments: []
    },
    commands: { registerCommand: () => ({ dispose() {} }) },
    window: {
        registerTreeDataProvider: () => ({ dispose() {} }),
        createWebviewPanel: () => ({
            webview: { html: '', postMessage() {} },
            reveal() {},
            onDidDispose() {},
            dispose() {}
        })
    },
    env: {}
};
