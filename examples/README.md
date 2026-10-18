# Examples

Three complete projects, in order of difficulty. Install one by copying it under
`<world>/blossom/` and load it with `/blossom script load <name>`.

| Project | Files | What it shows |
| --- | --- | --- |
| [`step-counter`](step-counter) | 1 | player events, a HUD above the hotbar, state saved to disk, public functions for the console |
| [`guardian-tower`](guardian-tower) | 3 | several modules, private symbols, a cancellable event, `try`/`catch` |
| [`living-vein`](living-vein) | 4 | a task outside the tick, `task.dock`, a critical section, an event between apps, logic kept apart from the world |

The guide that explains the concepts is in [`docs/guide/`](../docs/guide/README.md); the function
reference is in [`docs/api/`](../docs/api/Overview.md).

The pure-logic module of `living-vein` (`src/geology.bs`) is run by the core's test suite, in
`GuideSnippetsTest`: it is verified code, not pseudocode.
