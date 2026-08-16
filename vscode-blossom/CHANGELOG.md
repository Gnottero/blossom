# Changelog

## 0.3.0

- unused imports go away in one move: when a file has more than one, the quick fix offers
  **Remove all N unused imports**, and the same removal is a `source.fixAll` action, so
  `editor.codeActionsOnSave` can clean the preamble when you save
- **the API is readable inside the editor**: a new **Blossom API** view in the explorer lists every
  module and every function it exports, and opening one shows the page of its module beside the
  code — import line, signatures, parameters, return value, examples and notes, one card per
  function, with a filter box over the lot
- the pages are built from `data/api.json`, the same file that feeds completion and hover, so they
  work without the repo in the workspace and cannot drift from what the mod exports
- **Blossom: open the documentation of the module** now scrolls to the function under the cursor,
  and falls back to the built-in pages instead of saying that the repo is not open
- `config/blossomscript.json` has a schema too: registry, `io` roots, `http` hosts and limits, and
  `watchdog`, each key with its default and what it does
- `core.http` is in the catalogue, with its nine functions documented
- the generator understands a module whose public names differ from the implementations
  (`http.get` is registered as `http_get`), and `reset_chunk` and `relight` are gone from the data
  because the mod no longer pretends to have them

## 0.2.0

- **every function is documented**: hovering a library symbol now shows its signatures, what it does,
  every parameter one by one, what it gives back, at least one example of real code, and a note when
  something about the call is worth knowing before writing it. 302 symbols across the 23 `core.*`
  modules, plus the 15 functions of the prelude and the constants of the language, which had no
  hover at all before
- signature help carries the same card, and describes the parameter being written
- hovering an event shows the handler to write, with the right signature and the registration line
- the prose lives in `data/handbook/<module>.json`, merged into `data/api.json` by
  `tools/generate-api.js`: the list of symbols still comes only from the sources of the mod, and a
  handbook entry for a symbol nobody exports any more stops the generator
- the test suite refuses a symbol without an example, without a return value, or with an example that
  never calls the function it documents

## 0.1.2

- new diagnostic: an import nothing in the file uses is greyed out (`unused-import`), with a quick
  fix that removes the whole line
- every text of the extension is in English: commands, settings, snippets, `blossom.json` schema,
  quick fixes and messages

## 0.1.1

- the `blossom.json` schema accepts `autoload`, which the mod has always read: before, a manifest
  declaring it was marked with "Property autoload is not allowed."
- `version` is no longer mandatory in the manifest, as in the mod (absent means `0.1.0`)
- the documentation paths follow the new repo: `docs/api/` instead of `docs/blossom/api/`

## 0.1.0

First version.

- syntax highlighting (preamble, `fn`/`let`/`pub`, parameter types, implicit variables, strings,
  comments)
- completion of modules, library symbols, events, functions and bindings of the file
- hover with signature and description, taken from the documentation of the repo
- signature help, file outline, go to definition (across imports too)
- diagnostics of the preamble and of symbol usage, with quick fixes
- JSON schema for `blossom.json`
- `tools/generate-api.js`: the API data is regenerated from the sources of the mod
