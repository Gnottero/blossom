# `core.mc.world`

The world around the blocks: time, chunks, biomes, dimensions.

```
import "core.mc.world" as world;

world.day_time();                 // the time of the world
world.biome([0, 64, 0]);          // 'minecraft:plains'
```

## Time

### `world.day_time()` · `world.day_time(new_value)`
The time of day; with an argument it sets it. The total time of the world is
`system.info('world_time')`.

### `world.tick_time()`
The current tick of the server. The duration of the last 100 ticks is
`system.info('server_last_tick_times')`.

### `world.tick()` · `world.tick(max_milliseconds)`
Forces the server to advance by one tick. With the argument, it keeps running ticks until it has used
up that time. It is what makes the game move forward from a script running inside a tick.

## Weather and world

### `world.weather()` · `world.weather(type)` · `world.weather(type, duration)`
Reads or sets rain and thunder.

### `world.save()`
Forces a save to disk. The seed of the world is `system.info('world_seed')`.

### `world.top(type, pos)`
The first free height according to one of the height maps (`'surface'`, `'motion_blocking'`, ...).

### `world.sample_noise(pos, type)` · `world.sample_noise()`
Samples a density function of the generator at that point; without arguments it lists the available
names.

## Chunks

### `world.loaded(pos)` · `world.load_status(pos)` · `world.loaded_for_entities(pos)`
Whether the chunk is loaded, in which state it is, and whether it is loaded for entity processing.

### `world.chunk_generated(pos)` · `world.generation_status(pos)`
Whether the chunk exists on disk, and how far its generation got.

### `world.chunk_tickets()` · `world.add_chunk_ticket(pos, type, radius)`
The tickets keeping chunks loaded, and how to add one.

### `world.reload_chunk(pos)`
Sends the chunk back to the clients.

### `world.inhabited_time(pos)` · `world.in_slime_chunk(pos)` · `world.spawn_potential(pos)`
How much it has been lived in, whether it is a slime chunk, and the spawn potential computed by the
game.

## Biomes

### `world.biome(pos)` · `world.biome(name, property)` · `world.set_biome(pos, name, update?)`
Reads the biome of a point, queries the properties of a biome, or changes it.

The generator's structures live in [`core.mc.structure`](core.mc.structure.md), alongside the
templates: they are the same domain, and they were in two modules by an accident of history.

## Points of interest

### `world.poi(pos)` · `world.poi(pos, radius, sort?, type?, state?)`
The points of interest (beds, work stations, portals) in an area.

### `world.set_poi(pos, type, occupancy?)` · `world.set_poi(pos, null)`
Creates or removes a point of interest.

## Dimensions

### `world.in_dimension(dimension, expression)`
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
