# `core.mc.scan`

Walking over volumes of blocks without writing three nested loops.

```
import "core.mc.scan" as scan;
import "core.mc.block" as block;

// every block in a 5x5x5 cube around the origin
scan.scan([0, 64, 0], [2, 2, 2], block.set(_x, _y, _z, 'stone'));
```

Inside the body of `scan` and `volume`, `_x`, `_y` and `_z` are the coordinates of the current point
and `_` is the block there. Those names are set for the duration of the loop and restored
afterwards, so a `scan` inside a `scan` behaves.

Positions and radii follow the convention of the whole game layer: a list `[x, y, z]` or three loose
arguments, interchangeably.

**These loops are the expensive part of most scripts.** The work is the product of the three
dimensions, so a radius of 32 is 274,625 blocks and a radius of 16 is 35,937 — an eighth of it.
Narrow the volume before walking it, and for anything large use a task
([9. Concurrency](../guide/09-concurrency.md)).

---

### `scan.scan(centre, radii, expression)`
Walks a box centred on `centre`, extending `radii` blocks in each direction, and evaluates the
expression at every point. The box therefore has sides of `2r + 1`.

Returns **how many times the expression came out true**, so counting the blocks that satisfy a
condition is a single line with no accumulator.

### `scan.scan(centre, radii_down, radii_up, expression)`
The same, with two separate sets of radii: how far to go in the negative direction and how far in
the positive. This is how an asymmetric volume is written — a wide, shallow slab, or a column that
goes further down than up.

```
found = scan.scan(centre, [8, 4, 8], block.block(_) == 'diamond_ore');

// wide and shallow: 16 out, 1 down, 3 up
scan.scan(centre, [16, 1, 16], [16, 3, 16], block.set(_x, _y, _z, 'air'));
```

### `scan.volume(from, to, expression)`
Like `scan`, but the volume is given as two opposite corners rather than a centre and radii. The
corners may be given in any order — they are normalised — and both are **inclusive**.

Also returns how many times the expression was true.

```
scan.volume([0, 60, 0], [15, 70, 15], block.set(_x, _y, _z, 'stone'));
```

### `scan.rect(centre, radii?, skip?)`
The **positions** of a box, as a lazy list, instead of a loop that evaluates something. Being lazy,
the volume is never held in memory: the elements are produced as they are consumed, which is what
makes it usable with `list.filter` and `list.find` over a large area.

`radii` defaults to 0 (the centre alone). `skip` gives an inner box to leave out, so that
`scan.rect(pos, 5, 4)` is the hollow shell between radius 4 and radius 5 — the way to check the
border of an area without rechecking its inside.

### `scan.diamond(centre, radius?, height?)`
The positions of an octahedron — every block within `radius` in Manhattan distance from the centre —
as a lazy list. With a `height` it becomes a prism instead: the diamond in the horizontal plane,
extended `height` blocks up and down. With no radius it is the centre alone.

This is the shape of "how far away in walking distance", which is why it fits searches for a nearby
block better than a box does.

```
ore = list.find(scan.rect(pos, 8), block.block(_) == 'diamond_ore');
list.map(scan.diamond(pos, 3), block.block(_));
```

### `scan.neighbors(pos)`
The six positions adjacent to `pos` — above, below, north, south, east, west, in that order — as
block values. Diagonals are not included.

```
list.all(scan.neighbors(pos), block.air(_));     // is it floating in empty space?
```
