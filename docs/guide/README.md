# The Blossom guide

The [API reference](../api/Overview.md) says *what exists*. This guide says *how it is written*: the
grammar, the visibility rules, what an error does when it arrives, and how to put together an app
that runs on a real server.

Every code fragment in here has been executed. The ones that do not touch Minecraft are verified by
`blossom-core/src/test/java/blossom/GuideSnippetsTest.java`, which runs alongside the rest of the
tests: if the language changes and the guide is not updated, the build notices.

## The chapters

| | |
| --- | --- |
| [1. Getting started](01-primi-passi.md) | installing, loading an app, the commands |
| [2. Values](02-valori.md) | the types, the literals, truthiness and equality |
| [3. Operators](03-operatori.md) | precedence, assignment, access, `~`, `<>`, `...` |
| [4. Functions](04-funzioni.md) | `fn`, arguments, `outer`, types, functions as values |
| [5. Control flow](05-controllo.md) | `if`, loops, `map`/`filter`/`reduce`, `break` |
| [6. Modules and projects](06-moduli.md) | `module`, `import`, `pub`, the manifest, dependencies |
| [7. Errors](07-errori.md) | categories, `try`, `throw`, what cannot be caught |
| [8. Events](08-eventi.md) | reacting to the world, cancelling, events between apps |
| [9. Concurrency](09-concorrenza.md) | `task`, `synchronize`, `task.dock`, the watchdog |
| [10. Performance](10-prestazioni.md) | what costs, how to measure it, how to fix it |
| [11. Recipes](11-ricette.md) | ready-made pieces of code, one per problem |
| [12. Server configuration](12-configurazione.md) | `blossomscript.json`: registry, `io`, `http`, watchdog |
| [13. Game content](13-contenuti.md) | recipes, loot tables, tags and advancements inside the pack |
| [14. The tools of `/blossom script`](14-strumenti.md) | `scan`, `fill`, `invoke`, `explain`, `stop`: working on a live server |

## Where to start

- never written a script for Minecraft → [1](01-primi-passi.md), then [11](11-ricette.md): copy and
  change
- written scripts for Minecraft elsewhere → [6](06-moduli.md) and [7](07-errori.md) are the two
  parts you probably do not expect; the rest of the syntax will feel familiar
- setting up a large project → [6](06-moduli.md), [9](09-concorrenza.md),
  [10](10-prestazioni.md)

## The example projects

| Project | What it shows |
| --- | --- |
| [`torre-guardiana`](../../examples/torre-guardiana) | several modules, private symbols, a cancellable event, `try` |
| [`contapassi`](../../examples/contapassi) | a single file: HUD, persistent state, player events |
| [`vena-viva`](../../examples/vena-viva) | four modules, `scan`, a background task, an event between apps, a command |
