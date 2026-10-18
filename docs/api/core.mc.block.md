# `core.mc.block`

Reading and writing the blocks of the world.

```
import "core.mc.block" as block;

block.block([10, 64, 10]);                             // the block at that point
block.set([10, 65, 10], 'oak_stairs', 'facing', 'north');
block.state([10, 65, 10]);                       // {'facing' -> 'north', ...}
```

A position is written as a list `[x, y, z]` or as three loose arguments: the two forms are equivalent
everywhere in the game layer.

A block value printed is its name (`'stone'`; a block from a mod keeps its namespace,
`'mymod:marble'`), so it can be compared, put in a map and saved to json without converting it.

## Reading

### `block.block(pos)`
The block at that position. Compared with a string it answers on the name.

### `block.state(pos)` · `block.state(pos, property)`
The properties of the block as a map, or the value of a single property.

### `block.data(pos)`
The NBT of the block entity, `null` when that block does not have one.

### `block.tags(pos)` · `block.tags(pos, tag)`
The tags the block belongs to, or whether it belongs to the given one.

### `block.catalog()` · `block.catalog(tag)`
Every registered block, or the ones of a tag.

## Physical properties

### `block.solid(pos)` · `block.air(pos)` · `block.liquid(pos)`
Whether the block is solid, air, or a liquid.

### `block.flammable(pos)` · `block.transparent(pos)`
Whether it burns and whether it lets light through.

### `block.blocks_movement(pos)` · `block.suffocates(pos)`
Whether it stops movement, and whether it suffocates whoever is inside it.

### `block.hardness(pos)` · `block.blast_resistance(pos)`
How hard it is to break and how well it resists explosions.

### `block.sound(pos)` · `block.map_color(pos)`
Sound group and colour on the map.

### `block.ticks_randomly(pos)` · `block.power(pos)`
Whether it gets random ticks, and the redstone signal it emits.

## Light

### `block.light(pos)` · `block.light(pos, kind)`
The light at that point. Without a kind it is the total, the higher of the two layers; with one it is
that layer alone:

| Kind | What it is |
| --- | --- |
| `'total'` | the higher of block and sky (the default) |
| `'block'` | what blocks around emit |
| `'sky'` | what comes from the sky |
| `'effective'` | the one that counts for spawning |
| `'emitted'` | what the block itself emits |
| `'brightness'` | a number between 0 and 1 |

A kind that does not exist says which ones do.

### `block.see_sky(pos)`
Whether the point has an unobstructed view of the sky.

For brightness as a number between 0 and 1 there is no separate function: it is a kind of light,
`block.light(pos, 'brightness')`.

### `block.opacity(pos)` · `block.blocks_daylight(pos)`
How much light the block absorbs, and whether it stops daylight from going further down.

## Writing

### `block.set(pos, block, properties...)`
Places a block. The properties are passed as name/value pairs, or as a map. A property the block does
not have is an error, with a suggestion of the right name.

### `block.destroy(pos)` · `block.destroy(pos, -1)` · `block.destroy(pos, tool)`
Breaks the block: with drops, without drops, or as if it had been broken with that tool.

### `block.harvest(player, pos)`
Breaks the block the way that player would break it, respecting their tool and enchantments.

### `block.place_item(item, pos, face?, consider_flags?)`
Uses an item on a block, the way a player clicking on it would.

### `block.update(pos)` · `block.tick(pos)` · `block.random_tick(pos)`
Forces a block update, a scheduled tick or a random tick.

### `block.without_updates(expression)`
Runs the expression without propagating block updates: useful to build in bulk without setting off
redstone and falling blocks at every step.

### `block.explosion(pos, power?, type?, fire?, source?, cause?)`
Blows something up at that point.

## Positions

### `block.pos(entity)` · `block.pos_offset(pos, direction, how_much?)`
The position of an entity, and a position shifted in a direction.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `block_data(p)` | `data(p)` |
| `block_state(p, k?)` | `state(p, k?)` |
| `block_tags(p, t?)` | `tags(p, t?)` |
| `block_list(t?)` | `catalog(t?)` |
| `block_tick(p)` | `tick(p)` |
| `block_sound(p)` | `sound(p)` |
| `map_colour(p)` | `map_color(p)` |
| `create_explosion(...)` | `explosion(...)` |
| `block_light(p)` · `sky_light(p)` · `effective_light(p)` · `emitted_light(p)` · `brightness(p)` | `light(p, kind)` (one question, the layer as an argument) |
