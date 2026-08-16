# The Blossom API

The standard library is organised in modules: nothing is available by magic, everything is imported
with an explicit alias. Knowing where a function comes from is part of knowing how to read it.

```
import "core.math" as math;
import "core.list" as list;
```

| Module | What it holds |
| --- | --- |
| [`core.math`](core.math.md) | arithmetic, trigonometry, randomness, procedural noise |
| [`core.string`](core.string.md) | strings, regular expressions, encodings |
| [`core.list`](core.list.md) | lists: building, transforming, sorting |
| [`core.map`](core.map.md) | maps: keys, values, pairs |
| [`core.json`](core.json.md) | conversion to and from json |
| [`core.task`](core.task.md) | concurrency, waiting, critical sections |
| [`core.io`](core.io.md) | the disk outside the folder of the app, inside declared roots |
| [`core.http`](core.http.md) | http requests towards declared hosts |
| [`core.system`](core.system.md) | time, introspection, measurement |

## The Minecraft layer

| Module | What it holds |
| --- | --- |
| [`core.mc.block`](core.mc.block.md) | reading and writing blocks |
| [`core.mc.world`](core.mc.world.md) | time, chunks, biomes, structures, dimensions |
| [`core.mc.scan`](core.mc.scan.md) | walking over volumes of blocks |
| [`core.mc.entity`](core.mc.entity.md) | finding, creating and modifying entities and players |
| [`core.mc.item`](core.mc.item.md) | inventories, items, recipes |
| [`core.mc.structure`](core.mc.structure.md) | saving, placing and comparing structure templates |
| [`core.mc.screen`](core.mc.screen.md) | screens opened for a player |
| [`core.mc.scoreboard`](core.mc.scoreboard.md) | objectives, teams, boss bars |
| [`core.mc.nbt`](core.mc.nbt.md) | the raw data of the game |
| [`core.mc.text`](core.mc.text.md) | messages and formatted text |
| [`core.mc.effect`](core.mc.effect.md) | sounds, particles, post effects |
| [`core.mc.shape`](core.mc.shape.md) | showing what a script is computing |
| [`core.mc.event`](core.mc.event.md) | reacting to what happens |
| [`core.mc.file`](core.mc.file.md) | the data of the app on disk |
| [`core.mc.system`](core.mc.system.md) | commands, statistics, diagnostics |

## The prelude

Without importing anything, only the things that are grammar dressed up as functions are available,
plus `print`:

`print`, `if`, `try`, `throw`, `return`, `break`, `continue`, `loop`, `while`, `for`, `c_for`,
`exit`, `call`, `outer`, `not` (the functional name of `!`).

There are also `l` and `m`, which the tokenizer builds for `[ ... ]` and `{ ... }`: they are not
written by hand.

Using a library function without having imported it is not a generic error: the message says which
module it lives in and which import line to add.
