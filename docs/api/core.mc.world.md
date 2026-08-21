# `core.mc.world`

The world around the blocks: time, chunks, biomes, dimensions.

```
import "core.mc.world" as world;

world.day_time();                 // the time of the world
world.biome([0, 64, 0]);          // 'minecraft:plains'
```

## Time

### `day_time()` · `day_time(new_value)`
The time of day; with an argument it sets it. The total time of the world is
`system.info('world_time')`.

### `tick_time()`
The current tick of the server. The duration of the last 100 ticks is
`system.info('server_last_tick_times')`.

### `tick()` · `tick(max_milliseconds)`
Forces the server to advance by one tick. With the argument, it keeps running ticks until it has used
up that time. It is what makes the game move forward from a script running inside a tick.

## Weather and world

### `weather()` · `weather(type)` · `weather(type, duration)`
Reads or sets rain and thunder.

### `save()`
Forces a save to disk. The seed of the world is `system.info('world_seed')`.

### `top(type, pos)`
The first free height according to one of the height maps (`'surface'`, `'motion_blocking'`, ...).

### `sample_noise(pos, type)` · `sample_noise()`
Samples a density function of the generator at that point; without arguments it lists the available
names.

## Chunks

### `loaded(pos)` · `load_status(pos)` · `loaded_for_entities(pos)`
Whether the chunk is loaded, in which state it is, and whether it is loaded for entity processing.

### `chunk_generated(pos)` · `generation_status(pos)`
Whether the chunk exists on disk, and how far its generation got.

### `chunk_tickets()` · `add_chunk_ticket(pos, type, radius)`
The tickets keeping chunks loaded, and how to add one.

### `reload_chunk(pos)`
Sends the chunk back to the clients.

### `inhabited_time(pos)` · `in_slime_chunk(pos)` · `spawn_potential(pos)`
How much it has been lived in, whether it is a slime chunk, and the spawn potential computed by the
game.

## Biomes

### `biome(pos)` · `biome(name, property)` · `set_biome(pos, name, update?)`
Reads the biome of a point, queries the properties of a biome, or changes it.

Le strutture del generatore stanno in [`core.mc.structure`](core.mc.structure.md), insieme ai
template: sono lo stesso dominio, e stavano in due moduli per un incidente di storia.

## Points of interest

### `poi(pos)` · `poi(pos, radius, sort?, type?, state?)`
The points of interest (beds, work stations, portals) in an area.

### `set_poi(pos, type, occupancy?)` · `set_poi(pos, null)`
Creates or removes a point of interest.

## Dimensions

### `in_dimension(dimension, expression)`
Evaluates the expression as if it were in another dimension: every world function inside it looks
there. The dimension the script is running in is `system.info('source_dimension')`.


---

## Coming from the old names

| Before | Now |
| --- | --- |
| `loaded_ep(pos)` | `loaded_for_entities(pos)` |
| `loaded_status(pos)` | `load_status(pos)` |
| `is_chunk_generated(pos)` | `chunk_generated(pos)` |
| `plop(pos, what)` | `place_feature(pos, what)` |
| `game_tick(ms?)` | `tick(ms?)` |
| `structures`, `set_structure`, `structure_references`, `structure_eligibility` | [`core.mc.structure`](core.mc.structure.md) |
