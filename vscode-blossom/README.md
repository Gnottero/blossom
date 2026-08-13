# Blossom for VS Code

Editor support for [Blossom](../BLOSSOM.md): highlighting, completion on the real API, and the
errors that can be told without running anything — imports without an alias, imports nothing uses,
modules and symbols that do not exist, wrong events, module names that do not match the path.

## What it does

| | |
| --- | --- |
| **Highlighting** | preamble, `fn`/`let`/`pub`, parameter types, `_`/`_i`/`_a`, strings, comments |
| **Completion** | the modules inside `import "…"`, the symbols after `alias.`, the event names inside `event.on('…')`, the functions and bindings of the file |
| **Hover** | signature and description of every library function, with the module it comes from |
| **Signature help** | the parameters of the function being written, library or your own |
| **Go to definition** | on the functions of the file, and across imports inside `src/` |
| **File outline** | module, functions (`pub` marked), bindings |
| **Diagnostics** | import without an alias, duplicate alias, unused import, `core.*` module that does not exist, symbol not exported, library function used without importing it, unknown event, module name different from the path |
| **Quick fixes** | add the missing import, remove the unused import, replace with the suggested name, rename the module |
| **`blossom.json`** | full schema: fields, semver, dependency forms, `watchdog` |

Two commands, from the palette:

- **Blossom: open the documentation of the module** — opens `docs/api/<module>.md`, when the repo is
  in the workspace
- **Blossom: copy the load command of the app** — puts `/blossom script load <app>` in the
  clipboard, deducing the name from the file tree

## The API data is not hand-written

`data/api.json` is generated from the sources of the mod: modules and symbols from `StdLib.java` and
`BlossomApi.java`, events (name, arity, parameters, cancellability) from `Event.java`, signatures and
short descriptions from `docs/api/`. When the API changes:

```
node tools/generate-api.js          # from the extension folder, with the repo above it
node tools/generate-api.js /path/to/the/repo
```

A symbol added to the mod shows up in completion without anyone copying it over.

## Performance

No language server, no separate process, **zero dependencies** and no build step: it is JavaScript
running in the extension process.

- activation registers the providers and nothing else; `data/api.json` (~78 KB) is read on the first
  request
- the index of a file is one pass over the characters plus a few regexes on a copy where comments and
  strings are spaces, and it is cached per document version
- diagnostics are debounced at 300 ms: analysis happens when you stop typing
- on the projects in `examples/` the full analysis of a file stays under a millisecond

The diagnostics report only what the extension is sure about: no type checking, no arity checking on
the functions of the user. On every file in `examples/` it produces no warning.

## Installation

During development: open this folder in VS Code and press <kbd>F5</kbd> (Extension Development
Host).

To really install it:

```
cd vscode-blossom
npx --yes @vscode/vsce package
code --install-extension blossom-0.1.0.vsix
```

## Tests

```
node test/run.js
```

They run without VS Code and without `npm install`: `require('vscode')` is served by a stub in
`test/vscode-stub.js`.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| `blossom.diagnostics.enabled` | `true` | turns the squiggles off, leaving completion and hover |

## Licence

MIT, like the rest of the project.
