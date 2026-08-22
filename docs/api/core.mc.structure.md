# `core.mc.structure`

Saving, placing and comparing structure templates.

```
import "core.mc.structure" as structure;

structure.save('mine:tower', [0, 64, 0], [8, 80, 8]);
structure.load('mine:tower', [100, 64, 100], 90);
structure.matches('mine:tower', [100, 64, 100], [108, 80, 108], 90);
```

They are the **same templates as the vanilla structure block**, and they live in the same place
(`<world>/generated/<namespace>/structure/<name>.nbt`): what a script saves opens with a structure
block, and the other way round. That is why these functions are worth it compared to copying blocks
by hand.

### `structure.save(name, from, to, with_entities?)`
Saves the volume between the two corners (inclusive) as a template. `with_entities` is `false` by
default. It returns whether the save succeeded.

### `structure.load(name, pos, rotation?, mirror?, with_entities?, integrity?, seed?)`
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
makes `load` and `matches` reciprocal.

### `structure.matches(name, from, to, rotation?, mirror?)`
Whether the region between the two corners matches the template. `null` when the template does not
exist — which is different from "does not match".

A region of different size cannot match, and is discarded right away without reading a single block.
A template can have several **palettes**, that is, variants of the same volume: it matches when at
least one of them matches. `structure_void` blocks do not count, as in vanilla.

### `structure.size(name, rotation?)`
The size of the template as `[x, y, z]`, taking the rotation into account, or `null` when it does not
exist.

---

## The generator's structures

The four functions above are about **templates**: files you save and place yourself. The four below
are about the structures the **world generator** places — villages, fortresses, ruins — which are
not templates but entries of the game's structure registry, recorded chunk by chunk.

They are in this module because the subject is the same, even though the mechanism is not.

### `structure.at(pos)` · `structure.at(pos, name)`
The generated structures whose start is recorded in the chunk containing `pos`.

Without a name, a map from structure name to its bounding box, given as
`[[min_x, min_y, min_z], [max_x, max_y, max_z]]`. With a name, the box of that one structure alone,
or `null` when it does not start in this chunk.

Note the "start" part: a village whose start lies in the neighbouring chunk does not appear here even
if part of it extends over `pos`.

```
structure.at([100, 64, 100]);                       // {'minecraft:village_plains' -> [...]}
structure.at([100, 64, 100], 'minecraft:village_plains');
```

### `structure.references(pos)` · `structure.references(pos, name)`
Which structures **reach into** the chunk containing `pos`, as opposed to starting there.

Without a name, the list of structure names that have a reference in this chunk. With a name, the
list of the chunk positions where those structures start, as `[x, z]` pairs — which is how you walk
from a piece of a village back to the chunk that owns it. An unknown structure name gives `null`, a
known one with nothing here gives an empty list.

### `structure.eligibility(pos)` · `structure.eligibility(pos, name)` · `structure.eligibility(pos, name, with_size)`
Whether the generator **would** start a structure at that position, asked without generating
anything. This is the function for finding out where something can appear before the terrain around
it exists.

- with no name: a map of every structure that could start there;
- with a name: `true`, or `null` when it could not. The name may also be a structure **type**, in
  which case every structure of that type is examined and the answer is a map;
- with `with_size` set to true: instead of `true`, the details of the start — its bounding box and
  pieces — and instead of `null`, the same absence.

An unknown structure name throws `unknown_structure`.

```
structure.eligibility([100, 64, 100], 'minecraft:village_plains');          // true or null
structure.eligibility([100, 64, 100], 'minecraft:village_plains', true);    // the box and the pieces
structure.eligibility([100, 64, 100]);                                      // everything possible here
```

### `structure.place(pos, name)` · `structure.place(pos, name, null)`
With two arguments, generates that structure at that position, laid out on the generator's grid as
the world itself would place it, and returns whether it succeeded.

With `null` as the third argument, it does the opposite: it **removes** the record of that structure
from the chunk — the start and every reference to it in the chunks the structure spans. That deletes
the bookkeeping, not the blocks, so the buildings stay standing while the game stops considering
them part of a structure. It returns `true` when there was something to remove.

An unknown structure name throws `unknown_structure`. Both forms are world modifications and run on
the server thread.

```
structure.place([100, 64, 100], 'minecraft:village_plains');
structure.place([100, 64, 100], 'minecraft:village_plains', null);      // forget it is there
```

To place a single generator **feature** rather than a whole structure — a tree, an ore vein, a patch
of grass — there is `world.place_feature` in [`core.mc.world`](core.mc.world.md).
