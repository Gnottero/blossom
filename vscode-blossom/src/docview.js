'use strict';

/*
 * The API documentation, read inside the editor.
 *
 * The pages come from `data/api.json`, the same file that feeds completion and hover, so what the
 * viewer shows is what the mod really exports — there is no second copy of the documentation to
 * keep in step, and no need to have the repo open in the workspace to read it.
 *
 * The whole catalogue is handed to the webview once and the page switches module in the browser:
 * it is a hundred and fifty kilobytes of json, and paying one message per click to re-render it in
 * the extension would be slower and no smaller.
 */

const vscode = require('vscode');
const api = require('./api');

const VIEW_TYPE = 'blossom.apiDocs';

/** The open panel, or null: reading a second module reuses the first window. */
let panel = null;

// ------------------------------------------------------------------ the page

function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/**
 * The little markdown the handbook uses - `code`, **bold**, _italic_ - turned into html.
 *
 * <p>A full markdown renderer would be a dependency for three constructs, and the text is written
 * by hand in this repo: what is not handled here simply is not used.
 */
function inlineMarkdown(text) {
    return escapeHtml(text)
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}

/** @param alias the import alias to write in front of the call, empty for the prelude */
function symbolCard(alias, name, info) {
    const signatures = (info.signatures || [name + '(...)'])
        .map((signature) => (alias ? `${alias}.${signature}` : signature))
        .join('\n');
    const parts = [
        `<section class="symbol" data-name="${escapeHtml(name)}">`,
        `<h3><code>${escapeHtml(name)}</code></h3>`,
        `<pre class="signature">${escapeHtml(signatures)}</pre>`
    ];
    if (info.doc) parts.push(`<p>${inlineMarkdown(info.doc)}</p>`);
    if (info.params && info.params.length) {
        parts.push('<table class="params">');
        for (const [parameter, description] of info.params) {
            parts.push(`<tr><td><code>${escapeHtml(parameter)}</code></td><td>${inlineMarkdown(description)}</td></tr>`);
        }
        parts.push('</table>');
    }
    if (info.returns) parts.push(`<p class="returns"><strong>Returns</strong> ${inlineMarkdown(info.returns)}</p>`);
    if (info.examples && info.examples.length) {
        parts.push(`<pre class="example">${escapeHtml(info.examples.join('\n\n'))}</pre>`);
    }
    if (info.note) parts.push(`<p class="note">${inlineMarkdown(info.note)}</p>`);
    parts.push('</section>');
    return parts.join('\n');
}

/** The page of one module: the import line to copy, then a card per exported symbol. */
function modulePage(moduleName, module) {
    const alias = module.alias || moduleName.split('.').pop();
    const symbols = Object.keys(module.symbols).sort();
    const cards = symbols.map((name) => symbolCard(alias, name, module.symbols[name]));
    return [
        `<article class="module" id="module-${escapeHtml(moduleName)}">`,
        `<h2><code>${escapeHtml(moduleName)}</code></h2>`,
        module.doc ? `<p class="summary">${inlineMarkdown(module.doc)}</p>` : '',
        `<pre class="import">import "${escapeHtml(moduleName)}" as ${escapeHtml(alias)};</pre>`,
        `<p class="count">${symbols.length} function${symbols.length === 1 ? '' : 's'}</p>`,
        cards.join('\n'),
        '</article>'
    ].join('\n');
}

/** The prelude has no import line: it is what is available without one. */
function preludePage(data) {
    const symbols = Object.keys(data.preludeSymbols || {}).sort();
    const cards = symbols.map((name) => symbolCard('', name, data.preludeSymbols[name]));
    return [
        '<article class="module" id="module-prelude">',
        '<h2>prelude</h2>',
        '<p class="summary">What is available without importing anything: the constructs that are grammar dressed up as functions, and <code>print</code>.</p>',
        `<p class="count">${symbols.length} functions</p>`,
        cards.join('\n'),
        '</article>'
    ].join('\n');
}

/**
 * The prose of an event, from markdown to the little html this view needs.
 *
 * Only inline code, bold and paragraphs: the sections in `core.mc.event.md` use nothing else, and a
 * markdown library pulled in for three constructs would be a dependency to keep alive for years.
 */
function eventProse(text) {
    return escapeHtml(text)
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
        .split(/\n\s*\n/)
        .map((paragraph) => `<p>${paragraph.replace(/\n/g, ' ')}</p>`)
        .join('');
}

