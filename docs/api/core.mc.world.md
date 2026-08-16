# `core.mc.world`

The world around the blocks: time, chunks, biomes, structures, dimensions.

```
import "core.mc.world" as world;

world.current_dimension();        // 'minecraft:overworld'
world.day_time();                 // the time of the world
world.biome([0, 64, 0]);          // 'minecraft:plains'
```

## Time

### `world_time()` · `day_time()` · `day_time(new_value)`
The total time of the world and the time of day; with an argument it sets the time.

### `tick_time()` · `last_tick_times()`
The current tick of the server, and the times of the last 100 ticks in milliseconds.

### `game_tick()` · `game_tick(max_milliseconds)`
Forces the server to advance by one tick. With the argument, it keeps running ticks until it has used
up that time. It is what makes the game move forward from a script running inside a tick.

## Weather and world

### `weather()` · `weather(type)` · `weather(type, duration)`
Reads or sets rain and thunder.

### `seed()` · `save()`
The seed of the world, and a forced save to disk.

### `top(type, pos)`
The first free height according to one of the height maps (`'surface'`, `'motion_blocking'`, ...).

### `sample_noise(pos, type)` · `sample_noise()`
Samples a density function of the generator at that point; without arguments it lists the available
names.

## Chunks

### `loaded(pos)` · `loaded_status(pos)` · `loaded_ep(pos)`
Whether the chunk is loaded, in which state it is, and whether it is loaded for entity processing.

### `is_chunk_generated(pos)` · `generation_status(pos)`
Whether the chunk exists on disk, and how far its generation got.

### `chunk_tickets()` · `add_chunk_ticket(pos, type, radius)`
The tickets keeping chunks loaded, and how to add one.

### `reload_chunk(pos)`
Sends the chunk back to the clients.

### `inhabited_time(pos)` · `in_slime_chunk(pos)` · `spawn_potential(pos)`
How much it has been lived in, whether it is a slime chunk, and the spawn potential computed by the
game.

## Biomes and structures

### `biome(pos)` · `biome(name, property)` · `set_biome(pos, name, update?)`
Reads the biome of a point, queries the properties of a biome, or changes it.

### `structures(pos)` · `structures(pos, name)`
The structures covering that point.

### `structure_references(pos)` · `structure_references(pos, name)`
The chunks referring to the structures of that point.

### `structure_eligibility(pos, type?, details?)`
Whether a structure *could* generate in that chunk, without generating it.

### `set_structure(pos, name)` · `set_structure(pos, name, null)`
Generates a structure, or removes its reference.

### `plop(pos, what)`
Places a feature or a structure of the generator at that point (trees, geysers, villages...).

## Points of interest

### `poi(pos)` · `poi(pos, radius, sort?, type?, state?)`
The points of interest (beds, work stations, portals) in an area.

### `set_poi(pos, type, occupancy?)` · `set_poi(pos, null)`
Creates or removes a point of interest.

## Dimensions

### `current_dimension()`
The dimension the script is running in.

### `in_dimension(dimension, expression)`
Evaluates the expression as if it were in another dimension: every world function inside it looks
there.

### `view_distance()` · `enable_hidden_dimensions()`
The render distance of the server, and turning on the normally hidden dimensions.

