# `core.mc.structure`

Saving, placing and comparing structure templates.

```
import "core.mc.structure" as structure;

structure.structure_save('mine:tower', [0, 64, 0], [8, 80, 8]);
structure.structure_load('mine:tower', [100, 64, 100], 90);
structure.structure_matches('mine:tower', [100, 64, 100], [108, 80, 108], 90);
```

They are the **same templates as the vanilla structure block**, and they live in the same place
(`<world>/generated/<namespace>/structure/<name>.nbt`): what a script saves opens with a structure
block, and the other way round. That is why these functions are worth it compared to copying blocks
by hand.

### `structure_save(name, from, to, with_entities?)`
Saves the volume between the two corners (inclusive) as a template. `with_entities` is `false` by
default. It returns whether the save succeeded.

### `structure_load(name, pos, rotation?, mirror?, with_entities?, integrity?, seed?)`
Places the template. It returns whether it succeeded, `null` when the template does not exist.

| Argument | Values | Default |
| --- | --- | --- |
| `rotation` | `0`, `90`, `180`, `270` (also `'clockwise_90'` etc.) | `0` |
| `mirror` | `'none'`, `'left_right'`, `'front_back'` | `'none'` |
| `with_entities` | | `true` |
| `integrity` | from `0` to `1`: below `1` it removes random blocks, like the structure block | `1` |
| `seed` | the seed of the randomness, to make a ruin repeatable | random |

**`pos` is always the minimum corner of the result**, rotated or mirrored as it may be. Rotation and
mirroring would turn the structure *around* the anchor, and without this correction the same call
with a different rotation would end up somewhere else — which nobody finds obvious. It is also what
makes `structure_load` and `structure_matches` reciprocal.

### `structure_matches(name, from, to, rotation?, mirror?)`
Whether the region between the two corners matches the template. `null` when the template does not
exist — which is different from "does not match".

A region of different size cannot match, and is discarded right away without reading a single block.
A template can have several **palettes**, that is, variants of the same volume: it matches when at
least one of them matches. `structure_void` blocks do not count, as in vanilla.

### `structure_size(name, rotation?)`
The size of the template as `[x, y, z]`, taking the rotation into account, or `null` when it does not
exist.