function eventsPage(data) {
    const rows = data.events.map((event) => [
        `<tr id="event-${escapeHtml(event.name)}" data-name="${escapeHtml(event.name)}">`,
        `<td><code>${escapeHtml(event.name)}</code></td>`,
        `<td><code>${escapeHtml((event.params || []).join(', '))}</code></td>`,
        `<td>${event.cancellable ? 'cancellable' : ''}</td>`,
        '</tr>',
        // the prose under the row, so the table stays readable and the answer is still here
        event.doc
            ? `<tr class="event-doc" data-name="${escapeHtml(event.name)}"><td colspan="3">` +
              `${eventProse(event.doc)}</td></tr>`
            : ''
    ].join(''));
    return [
        '<article class="module" id="module-events">',
        '<h2>events</h2>',
        '<p class="summary">What a script can react to, with the arguments the handler receives. A cancellable event stops the action when the handler gives back <code>true</code>.</p>',
        `<p class="count">${data.events.length} events</p>`,
        '<table class="events"><tr><th>Event</th><th>Arguments</th><th></th></tr>',
        rows.join('\n'),
        '</table>',
        '</article>'
    ].join('\n');
}

/** Every page in one document: the nav switches between them without asking the extension. */
function renderPage(data, initial, nonce) {
    const moduleNames = Object.keys(data.modules);
    const navEntries = ['prelude', ...moduleNames, 'events'];
    const nav = navEntries.map((name) =>
        `<li><a href="#" data-module="${escapeHtml(name)}">${escapeHtml(name)}</a></li>`).join('\n');
    const pages = [
        preludePage(data),
        ...moduleNames.map((name) => modulePage(name, data.modules[name])),
        eventsPage(data)
    ].join('\n');
    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';">
<style nonce="${nonce}">
body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); margin: 0; }
.layout { display: flex; align-items: stretch; min-height: 100vh; }
nav { flex: 0 0 14rem; padding: 1rem 0.5rem; border-right: 1px solid var(--vscode-panel-border); position: sticky; top: 0; align-self: flex-start; max-height: 100vh; overflow-y: auto; }
nav ul { list-style: none; margin: 0; padding: 0; }
nav a { display: block; padding: 0.2rem 0.5rem; color: var(--vscode-foreground); text-decoration: none; border-radius: 3px; font-size: 0.9em; }
nav a:hover { background: var(--vscode-list-hoverBackground); }
nav a.current { background: var(--vscode-list-activeSelectionBackground); color: var(--vscode-list-activeSelectionForeground); }
main { flex: 1 1 auto; padding: 1rem 1.5rem 4rem; max-width: 60rem; }
h2 { border-bottom: 1px solid var(--vscode-panel-border); padding-bottom: 0.3rem; }
h3 { margin-bottom: 0.2rem; }
pre { background: var(--vscode-textCodeBlock-background); padding: 0.5rem 0.7rem; border-radius: 4px; overflow-x: auto; }
pre.signature { font-weight: 600; }
code { font-family: var(--vscode-editor-font-family); }
.summary, .count { opacity: 0.85; }
.count { font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.04em; }
.note { border-left: 3px solid var(--vscode-textLink-foreground); padding-left: 0.7rem; opacity: 0.9; }
table { border-collapse: collapse; margin: 0.3rem 0; }
td, th { padding: 0.15rem 0.8rem 0.15rem 0; text-align: left; vertical-align: top; }
.symbol { padding: 0.4rem 0 1rem; border-bottom: 1px solid var(--vscode-panel-border); }
table.events tr[data-name]:not(.event-doc) td { padding-top: 0.9rem; font-weight: 600; }
.event-doc td { padding: 0 0 0.4rem; opacity: 0.9; border-bottom: 1px solid var(--vscode-panel-border); }
.event-doc p { margin: 0.3rem 0; max-width: 55rem; }
.symbol.target { background: var(--vscode-editor-findMatchHighlightBackground); }
#search { width: 100%; box-sizing: border-box; margin-bottom: 0.7rem; padding: 0.3rem 0.4rem;
  background: var(--vscode-input-background); color: var(--vscode-input-foreground);
  border: 1px solid var(--vscode-input-border, transparent); border-radius: 3px; }
article { display: none; }
article.current { display: block; }
.hidden { display: none; }
</style>
</head>
<body>
<div class="layout">
<nav><input id="search" type="search" placeholder="filter functions"><ul>${nav}</ul></nav>
<main>${pages}</main>
</div>
<script nonce="${nonce}">
const vscode = acquireVsCodeApi();
const links = Array.from(document.querySelectorAll('nav a'));
const articles = Array.from(document.querySelectorAll('article'));

