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
| [1. Getting started](01-getting-started.md) | installing, loading an app, the commands |
| [2. Values](02-values.md) | the types, the literals, truthiness and equality |
| [3. Operators](03-operators.md) | precedence, assignment, access, `~`, `<>`, `...` |
| [4. Functions](04-functions.md) | `fn`, arguments, `outer`, types, functions as values |
| [5. Control flow](05-control-flow.md) | `if`, loops, `map`/`filter`/`reduce`, `break` |
| [6. Modules and projects](06-modules.md) | `module`, `import`, `pub`, the manifest, dependencies |
| [7. Errors](07-errors.md) | categories, `try`, `throw`, what cannot be caught |
| [8. Events](08-events.md) | reacting to the world, cancelling, events between apps |
| [9. Concurrency](09-concurrency.md) | `task`, `synchronize`, `task.dock`, the watchdog |
| [10. Performance](10-performance.md) | what costs, how to measure it, how to fix it |
| [11. Recipes](11-recipes.md) | ready-made pieces of code, one per problem |
| [12. Server configuration](12-configuration.md) | `blossomscript.json`: registry, `io`, `http`, watchdog |
| [13. Game content](13-game-content.md) | recipes, loot tables, tags and advancements inside the pack |
| [14. The tools of `/blossom script`](14-script-tools.md) | `scan`, `fill`, `invoke`, `explain`, `stop`: working on a live server |

## Where to start

- never written a script for Minecraft → [1](01-getting-started.md), then [11](11-recipes.md): copy and
  change
- written scripts for Minecraft elsewhere → [6](06-modules.md) and [7](07-errors.md) are the two
  parts you probably do not expect; the rest of the syntax will feel familiar
- setting up a large project → [6](06-modules.md), [9](09-concurrency.md),
  [10](10-performance.md)

## The example projects

| Project | What it shows |
| --- | --- |
| [`guardian-tower`](../../examples/guardian-tower) | several modules, private symbols, a cancellable event, `try` |
| [`step-counter`](../../examples/step-counter) | a single file: HUD, persistent state, player events |
| [`living-vein`](../../examples/living-vein) | four modules, `scan`, a background task, an event between apps, a command |
