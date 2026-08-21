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

A block value printed is its name (`'minecraft:stone'`), so it can be compared, put in a map and
saved to json without converting it.

## Reading

### `block(pos)`
The block at that position. Compared with a string it answers on the name.

### `state(pos)` · `state(pos, property)`
The properties of the block as a map, or the value of a single property.

### `data(pos)`
The NBT of the block entity, `null` when that block does not have one.

### `tags(pos)` · `tags(pos, tag)`
The tags the block belongs to, or whether it belongs to the given one.

### `catalog()` · `catalog(tag)`
Every registered block, or the ones of a tag.

## Physical properties

### `solid(pos)` · `air(pos)` · `liquid(pos)`
Whether the block is solid, air, or a liquid.

### `flammable(pos)` · `transparent(pos)`
Whether it burns and whether it lets light through.

### `blocks_movement(pos)` · `suffocates(pos)`
Whether it stops movement, and whether it suffocates whoever is inside it.

### `hardness(pos)` · `blast_resistance(pos)`
How hard it is to break and how well it resists explosions.

### `sound(pos)` · `map_color(pos)`
Sound group and colour on the map.

### `ticks_randomly(pos)` · `power(pos)`
Whether it gets random ticks, and the redstone signal it emits.

## Light

### `light(pos)` · `block_light(pos)` · `sky_light(pos)` · `effective_light(pos)` · `emitted_light(pos)`
The various light levels at that point: total, from blocks, from the sky, the one that counts for
spawning, and the one the block itself emits.

### `see_sky(pos)` · `brightness(pos)`
Whether the point sees the sky, and the brightness as a number between 0 and 1.

### `opacity(pos)` · `blocks_daylight(pos)`
How much light the block absorbs, and whether it stops daylight from going further down.

## Writing

### `set(pos, block, properties...)`
Places a block. The properties are passed as name/value pairs, or as a map. A property the block does
not have is an error, with a suggestion of the right name.

### `destroy(pos)` · `destroy(pos, -1)` · `destroy(pos, tool)`
Breaks the block: with drops, without drops, or as if it had been broken with that tool.

### `harvest(player, pos)`
Breaks the block the way that player would break it, respecting their tool and enchantments.

### `place_item(item, pos, face?, consider_flags?)`
Uses an item on a block, the way a player clicking on it would.

### `update(pos)` · `tick(pos)` · `random_tick(pos)`
Forces a block update, a scheduled tick or a random tick.

### `without_updates(expression)`
Runs the expression without propagating block updates: useful to build in bulk without setting off
redstone and falling blocks at every step.

### `explosion(pos, power?, type?, fire?, source?, cause?)`
Blows something up at that point.

## Positions

### `pos(entity)` · `pos_offset(pos, direction, how_much?)`
The position of an entity, and a position shifted in a direction.
