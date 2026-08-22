# 10. Performance

The real constraint is not "how fast is the language", it is **how long a tick lasts**: 50 ms, and
the whole rest of the game has to fit in there too. A script that takes 5 of them has already taken
a fifth of the server's budget.

## Orders of magnitude

Measured on an AMD Ryzen 5 5600H, OpenJDK 25 (the full numbers and the method are in
[`BENCHMARK.md`](../../BENCHMARK.md)):

| Operation | Cost |
| --- | --- |
| one iteration of an arithmetic loop | ~55 ns |
| one call to a user function (1 argument) | ~178 ns (about 3 iterations) |
| the same call with 4 arguments | ~332 ns |
| one indexed read from a list | ~80 ns |
| building a 2-key map | ~234 ns |

A call still costs **per argument**: roughly 65 ns each beyond the first. If a function called in a
tight loop takes six parameters, the parameter list is half its cost.

Translated: a 50 ms tick holds **about 900,000 arithmetic operations**. The language is not the
bottleneck of almost any script. What actually costs is a different list:

| What | Why it costs |
| --- | --- |
| reading or writing a block | it goes through the world, and the world is a real data structure |
| `entity.area` / `entity.select` with large radii | it walks entities |
| a `scan` over a large volume | it is the product of three dimensions: radius 32 is 274,625 blocks |
| a `task.dock` for every single read | every stop queues up with the tick |
| a `tick` handler that does everything every tick | twenty times a second, forever |

An order of magnitude between the cost of the language and the cost of the world means one thing:
**optimise world access, not arithmetic**.

## Measuring instead of guessing

To compare two implementations, `system.measure(expression)` runs it repeatedly for a fixed interval
and reports how many times it managed to: higher number, faster implementation.

For the real question, *what is eating the tick*, there is `/blossom profile`:

| Command | What it measures |
| --- | --- |
| `/blossom profile run <code>` | one expression, on the fly |
| `/blossom profile in <app> <code>` | some code inside a loaded app |
| `/blossom profile start <app>` | turns measurement on for the app: from then on its handlers count too |
| `/blossom profile stop <app>` | closes and prints the report |

The report has two tables: modules ordered by self time, and the fifteen most expensive functions
with **self time and total time**. The pair matters: total is the time spent inside the function,
including everything it calls; self is what is left after removing those. A function with a huge
total and a tiny self is not slow: it is merely on top of the stack, and optimising it changes
nothing.

On an event-driven app the pair to use is `start`/`stop`: `run` measures only what you type on the
command line, while the problem is usually a handler firing a hundred times a second.

## The fixes that pay

**Narrow the volume before walking it.** Halving the radius is an eighth of the work.

```
// no: 274,625 blocks
scan.scan(centre, [32, 32, 32], if(block.block(_) == 'diamond_ore', found += 1));

// yes: look where it makes sense to look
scan.volume([x-16, 0, z-16], [x+16, 16, z+16], ...);
```

**Read once, decide later.** Every `block.block(p)` is a world access: if you need the same point
three times, put it in a variable.

**Filter before calling.** `entity.on_load(['zombie'], f)` filters by type inside the mod; the
`entity_loads` event with an `if` inside the handler makes you pay one call for every entity that
appears in the world.

**Thin out instead of cutting down.** A check that has to be responsive, not instantaneous, does not
need every tick:

```
let mut t = 0;
fn on_tick() -> (t += 1; if(t % 20 == 0, check()));
```

**One `task.dock` per unit of work**, not one per read ([chapter 9](09-concorrenza.md)).

**Lazy lists stay lazy.** `list.range(1000000)` does not allocate a million values; you walk over it
with `map`/`filter` and never pay the memory. Materialising it with `list.slice(...)` pays for all
of it.

**Parameter types cost one comparison per annotated parameter.** On a module's public interface they
are a good trade; on a function called in a tight loop they are not.

## What the interpreter already does

Before being evaluated, the tree goes through a compilation pass: constants are folded, associative
operators flattened, pure subtrees resolved once. That is worth about **32%** on an arithmetic loop.
One visible side effect: `json.decode('{')` with a constant can fail during parsing rather than
inside your `try`.

Imports are resolved **once, at load time**: `math.sqrt` looks nothing up by name at run time, and
one extra module does not slow calls down.

Variable names are decided at parse time too: the compiled node already knows whether it is reading
a local, a global, an imported symbol or an implicit one (`_`, `_i`, `_a`, which are context fields
and not map entries). At run time no question about the name remains.

A call frame **is not a hash map**: up to sixteen locals are two parallel arrays with a linear scan,
and a function with no parameters allocates nothing. A value is not wrapped in any lazy object to be
passed or returned, and arguments are not copied on entering the frame. Together the two are worth
**-24%** and **-12%** on function calls, and bring the per-parameter cost from ~110 down to ~65 ns.

The watchdog costs about **5 ns per iteration** and **19 ns per call**. Turning it off to go faster
is a bad trade: you turn it off when it gets in the way, not to win those nanoseconds.
