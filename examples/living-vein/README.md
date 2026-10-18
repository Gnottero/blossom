# living-vein

When someone breaks an ore, the app looks at how much of it is left around there, says so above the
hotbar, draws the box of the vein, records it on disk, and when the vein runs out it tells the other
apps with an event.

It is the big example: four modules, a task, a critical section, an event between apps and a `try`.
Its small sibling is [`step-counter`](../step-counter), which fits in a single file.

## Trying it

```
cp -r examples/living-vein <world>/blossom/living-vein
```

```
/blossom script load living-vein
```

Go down a mine and break an ore. Then:

```
/blossom script in living-vein summary()
/blossom script in living-vein latest(5)
/blossom script in living-vein reset()
```

The registry is kept in `<world>/blossom/data/living-vein/registry.json`.

To hear the event from another app:

```
event.declare('vein_depleted', your_fn);        // your_fn(player, type, pos, name)
```

## How it is split

```
src/
├── main.bs        the handlers, the task, the interface for the console
├── geology.bs     the logic: no access to the world, no events, no printing
├── worksite.bs    everything that touches the world: blocks, particles, sounds, HUD
└── registry.bs    the state and the disk, with the critical section
```

The split is not cosmetic: `geology` can be tried without anything happening in game,

```
/blossom script in living-vein geology.count([[[0, 0, 0], 'diamond_ore']], 'diamond_ore')
```

and `main` stays thin enough to be read in one go.

## What it shows

| In the code | The concept |
| --- | --- |
| `import "geology" as geology;` | the alias is mandatory, one module per file ([guide §6](../../docs/guide/06-modules.md)) |
| `fn extreme(...)` in `geology` | private: without `pub` it does not leave the module, and using it from outside is a load error |
| `task.start('inspect', ...)` in the handler | long work leaves the tick ([guide §9](../../docs/guide/09-concurrency.md)) |
| only two `task.dock` | read in one block, think outside, write in one block |
| `task.synchronize(LOCK, ...)` in `registry` | a read-modify-write sequence called from another thread |
| `try(..., 'exception', ...)` around the task | a task that blows up must not disappear silently |
| `reason = _;` inside the `catch` | `_` changes meaning inside a `for`: copy it right away |
| `event.emit('vein_depleted', ...)` | two apps talking without knowing each other ([guide §8](../../docs/guide/08-events.md)) |
| `return(false)` at the top of the handler | an early exit when the block does not interest us |
| `pub fn count(readings: list, type: str)` | types on the public interface, not in the tight loop |
| `"watchdog"` in the manifest | limits declared by the app ([guide §7](../../docs/guide/07-errors.md)) |
