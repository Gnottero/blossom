# `core.mc.shape`

Showing what a script is computing.

```
import "core.mc.shape" as shape;

shape.draw('box', 100, {'from' -> [0, 64, 0], 'to' -> [8, 70, 8], 'color' -> 'red'});
shape.draw('sphere', 100, {'center' -> [0, 66, 0], 'radius' -> 4, 'color' -> 'cyan'});
shape.draw('label', 100, {'pos' -> [0, 70, 0], 'text' -> 'here'});
```

## The deal with the client

The mod is **server-side**: it is installed on the server and that is it, and no function stops
working for whoever plays vanilla. Shapes, though, are the one thing a server cannot draw on its own
— there is no vanilla packet saying "draw a line here". So:

| Who is watching | What they see |
| --- | --- |
| has Blossom on the client too | the real shape, drawn with lines |
| plays vanilla | the same geometry approximated with **particles** |

Whoever writes the script does not have to know who has what: `draw` decides on its own, player
by player, and returns how many of them got it as a real shape. If you need to know beforehand there
is `has_client(player)`, in [`core.mc.entity`](core.mc.entity.md) — it is a question about a player,
not about the drawing.

The client announces itself: as soon as you join, the server sends a greeting; a vanilla client drops
it silently, a Blossom one answers. Nothing to configure.

**The limits of particles**, stated: the colour becomes the particle that looks most like it (vanilla
particles cannot be coloured at will), curves become polygons, and a label cannot be drawn at all —
whoever has no client gets the text in chat once. A block or an item becomes a particle showing its
texture at the right point.

### `shape.draw(type, duration, attributes)`

`duration` is in ticks. `attributes` is a map; the ones common to every shape:

| Attribute | What it does | Default |
| --- | --- | --- |
| `player` | who sees it: a player or a list | everyone in the dimension |
| `color` | a name or a number `0xRRGGBBAA`; alpha zero means "no wireframe" | white |
| `fill` | the fill colour; alpha zero means "do not fill", alpha `ff` covers what is behind | transparent |
| `line` | the thickness of the line | `2.0` |
| `follow` | an entity (or its id): the coordinates become relative to it | none |
| `snap` | which axes follow the entity, with `d` to round to the block | `'xyz'` |
| `debug` | the shape shows only with debug hitboxes turned on | `false` |

The colour names are `white`, `black`, `red`, `green`, `blue`, `yellow`, `cyan`, `magenta`, `orange`,
`grey`. They are not just a convenience: a colour hand-written in hexadecimal is the thing people get
wrong most often, and it almost always ends with the alpha channel at zero — that is, the shape is
invisible, and it looks like the function did not work.

`snap` takes a combination of the axes: `'xz'` follows the entity on the plane but leaves the height
written in the script, `'dxdydz'` follows it rounding to the block. It only applies with `follow`.

And the geometry, by type:

| Type | Attributes |
| --- | --- |
| `line` | `from`, `to` |
| `box` | `from`, `to` |
| `sphere` | `center`, `radius`, `level` |
| `cylinder` | `center`, `radius`, `height`, `axis` (`'x'`/`'y'`/`'z'`, default `'y'`), `level` |
| `label` | `pos`, `text` (or `value`), `size`, `align`, `indent`, `height`, `raise`, `tilt`, `lean`, `turn`, `facing`, `doublesided` |
| `block` | `pos`, `block`, `facing`, `tilt`, `lean`, `turn`, `scale`, `blocklight`, `skylight` |
| `item` | `pos`, `item`, `variant`, `facing`, `tilt`, `lean`, `turn`, `scale`, `blocklight`, `skylight` |

`level` is how many sides the circles have: `0` (the default) computes it from the radius. It applies
to the wireframe and to the fill together — a sphere with a high `level` has more meridians and
parallels, not just a smoother skin. A cylinder of height `0` is a disc — the way to draw a radius on
the ground without inventing a sixth shape.

Wireframe and fill turn on independently of each other, through the alpha of their colours.
`{'color' -> 0x00000000, 'fill' -> 0x2255ffff}` is a full, smooth shape with no edges;
`{'fill' -> 0x2255ff80}` is the same thing in transparency, with the default white wireframe on top.
A fill with alpha `ff` is really opaque: it hides the ground, the sky and the clouds behind it, and
the other half of the shape itself too. Intermediate alphas hide them in proportion: behind a
half-transparent fill, a cloud shows half. Whoever has no client still sees the outline in particles,
which takes the colour of the fill when the wireframe is off.

The attributes of the label: `size` is the character size (default `10`), `align` is `'center'`,
`'left'` or `'right'`, `indent`/`height`/`raise` move it in the three directions in font units,
`tilt`/`lean`/`turn` rotate it in degrees, `facing` is `'player'` (default), `'camera'` or a fixed
direction (`'north'`, `'south'`, `'east'`, `'west'`, `'up'`, `'down'`), `doublesided` makes it
readable from behind too. The `fill` of a label is the background of the text.

`text` also takes formatted text from [`core.mc.text`](core.mc.text.md), and in that case its colours
and formatting show in the world. Whoever has no client still gets the text in chat, with the same
style.

### Blocks and items in mid-air

`block` and `item` draw the real model of the game where there is nothing in the world: a ghost block
to show where a piece of a build will go, an item marking a point.

```
shape.draw('block', 100, {'pos' -> [0, 65, 0], 'block' -> 'chest[facing=north]'});
shape.draw('item', 100, {'pos' -> [0, 67, 0], 'item' -> 'diamond_sword', 'variant' -> 'ground'});
```

`block` takes the block as it is written (`'chest[facing=north]'`) or a block value taken from the
world — in the second case it carries its data along, so a chest drawn this way has its lid and a
sign its text. `item` takes the name of the item or an item with its NBT, and its data components
show: an enchanted weapon glints.

The attributes of the pose: `facing` is as for the label but the default is `'north'`,
`tilt`/`lean`/`turn` rotate in degrees, `scale` is a single number or one per axis (`[2, 1, 2]`),
`blocklight` and `skylight` go from `0` to `15` and force the lighting instead of taking the one of
the place. `variant` applies only to items and is the pose the game shows them in: `'none'` (the
default), `'gui'`, `'ground'`, `'fixed'`, `'head'`, and the four in-hand variants.

A block turns around its own centre, an item around the point it sits at. Whoever has no client sees
a `block_marker` particle (or `item`, for items) at the point: it is not the model, but it says at
least which block it is and where it is.

The attributes live in a map instead of being spread across the arguments, and **a name that shape
does not have is an error**, with the right name suggested, instead of a silent default.

There are seven types because they are the things needed to show what a script is computing: a path,
a volume, a radius, a column, a number, a block, an item. Arbitrary polygonal faces are deliberately
not among them: that is for making graphics, not for understanding what your own code is doing.

### `shape.types()`
The list of the shape type names this server knows, in the order the table above lists them. Useful
for a script that validates its own configuration before drawing.

```
shape.types();      // ['line', 'box', 'sphere', 'cylinder', 'label', 'block', 'item']
```

### `shape.clear()`
Forgets the shapes still waiting to be redrawn as particles. It does not remove the ones already sent
to a client, which expire on their own.

## Installing the client

The same jar. Put in the `mods/` folder of a client, it adds the drawing of shapes and nothing else:
no game logic lives on that side, and none should — a client that decided anything would be a client
the server has to trust.
