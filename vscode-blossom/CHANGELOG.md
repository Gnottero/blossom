# Changelog

## 0.7.0

Blossom 1.0: the syntax of the language and the renamed API.

- **the new syntax is highlighted**: lambdas (`fn(x) -> x * 2`), `match` with its arms and `else`,
  the pipe `|>`, the definition arrow, and interpolated strings — where what sits inside `${…}` is
  highlighted as the code it is
- **the analysis reads `${…}` as code too**: a symbol used only inside a placeholder counts as used,
  and a module that does not export it is reported there like anywhere else. A single quote goes on
  interpolating nothing
- **what a lambda or a `match` arm binds is a declared name**: `fn(size) -> size(1)` is no longer
  reported as a library function used without importing it
- **signature help through a pipe**: in `list |> list.map(…)` the parameter highlighted is the one
  being typed, since the value on the left already fills the first
- **events**: `signal_event` is gone; the catalogue is checked on `on`, `off`, `describe` and
  `listeners`, in double quotes as well as single ones, and left alone on `declare` and `emit`,
  which are the two that take the name of an application event
- **snippets**: a lambda, a `match`, a pipe chain and an interpolated string; `task` now writes
  `task.start(fn() -> …)`, and the manifest writes `"api_level": "2.0"`
- **the handbook follows the renamed API**: the examples of `core.mc.inventory`, `core.mc.structure`,
  `core.mc.bossbar`, `core.string`, `core.list` and `core.task` call their functions through the
  alias of the module that actually exports them
- the generator strips java comments before reading the lists of names: a name quoted in a comment
  was ending up in the API as a symbol nobody could call

## 0.6.0

- **doc comments**: `///` above a `fn`, a `let` or the `module` line, with `@param`, `@returns`,
  `@throws`, `@example`, `@see` and `@deprecated`. They show up in hover, in completion and in
  signature help — where each parameter carries its own `@param` line — and they are highlighted,
  tags and parameter names included. Pressing Enter inside a block continues it
- **Blossom: write the doc comment of the function**: the template of the declaration under the
  cursor, one tabstop for the summary and one per parameter. The same template is offered by the
  lightbulb on an undocumented declaration
- fixed the indentation of the closing bracket: after `fn f() -> (` and Enter, the `)` was left
  glued to the cursor instead of going down and out. The rule that handled `-> (` on its own is
  gone, and the generic bracket rules now do indent *and* outdent

## 0.5.0

- `core.mc.file` no longer exists: `read_nbt`, `write_nbt` and `create_datapack` moved into
  `core.io`, which is now the only place a script opens a file from
- the generator understands `StdLib.extendModule(...)`, so a module of the core the game layer adds
  symbols to shows all of them under one name in completion, hover and the API view

## 0.4.0

- **every event carries its documentation**: the generator now scrapes the per-event sections of
  `docs/api/core.mc.event.md`, so hovering or completing an event name shows what it actually is —
  when it fires, what the arguments mean, what cancelling it does — and the API view prints the same
  prose under each row of the events table
- the two new events `app_reloading` and `app_reloaded` are in the catalogue
- `core.mc.file` is down to `read_nbt`, `write_nbt` and `create_datapack`: `read_file`, `write_file`,
  `list_files`, `delete_file`, `load_app_data` and `store_app_data` were doing the same job as
  `core.io` and are gone, together with the per-app data folder
- `player_command` and `lightning` are described correctly: the first is not cancellable, and the
  second passes a boolean, not an entity
- the events table said cancelling happens on `false`; it happens on `true`

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
