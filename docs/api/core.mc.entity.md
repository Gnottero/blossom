# `core.mc.entity`

Finding, creating, querying and modifying entities - players included.

```
import "core.mc.entity" as entity;

entity.player();                            // whoever launched the script
entity.player('all');                       // every connected player
entity.query(e, 'health');                  // health
entity.modify(e, 'motion', [0, 1, 0]);      // a push upwards
```

## Finding

### `entity.player()`
The player who launched the script, or `null` when nobody did: a `tick` handler has no caller.

### `entity.player(name)`
The connected player with that name, or `null`.

### `entity.player(category)`
A **list** of players. The categories are:

| Category | Who |
| --- | --- |
| `'all'` | every player connected to the server, in every dimension |
| `'*'` | every player in the current dimension |
| `'survival'` | those in survival or adventure mode |
| `'creative'` | those in creative |
| `'spectating'` · `'!spectating'` | those spectating, and those not |

```
entity.player();                    // whoever typed the command
entity.player('ada');               // one by name
entity.player('all');               // a list
```

### `entity.list(type)`
Every entity of a type in the world, as a list. The type is an entity id (`'zombie'`,
`'minecraft:cow'`) or one of the classes below; prefixing a class with `!` inverts it.

`*` (everything), `valid` (alive), `living`, `regular`, `undead`, `arthropod`, `aquatic`,
`illager`, `projectile`, `minecarts`, each with its `!` counterpart.

### `entity.area(type, centre, radius)`
The entities of that type inside a box centred on `centre`. The radius is one number or three.
Costly on large radii: it walks entities, so keep it as small as the question allows.

### `entity.select(selector)`
The entities a vanilla target selector would pick, written exactly as in a command:
`entity.select('@e[type=cow,distance=..10]')`. Use it when the query is easier to say in selector
syntax than to assemble; `list` and `area` are cheaper for the simple cases.

### `entity.by_id(id)`
An entity from its numeric id or from its uuid string, or `null` when nothing has it.

### `entity.types()` · `entity.types(type)`
The registered entity type names, or the ones belonging to one of the classes above.

## Querying and modifying

### `entity.query(entity, what, arguments...)`
Reads an attribute. One function rather than a hundred, because the set of things you can ask about
an entity is long and open, and a name in a string is checked against the list below with a
suggestion when it is misspelled. Some attributes take further arguments: `has_tag` takes the tag,
`attribute` takes the attribute name, `effect` takes an effect name.

`entity.query(e, 'nbt')` gives everything else: whatever the curated list does not cover is in
there, in the game's own raw form.

**Position and movement**
`pos`, `x`, `y`, `z`, `location` (position plus rotation), `motion`, `motion_x`, `motion_y`,
`motion_z`, `look` (the unit vector of the gaze), `facing`, `yaw`, `pitch`, `head_yaw`, `body_yaw`,
`pose`, `on_ground`, `path`, `trace`, `home`, `spawn_point`, `dimension`.

**Identity and shape**
`id`, `uuid`, `type`, `name`, `custom_name`, `display_name`, `command_name`, `category`, `height`,
`width`, `eye_height`, `is_baby`, `age`, `breeding_age`, `count`, `removed`, `test`.

**State and vital signs**
`health`, `absorption`, `air`, `fire`, `is_burning`, `frost`, `is_freezing`, `immune_to_fire`,
`immune_to_frost`, `invulnerable`, `gravity`, `silent`, `persistence`, `despawn_timer`,
`portal_cooldown`, `portal_timer`, `pickup_delay`, `effect`, `attribute`, `nbt`.

**Behaviour**
`sneaking`, `sprinting`, `swimming`, `swinging`, `jumping`, `flying`, `target`, `brain`,
`active_block`, `breaking_progress`, `holds`, `item`, `offering_flower`, `blue_skull`.

**Riding**
`mount`, `passengers`, `is_riding`, `is_ridden`, `unmountable`.

**Players only**
`gamemode`, `gamemode_id`, `player_type`, `permission_level`, `may_fly`, `may_build`, `insta_build`,
`fly_speed`, `walk_speed`, `selected_slot`, `hunger`, `saturation`, `exhaustion`, `xp`, `xp_level`,
`xp_progress`, `score`, `ping`, `language`, `client_brand`, `input`.

`input` is the set of movement keys the client last reported as held down: a map with `forward`,
`backward`, `left`, `right`, `jump`, `sneak` and `sprint`, each `true` or `false`. It is updated also
while the player is not riding anything.

