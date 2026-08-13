# Changelog

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
