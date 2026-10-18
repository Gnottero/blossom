# step-counter

An app in a single file: it counts each player's jumps, broken blocks and deaths, shows them above
the hotbar and saves them to disk.

It is the example to read first. Its big sibling is [`living-vein`](../living-vein), which has
several modules and uses tasks.

## Trying it

```
cp -r examples/step-counter <world>/blossom/step-counter
```

```
/blossom script load step-counter
```

Jump, break something: the line above the hotbar updates. Then:

```
/blossom script in step-counter leaderboard('broken')
/blossom script in step-counter reset()
```

The data ends up in `<world>/blossom/data/step-counter/counts.json` and is read back on load.

## What it shows

| In the code | The concept |
| --- | --- |
| `let mut counts = {}` | module state: functions see it, a bare assignment does not ([guide §4](../../docs/guide/04-functions.md)) |
| `event.on(...)` in the module body | handlers are registered on load, and die with the app ([guide §8](../../docs/guide/08-events.md)) |
| `false` at the end of every handler | a true value would cancel the action |
| `text.hud(player, ...)` | a line above the hotbar that stays until you change it |
| `io.read_json` / `write_json` | persistence in the `data` root, under `step-counter/` |
| saving every 6000 ticks | a server that crashes never calls `server_shuts_down` |
| `pub fn leaderboard(stat: str)` | the interface for the console, with a type on the parameter |
| `(STATS ~ stat) == null` | `~` returns the index, and index `0` is falsy: compare with `null` |