**Tags and teams**
`tags`, `has_tag`, `entity_tags`, `has_entity_tag`, `scoreboard_tags`, `has_scoreboard_tag`, `team`.

```
entity.query(e, 'health');                      // 20
entity.query(e, 'pos');                         // [10.5, 64.0, 10.5]
entity.query(e, 'has_tag', 'guard');            // true
entity.query(player, 'gamemode');               // 'survival'
entity.query(e, 'nbt');                         // everything else
```

### `entity.modify(entity, what, value...)`
Writes an attribute. The names are the reading ones where the operation makes sense both ways, plus
a few that are actions rather than values.

**Position and movement**
`pos`, `x`, `y`, `z`, `location`, `move` (a relative displacement), `motion`, `motion_x`,
`motion_y`, `motion_z`, `accelerate`, `look`, `yaw`, `pitch`, `head_yaw`, `body_yaw`, `jump`,
`jumping`, `no_clip`, `home`, `spawn_point`.

**State and vital signs**
`health`, `absorption`, `air`, `fire`, `frost`, `invulnerable`, `gravity`, `silent`, `persistence`,
`despawn_timer`, `portal_cooldown`, `portal_timer`, `pickup_delay`, `effect`, `age`, `breeding_age`,
`custom_name`, `Name`, `item`, `nbt`, `Data`, `nbt_merge`.

**Behaviour and actions**
`ai`, `talk`, `swing`, `kill`, `remove`, `breaking_progress`, `offering_flower`, `blue_skull`.

**Riding**
`mount`, `dismount`, `mount_passengers`, `drop_passengers`, `unmountable`.

**Players only**
`gamemode`, `may_fly`, `may_build`, `flying`, `insta_build`, `fly_speed`, `walk_speed`,
`selected_slot`, `hunger`, `saturation`, `exhaustion`, `add_exhaustion`, `xp_level`, `xp_progress`,
`xp_score`, `add_xp`.

**Tags**
`tag`, `clear_tag`.

`nbt` replaces the whole tag, `nbt_merge` merges into it: the second is almost always the one you
want, since replacing wholesale discards everything the game put there.

```
entity.modify(e, 'motion', [0, 1, 0]);          // a push upwards
entity.modify(e, 'health', 20);
entity.modify(e, 'tag', 'guard');
entity.modify(e, 'nbt_merge', {'Silent' -> 1});
entity.modify(e, 'kill');                       // an action, no value
```

### `breaking_progress`: the cracks on a block

`entity.query(player, 'breaking_progress')` gives how far the break in progress has got (0 to 9, or
`null` when there is none). `entity.modify(player, 'breaking_progress', n)` **draws** it: `n` goes
from 0 to 9, and `-1` removes the animation. It is for a script that computes breaking on its own
(a custom block with a hardness of its own) and wants to show what is happening.

The packet travels with the player's id negated, so an animation written by a script and the one the
game sends by itself never overwrite each other.

Two limits, which come from the client and not from here: the cracks are drawn **on the block the
player is actually breaking** (the one in `active_block`), and only if that block has a model: on a
barrier, which is invisible, the client draws nothing.

### `entity.spawn(type, pos, nbt?)`
Creates an entity. The NBT is the one the game would use in a `/summon` command.

## Events of a single entity

### `entity.on(entity, event, function, fixed_arguments...)`
Attaches a handler to *that* entity: `on_tick`, `on_removed`, `on_death`, `on_damage`, `on_move`.
The handler lives inside the entity, so it follows it between worlds and disappears with it. Passing
`null` in place of the function removes it.

The first argument passed to the handler is always the entity itself; the number of arguments is
checked right away, not at the first event.

### `entity.on_load(types, function)`
Calls `function(entity)` every time an entity of one of those types appears. `types` is a type or a
list of types (`'zombie'`, `['zombie', 'minecraft:husk']`); with `null` you hear about all of them.

```
entity.on_load(['zombie', 'skeleton'], on_undead);
```

The filter by type happens **before** calling the handler, so listening for a rare type does not cost
a call for every entity appearing in the world. To really hear about all of them there is also the
`entity_loads` event in [`core.mc.event`](core.mc.event.md); this function is the same thing with the
filter included.

One event with a filter, rather than one generated event per entity type: a catalogue of a hundred
names is not something anybody reads.

## The client of the player

### `entity.has_client(player)`
Whether that player has Blossom on the client too, that is, whether they will see the shapes of
[`core.mc.shape`](core.mc.shape.md) really drawn instead of approximated with particles.

It is not needed to draw: `draw_shape` decides on its own, player by player. It is for whoever wants
to offer something different to those who have the client, without having to guess.
