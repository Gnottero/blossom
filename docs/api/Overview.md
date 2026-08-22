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
| [`core.io`](core.io.md) | files, inside declared roots (NBT and datapacks included) |
| [`core.http`](core.http.md) | http requests towards declared hosts |
| [`core.system`](core.system.md) | time, introspection, measurement |

## The Minecraft layer

| Module | What it holds |
| --- | --- |
| [`core.mc.block`](core.mc.block.md) | reading and writing blocks |
| [`core.mc.world`](core.mc.world.md) | time, chunks, biomes, dimensions |
| [`core.mc.scan`](core.mc.scan.md) | walking over volumes of blocks |
| [`core.mc.entity`](core.mc.entity.md) | finding, creating and modifying entities and players |
| [`core.mc.item`](core.mc.item.md) | items, recipes, data components |
| [`core.mc.inventory`](core.mc.inventory.md) | inventories, of whoever carries one and of whatever contains one |
| [`core.mc.structure`](core.mc.structure.md) | structure templates, and the structures of the generator |
| [`core.mc.screen`](core.mc.screen.md) | screens opened for a player |
| [`core.mc.score`](core.mc.score.md) | objectives and scores |
| [`core.mc.team`](core.mc.team.md) | teams |
| [`core.mc.bossbar`](core.mc.bossbar.md) | the bars at the top of the screen |
| [`core.mc.nbt`](core.mc.nbt.md) | the raw data of the game |
| [`core.mc.text`](core.mc.text.md) | messages and formatted text |
| [`core.mc.effect`](core.mc.effect.md) | sounds, particles, post effects |
| [`core.mc.shape`](core.mc.shape.md) | showing what a script is computing |
| [`core.mc.event`](core.mc.event.md) | reacting to what happens |
| [`core.mc.system`](core.mc.system.md) | commands, statistics, diagnostics |
| [`core.mc.data`](core.mc.data.md) | generating the pack's own game content |

## Conventions across the game layer

A handful of shapes recur in every `core.mc.*` module, and knowing them once saves reading them
eighteen times.

| Shape | Written as | Notes |
| --- | --- | --- |
| a position | `[x, y, z]` or three loose arguments | the two are interchangeable everywhere |
| radii | one number or three | one number means the same in all three axes |
| an item | `[name, count, nbt]` | an empty slot is `['minecraft:air', 0, {}]`, not `null` |
| a block | a block value, or its name as a string | a block value compares equal to its name |
| a player | a player value, or their name as a string | many functions also accept a **list** of players |
| a resource id | `'namespace:name'` | the `minecraft:` prefix may be left out |

Two conventions about failure, which hold throughout:

- **absence is `null`**, not an error: a block with no block entity, an entity that is not there, a
  property nothing has.
- **an invalid name is an error**, not `null`: a misspelled block, item, biome or particle throws a
  catchable `unknown_*` exception, and a misspelled *property* or *event* name is a load-time error
  with a suggestion. See [7. Errors](../guide/07-errori.md).

## The prelude

Without importing anything, only the things that are grammar dressed up as functions are available,
plus `print`:

`print`, `if`, `match`, `try`, `throw`, `return`, `break`, `continue`, `loop`, `while`, `for`, `c_for`,
`exit`, `call`, `fn`, `outer`, `not` (the functional name of `!`).

There are also `l` and `m`, which the tokenizer builds for `[ ... ]` and `{ ... }`: they are not
written by hand.

Using a library function without having imported it is not a generic error: the message says which
module it lives in and which import line to add.