function show(name, symbol) {
    for (const article of articles) article.classList.toggle('current', article.id === 'module-' + name);
    for (const link of links) link.classList.toggle('current', link.dataset.module === name);
    vscode.setState({ module: name });
    const previous = document.querySelector('.target');
    if (previous) previous.classList.remove('target');
    const target = symbol && document.querySelector('#module-' + CSS.escape(name) + ' [data-name="' + symbol + '"]');
    if (target) {
        target.classList.add('target');
        target.scrollIntoView({ block: 'start' });
    } else {
        window.scrollTo(0, 0);
    }
}

for (const link of links) {
    link.addEventListener('click', (event) => {
        event.preventDefault();
        show(link.dataset.module, null);
    });
}

document.getElementById('search').addEventListener('input', (event) => {
    const needle = event.target.value.trim().toLowerCase();
    for (const entry of document.querySelectorAll('[data-name]')) {
        entry.classList.toggle('hidden', needle !== '' && !entry.dataset.name.toLowerCase().includes(needle));
    }
});

window.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'reveal') show(event.data.module, event.data.symbol);
});

show(${JSON.stringify(initial)}, null);
</script>
</body>
</html>`;
}

// ------------------------------------------------------------- the tree view

/** The catalogue in the side bar: a module, its functions, and the documentation on click. */
class ApiTree {
    getTreeItem(element) {
        if (element.symbol) {
            const item = new vscode.TreeItem(element.symbol, vscode.TreeItemCollapsibleState.None);
            const info = api.symbolInfo(element.module, element.symbol);
            item.description = info && info.signatures ? info.signatures[0] : '';
            item.tooltip = info ? info.doc : '';
            item.iconPath = new vscode.ThemeIcon('symbol-function');
            item.command = {
                command: 'blossom.openApiDocs',
                title: 'Open the documentation',
                arguments: [element.module, element.symbol]
            };
            return item;
        }
        const item = new vscode.TreeItem(element.module, vscode.TreeItemCollapsibleState.Collapsed);
        const module = api.moduleOf(element.module);
        item.description = module ? module.doc : '';
        item.iconPath = new vscode.ThemeIcon('symbol-namespace');
        return item;
    }

    getChildren(element) {
        if (!element) return api.moduleNames().map((module) => ({ module }));
        if (element.symbol) return [];
        return api.symbolsOf(element.module).sort().map((symbol) => ({ module: element.module, symbol }));
    }
}

// ------------------------------------------------------------------ commands

function nonceOf() {
    let text = '';
    for (let i = 0; i < 32; i++) text += Math.floor(Math.random() * 16).toString(16);
    return text;
}

/**
 * Opens the viewer on `moduleName`, creating the panel the first time.
 *
 * @param moduleName the module to show; asked for with a quick pick when it is not given
 * @param symbol the function to scroll to, when the request came from the tree or from the code
 */
async function open(moduleName, symbol) {
    const data = api.load();
    if (!moduleName) {
        moduleName = await vscode.window.showQuickPick(
            Object.keys(data.modules).map((name) => ({
                label: name,
                description: data.modules[name].doc,
                detail: `${Object.keys(data.modules[name].symbols).length} functions`
            })),
            { title: 'Documentation of which module?', matchOnDescription: true }
        ).then((picked) => (picked ? picked.label : null));
        if (!moduleName) return;
    }
    if (panel) {
        panel.reveal(vscode.ViewColumn.Beside, true);
    } else {
        panel = vscode.window.createWebviewPanel(VIEW_TYPE, 'Blossom API', {
            viewColumn: vscode.ViewColumn.Beside,
            preserveFocus: true
        }, { enableScripts: true, retainContextWhenHidden: true });
        panel.onDidDispose(() => { panel = null; });
        panel.webview.html = renderPage(data, moduleName, nonceOf());
    }
    panel.webview.postMessage({ type: 'reveal', module: moduleName, symbol: symbol || null });
}

function register(context) {
    context.subscriptions.push(
        vscode.commands.registerCommand('blossom.openApiDocs', (moduleName, symbol) => open(moduleName, symbol)),
        vscode.window.registerTreeDataProvider('blossomApi', new ApiTree())
    );
}

module.exports = { register, open, renderPage, modulePage, ApiTree };
