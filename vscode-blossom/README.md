# Blossom for VS Code

Editor support for [Blossom](../BLOSSOM.md): highlighting, completion on the real API, and the
errors that can be told without running anything — imports without an alias, imports nothing uses,
modules and symbols that do not exist, wrong events, module names that do not match the path.

## What it does

| | |
| --- | --- |
| **Highlighting** | preamble, `fn`/`let`/`pub`, lambdas (`fn(x) -> …`), `match` and its arms, the pipe `\|>`, parameter types, `_`/`_i`/`_a`, strings — with the code inside `${…}` highlighted as code — comments |
| **Completion** | the modules inside `import "…"`, the symbols after `alias.`, the event names inside `event.on('…')` — `declare` and `emit` included, for application events — the functions and bindings of the file |
| **Hover** | the full card of every library function: signatures, what it does, every parameter, what it gives back, at least one example, and the module it comes from — and, for what the file declares, its own `///` doc |
| **Doc comments** | `///` above a `fn`, a `let` or the `module` line, with `@param`, `@returns`, `@throws`, `@example`, `@see`, `@deprecated`; a command and a lightbulb write the template |
| **Signature help** | the parameters of the function being written, library or your own, each with its own description; through a pipe the highlighted parameter is the one being typed, not the one the value fills |
| **Go to definition** | on the functions of the file, and across imports inside `src/` |
| **File outline** | module, functions (`pub` marked), bindings |
| **Diagnostics** | import without an alias, duplicate alias, unused import, `core.*` module that does not exist, symbol not exported, library function used without importing it, unknown event, module name different from the path |
| **Quick fixes** | add the missing import, remove the unused import, replace with the suggested name, rename the module |
| **`blossom.json`** | full schema: fields, semver, dependency forms, `watchdog` |
| **`blossomscript.json`** | full schema of the server configuration: registry, `io` roots, `http` hosts, watchdog |
| **API browser** | a page per module, every function on it, inside the editor and without the repo |

### The API browser

The **Blossom API** view, in the explorer, lists every module and its functions. Clicking one opens
the page of its module beside the editor: the import line to copy, then a card per function with
signatures, parameters, return value, examples and notes — the same prose that hover shows, laid out
to be read rather than glanced at. The filter box at the top narrows the list while typing.

The pages are built from the data bundled with the extension, so reading `core.io` does not need the
repo in the workspace. When the workspace *does* contain the repo, **Blossom: open the documentation
of the module** still prefers the markdown of `docs/api/`, which is the page an author is editing.

### Doc comments

A comment that starts with **three** slashes, glued to the declaration under it, is documentation:

```
/// The score of a tower.
///
/// @param height   how many blocks tall
/// @param material what it is made of
/// @returns the score, zero when the tower does not count
/// @throws value_exception when the material is not in the catalogue
/// @example score(12, 'stone');
pub fn score(height: num, material: str) -> ...;
```

The language sees ordinary comments — nothing to learn, nothing to break. The editor reads them:
hovering the name shows the prose, completion shows the first line, and signature help shows the
description of the parameter being typed. **Blossom: write the doc comment of the function** (or the
lightbulb on the declaration) writes the skeleton with one `@param` per parameter, already named,
each one a tabstop.

A blank line or a plain `//` comment between the block and the declaration detaches it: a doc that
is not glued to its declaration documents nothing, and that is on purpose — a floating note is a
note, not a doc.

Four commands, from the palette:

- **Blossom: browse the API** — asks which module and opens the browser on it
- **Blossom: open the documentation of the module** — the module under the cursor, scrolled to the
  function under it
- **Blossom: copy the load command of the app** — puts `/blossom script load <app>` in the
  clipboard, deducing the name from the file tree
- **Blossom: write the doc comment of the function** — the `///` template of the declaration under
  the cursor, or of the first one below it

## The API data is not hand-written

`data/api.json` is generated from the sources of the mod: modules and symbols from `StdLib.java` and
`BlossomApi.java`, events (name, arity, parameters, cancellability) from `Event.java`, signatures and
short descriptions from `docs/api/`. When the API changes:

```
node tools/generate-api.js          # from the extension folder, with the repo above it
node tools/generate-api.js /path/to/the/repo
```

A symbol added to the mod shows up in completion without anyone copying it over.

### Where the prose comes from

The list of names is generated; the prose is not. What the hover shows for each function — the
summary, the parameters one by one, the return value, one or more examples and the note about what
surprises people — is written by hand in `data/handbook/<module>.json`, plus `prelude.json` for the
functions that need no import:

```json
"sqrt": {
  "signatures": ["sqrt(x)"],
  "summary": "The square root.",
  "params": [["x", "a number that is not negative"]],
  "returns": "`num` — the root.",
  "examples": ["math.sqrt(16);   // 4"],
  "note": "optional, for what is worth knowing before writing the call"
}
```

The two halves check each other. A key the handbook documents and no module exports any more stops
the generator, so a renamed function cannot leave its old documentation behind; a symbol without an
entry is listed at the end of the run. The test suite refuses a symbol with no example, no return
value, or an example that never calls the function it documents — which is how 303 entries stay
honest instead of drifting one rename at a time.

## Performance

No language server, no separate process, **zero dependencies** and no build step: it is JavaScript
running in the extension process.

- activation registers the providers and nothing else; `data/api.json` (~145 KB) is read on the first
  request
- the index of a file is one pass over the characters plus a few regexes on a copy where comments and
  strings are spaces, and it is cached per document version
- diagnostics are debounced at 300 ms: analysis happens when you stop typing
- on the projects in `examples/` the full analysis of a file stays under a millisecond

The diagnostics report only what the extension is sure about: no type checking, no arity checking on
the functions of the user. What a lambda and a `match` arm bind counts as declared, so a parameter
named like a library function is not reported; the name of an application event, which only
`declare` and `emit` take, is never judged against the catalogue. On every file in `examples/` it produces no warning.

## Installation

During development: open this folder in VS Code and press <kbd>F5</kbd> (Extension Development
Host).

To really install it:

```
cd vscode-blossom
npx --yes @vscode/vsce package
code --install-extension blossom-0.7.0.vsix
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
