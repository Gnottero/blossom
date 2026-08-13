# `core.mc.scan`

Walking over volumes of blocks without writing three nested loops.

```
import "core.mc.scan" as scan;
import "core.mc.block" as block;

// every block in a 5x5x5 cube around the origin
scan.scan([0, 64, 0], [2, 2, 2], block.set(_x, _y, _z, 'stone'));
```

Inside the body, `_x`, `_y`, `_z` are the coordinates of the current point and `_` is the block.

### `scan(centre, radii, expression)` · `scan(centre, radii_down, radii_up, expression)`
Walks over a box around the centre. The radii are **three numbers** or a triple, like positions:
`scan(pos, [2, 1, 2], ...)` and `scan(pos, 2, 1, 2, ...)` are the same thing. With two groups of
radii the volume can be asymmetric.

It returns how many times the expression came out true: counting the blocks that satisfy a condition
is a single line.

### `volume(from, to, expression)`
Like `scan`, but the volume is given with two corners instead of centre and radius.

### `rect(centre, radius?, skip?)` · `diamond(centre, radius?, height?)`
The positions of a box or of an octahedron, as a lazy list: they are consumed as they go, without
building the whole volume in memory.

### `neighbours(pos)`
The six adjacent positions.
