# `core.mc.command`

The app's own command: `/<app> ...`, with typed arguments, suggestions while typing and its own
permission.

```
import "core.mc.command" as command;
import "core.mc.block" as block;

fn plant(pos, what) -> (block.set(pos, what); 1);

command.register('plant <pos> <block>', plant);
command.register('ring <radius>', fn(r) -> draw_ring(r));
command.argument('radius', {'type' -> 'int', 'min' -> 1, 'max' -> 32, 'suggest' -> [4, 8]});
command.permission('ops');
```

An app called `garden` now answers to `/garden plant ~ ~ ~ oak_sapling` and `/garden ring 8`.

The command is declared from the code, like the events, and the function is passed as a **value**:
it stays bound to the module that defined it, and the number of its parameters is checked against
the path when `register` runs, not when someone types the command. The checks that need every path
at once — two paths taking different arguments at the same point, a name already taken by another
command — run when the load is over, and are a load error like any other.

The command lives as long as the app: unloading takes it away, reloading puts back the new one, and
the game's `/reload`, which rebuilds the commands from scratch, finds it again.

### `command.register(path, function)` · `command.register(path, function, fixed_arguments)`
Adds a path under `/<app>`. The path is words to type as they are and `<name>` arguments, separated
by spaces; the empty path `''` is `/<app>` alone.

`function` receives the typed arguments in the order of the path, then the `fixed_arguments` (a
list), which are the same on every call: the way to send several paths to one function that tells
them apart.

```
command.register('give <entities> <effect>', apply, [30]);           // apply(targets, effect, 30)
command.register('give <entities> <effect> <seconds>', apply);        // apply(targets, effect, seconds)
```

What the function returns becomes the result of the command, the number a command block or
`/execute store` sees: a number as it is, anything else `1` if true and `0` if false. Nothing is
printed on its own: whoever wants to answer the player does it with `print` or `core.mc.text`.

An error inside the function reaches whoever typed the command, with its position and stack, and the
command returns `0`.

### `command.argument(name, spec)`
Declares the type of the arguments called `name`. `spec` is a map with a `'type'`, one of the
built-in types below, and the modifiers of that type:

```
command.argument('amount', {'type' -> 'int', 'min' -> 1, 'max' -> 64});
command.argument('mode', {'type' -> 'term', 'options' -> ['fast', 'safe'], 'case_sensitive' -> false});
command.argument('owner', {'type' -> 'term', 'suggester' -> fn(typed) -> owners()});
```

The type of `<name>` is found from the name, as in Scarpet: the app's own types first, then the
built-in ones, trying the whole name and then every suffix after an underscore, longest first.
`<from_pos>` is a `pos`, `<target_biome>` a `biome`, `<count_int>` an `int`; a name that matches
nothing is a `string`. A type declared with `command.argument` wins over a built-in of the same name.

A base type that does not exist and a modifier the type does not have are errors with the right name
suggested: `{'type' -> 'int', 'mni' -> 1}` stops the load, it does not become an `int` without a
minimum.

Every type accepts:

| Modifier | What it does |
| --- | --- |
| `suggest` | a fixed list of suggestions shown while typing |
| `suggester` | a function `fn(typed) -> [...]`, where `typed` is the map of the arguments typed so far |
| `case_sensitive` | whether the suggestions (and `options`) care about case, `true` by default |

### `command.permission(who)`
Who can use the command:

| `who` | Who |
| --- | --- |
| `'all'` | everyone (the default) |
| `'ops'` | operators, permission level 2 |
| `1` … `4` | that permission level or higher |
| `'players'` | only players, from chat |
| `'server'` | only the console and command blocks |
| a function | `fn(player) -> bool`, `player` is `null` from the console |

The game decides which commands a player can see when they join and when the commands change: a
function that answers differently from one minute to the next may show the command to someone who
cannot use it, but typing it is always checked again.

### `command.allow_conflicts()` · `command.allow_conflicts(false)`
Two paths that take different arguments at the same point, `give <player>` and `give <item>`, are a
load error: brigadier accepts them and then suggests badly, which looks like a command that works.
Calling this lets them through anyway.

### `command.list()`
The paths the app has declared so far, as they are typed: `['/garden plant <pos> <block>', ...]`.

### `command.types()`
The names of the built-in argument types.

## The argument types

| Type | What the function receives | Modifiers |
| --- | --- | --- |
| `string` | a string, quoted if it holds spaces | `options` |
| `term` | a single word | `options` |
| `text` | the rest of the line; it has to be the last argument | `options` |
| `bool` | `true` or `false` | |
| `int` | an integer | `min`, `max` |
| `float` | a number | `min`, `max` |
| `yaw` | an angle | |
| `pos` | `[x, y, z]` of a block | `loaded`: the position must be loaded |
| `location` | `[x, y, z]`, decimals allowed | `block_centered`, `true` by default |
| `columnpos` | `[x, z]` | |
| `surfacelocation` | `[x, z]`, decimals allowed | |
| `rotation` | `[yaw, pitch]` | |
| `swizzle` | a set of axes, like `'xz'` | |
| `block` | a block value, with state and data | |
| `blockpredicate` | `[block, tag, properties, nbt]`; block or tag is `null`, never both | |
| `item` | an item `[name, 1, nbt]` | |
| `slot` | `[inventory, index]`, as [`core.mc.inventory`](core.mc.inventory.md) uses them | `restrict`: `player`, `enderchest`, `equipment`, `armor`, `weapon`, `container`, `villager`, `horse` |
| `entities` | a list of entities, from a selector | `single` (an entity or `null`), `players` |
| `players` | a list of player names, online or not | `single` |
| `scoreholder` | a list of score holders | `single` |
| `dimension` | the name of a dimension | |
| `anchor` | `'feet'` or `'eyes'` | |
| `entitytype` | the name of an entity type | |
| `biome` | the name of a biome or of a biome tag | |
| `effect`, `enchantment`, `attribute`, `recipe`, `advancement`, `loottable`, `lootcondition` | the name of one, from its registry | |
| `particle` | the particle, as `core.mc.effect` takes it | |
| `sound`, `boss`, `storekey` | an identifier, with the game's suggestions | |
| `identifier` | any identifier | `options` |
| `teamcolor` | `[name, colour]` | |
| `team` | the name of a team | |
| `objective` | `[name, criterion]` | |
| `criterion` | the name of a criterion | |
| `scoreboardslot` | the name of a display slot | |
| `tag` | an NBT compound | `allow_element`: any NBT value, not only a compound |
| `path` | an NBT path, as a string | |
| `message` | formatted text, with selectors expanded | |
| `time` | a duration, in ticks | |
| `uuid` | a uuid, as a string | |
| `floatrange`, `intrange` | `[min, max]`, either may be `null` | |

## Coming from Scarpet

| Scarpet | Blossom |
| --- | --- |
| `__config() -> {'commands' -> {'add <pos>' -> 'add'}}` | `command.register('add <pos>', add)` |
| `'add <pos>' -> ['add', 5]` | `command.register('add <pos>', add, [5])` |
| `'arguments' -> {'radius' -> {...}}` | `command.argument('radius', {...})` |
| `'command_permission' -> 'ops'` | `command.permission('ops')` |
| `'allow_command_conflicts' -> true` | `command.allow_conflicts()` |
| `__command()` and the public functions as subcommands | not there: every path is declared |
| `'legacy_command_type_support'` | not there: the types come from `command.argument` and the names |
