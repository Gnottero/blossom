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

### `player()` · `player(name)` · `player('all' | 'survival' | 'creative' | 'spectating' | ...)`
The player running the script, one by name, or the list of the ones in a category.

### `entity_list(type)`
Every entity of a type in the world (`'*'` for all of them, `'!player'` for everything but players).

### `entity_area(type, centre, radius)`
The entities of a type inside an area.

### `entity_selector(selector)`
The entities a vanilla selector (`@e[type=cow,distance=..10]`) would pick.

### `entity_id(id | uuid)` · `entity_types(type?)`
An entity from its numeric id or uuid, and the registered entity types.

## Querying and modifying

### `query(entity, what, arguments...)`
Reads an attribute. There are many: `pos`, `x`/`y`/`z`, `motion`, `name`, `uuid`, `health`, `food`,
`air`, `on_ground`, `sneaking`, `sprinting`, `gamemode`, `holds`, `inventory`, `effect`, `nbt`,
`dimension`, `target`, `brain`, `look`, `facing`, `age`, `fire`, `is_burning`, `hunger`, `saturation`,
`portal_cooldown`, and others. `query(entity, 'nbt')` gives all the rest.

### `modify(entity, what, value...)`
Writes an attribute: `pos`, `motion`, `motion_x`/`y`/`z`, `custom_name`, `health`, `food`, `air`,
`gamemode`, `nbt`, `effect`, `nbt_merge`, `persistence`, `jump`, `kill`, `dismount`, `mount`, `tag`,
`fire`, `silent`, `gravity`, `invulnerable`, `pickup_delay`, `portal_cooldown`, and others.

### `spawn(type, pos, nbt?)`
Creates an entity. The NBT is the one the game would use in a `/summon` command.

## Events of a single entity

### `entity_event(entity, event, function, fixed_arguments...)`
Attaches a handler to *that* entity: `on_tick`, `on_removed`, `on_death`, `on_damage`, `on_move`.
The handler lives inside the entity, so it follows it between worlds and disappears with it. Passing
`null` in place of the function removes it.

The first argument passed to the handler is always the entity itself; the number of arguments is
checked right away, not at the first event.

## Markers

### `create_marker(name, pos, block?, tag?)` · `remove_all_markers()`
Markers shown while debugging a script; `remove_all_markers` cleans up the ones of the app.

### `entity_load_handler(types, function)`
Calls `function(entity)` every time an entity of one of those types appears. `types` is a type or a
list of types (`'zombie'`, `['zombie', 'minecraft:husk']`); with `null` you hear about all of them.

```
entity.entity_load_handler(['zombie', 'skeleton'], on_undead);
```

The filter by type happens **before** calling the handler, so listening for a rare type does not cost
a call for every entity appearing in the world. To really hear about all of them there is also the
`entity_loads` event in [`core.mc.event`](core.mc.event.md); this function is the same thing with the
filter included.

In Scarpet this was a family of over a hundred events generated from the entity registry, one per
type, with two signature variants, one of them already deprecated.

## The client of the player

### `has_client(player)`
Whether that player has Blossom on the client too — that is, whether they will see the shapes of
[`core.mc.shape`](core.mc.shape.md) really drawn instead of approximated with particles.

It is not needed to draw: `draw_shape` decides on its own, player by player. It is for whoever wants
to offer something different to those who have the client, without having to guess.
