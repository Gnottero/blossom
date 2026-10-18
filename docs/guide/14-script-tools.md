# 14. The tools of `/blossom script`

Besides loading and running apps, `/blossom script` has a set of tools for working on a live
server: running an expression over an area, calling a function of an app by hand, looking inside an
app, and stopping everything when a script runs away.

Every tool exists in two forms. Written directly, `/blossom script scan ...`, it runs in a clean
interpreter, like `run`. Written after `in <app>`, `/blossom script in garden scan ...`, it runs
inside a loaded app and sees its functions, its imports and its state. `invoke`, `invokepoint`,
`invokearea` and `globals` only make sense inside an app, so they only exist in the second form.

## Over an area: `scan`, `fill`, `outline`

```
/blossom script scan <origin> <from> <to> <expression>
/blossom script fill <origin> <from> <to> "<expression>" <block> [replace <filter>]
/blossom script outline <origin> <from> <to> "<expression>" <block> [replace <filter>]
```

The expression is evaluated once for every block between `from` and `to`. Inside it `x`, `y` and `z`
are the coordinates **relative to `origin`**, and `_` is the block.

- **`scan`** counts the blocks for which the expression is true.
- **`fill`** sets `block` wherever it is true, optionally only where the old block matches `filter`.
- **`outline`** does the same, but only on the surface of the shape: where the expression is true
  and false for at least one neighbour.

```
/blossom script scan ~ ~ ~ ~-16 0 ~-16 ~16 ~ ~16 _ == 'diamond_ore'
/blossom script fill ~ ~ ~ ~-8 ~-8 ~-8 ~8 ~8 ~8 "x*x + y*y + z*z < 64" glass
/blossom script outline ~ ~ ~ ~-8 ~-8 ~-8 ~8 ~8 ~8 "x*x + y*y + z*z < 64" glass
```

The second line is a solid sphere of glass with a radius of 8, the third the same sphere hollow.
`scan` takes the expression as the rest of the line; `fill` and `outline` want it in quotes,
because the block comes after it.

The expression is compiled once and evaluated block after block, and **no event fires** while it
runs: a fill that woke up every app listening for placed blocks would be a command whose cost nobody
can predict. The volume is held to the game's own limit for `/fill`, the game rule
`max_block_modifications`.

Inside an app, the expression can call the app's own functions:

```
/blossom script in garden fill ~ ~ ~ ~-20 ~ ~-20 ~20 ~ ~20 "fertile(x, z)" grass_block
```

## Calling a function: `invoke`, `invokepoint`, `invokearea`

```
/blossom script in <app> invoke <function> [arguments...]
/blossom script in <app> invokepoint <function> <origin> [arguments...]
/blossom script in <app> invokearea <function> <from> <to> [arguments...]
```

Calls a function of the app's main module with arguments typed on the command line. The arguments
are separated by spaces, and each one is an expression of the app: `3`, `'oak'`, `[1, 2]` and a
binding of the module all work. A string with spaces goes in quotes, a list in brackets:
`1 'two words' [3, 4]` is three arguments.

`invokepoint` passes the three coordinates of `origin` first, as plain numbers, then the rest;
`invokearea` passes the six of `from` and `to`. The coordinates use the syntax of the game, so `~`
works.

```
/blossom script in garden invoke plant_row 10 'oak_sapling'
/blossom script in garden invokepoint plant ~ ~ ~ 'birch_sapling'
```

The second line calls `plant(x, y, z, 'birch_sapling')`. What the function returns is printed, and
it is the result of the command for a command block: a number as it is, anything else `1` or `0`.

## Looking inside: `globals`, `event`, `explain`

**`/blossom script in <app> globals`** lists the functions of the app's main module, with their
parameters and the file and line where they are defined, and then its module bindings with their
current value.

**`/blossom script event`** lists every handler of every event, with the app it belongs to.
`event add <event> <app> <function>` attaches a function of the app to an event, as `event.on`
would from the code; `event remove <event> <app> <function>` takes it off. Both are meant for
trying things out: the next reload of the app puts back what its code says.

**`/blossom script explain`** shows how the parser reads code, coloured by kind of token: numbers,
strings, variables, functions, operators. It is the way to find out why `a - b * c` does not do what
was meant, or where an unclosed bracket really ends.

```
/blossom script explain expression canonical 1 + 2 * 3 - 4
/blossom script in garden explain function plant
/blossom script in garden explain source clean
```

The first form explains an expression, the second one function of an app, the third the whole
source of the app. The style says at what stage the code is shown:

| Style | What it shows |
| --- | --- |
| `raw` | the tokens as they were written |
| `clean` | after the tokenizer's clean-up: comments and spare semicolons gone, `[...]` and `{...}` turned into the calls they stand for |
| `canonical` | rebuilt from the tree, with the brackets that the precedence implies |
| `optimized` | after the optimizer has folded what it could |
| `functional` · `functional_optimized` | every operator written as a function call |

`canonical` is the one to start from: it puts brackets where the parser does, also on the right of
an operator of the same precedence, so `a - (b - c)` and `a - b - c` read differently.

## When a script runs away: `stop`, `resume`

**`/blossom script stop`** stops every script on the server. Running code ends at its next check of
the watchdog, background tasks included; events and scheduled calls are held and wait. Nothing is
unloaded and nothing is lost: **`/blossom script resume`** lets everything run again.

It is the command for when something is flooding the server and it is not yet clear which app it
is. Once it is found, `/blossom script unload <app>` and `resume`.
