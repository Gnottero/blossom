# `core.system`

Time, type introspection, copying, and measurement.

```
import "core.system" as system;

system.time();              // high-resolution milliseconds, to measure durations
system.type([1, 2]);        // 'list'
```

---

## Time

### `system.time()`
A high-resolution counter in milliseconds, with a decimal part. It is **monotonic** and has no
relation to the wall clock: the only thing it is good for is subtracting two readings to find out
how long something took.

### `system.unix_time()`
Absolute time, in milliseconds since 1 January 1970. This is the one to store in a file or compare
against a date.

### `system.convert_date(timestamp)` · `system.convert_date(parts)`
Converts in both directions, deciding by the shape of its argument.

Given a **number**, it breaks a unix timestamp into a list of nine elements:

| Index | Element |
| --- | --- |
| 0 | year |
| 1 | month, 1–12 |
| 2 | day of the month |
| 3 | hour, 0–23 |
| 4 | minute |
| 5 | second |
| 6 | day of the week, 1 = Monday … 7 = Sunday |
| 7 | day of the year |
| 8 | week of the year |

Given a **list** (or three or six loose arguments), it goes the other way and returns a timestamp:
three elements are year, month and day; six add hour, minute and second. Any other count is an
`ArityError`.

```
now = system.unix_time();
parts = system.convert_date(now);
parts:0;                                        // the year

system.convert_date([2026, 8, 21]);             // the timestamp of that midnight
system.convert_date([2026, 8, 21, 14, 30, 0]);  // with a time of day
```

---

## Introspection

### `system.type(value)`
The name of the value's type, as a string: `number`, `string`, `bool`, `list`, `map`, `null`,
`function`, plus the game types `entity`, `block`, `text`, `nbt`, `screen`.

It is also the way to make equality strict, since `'1' == 1` is `true` in Blossom:

```
fn identical(a, b) -> system.type(a) == system.type(b) && a == b;
```

### `system.bool(value)`
The truth value of a value, as a `bool`, following the rules in
[2. Values](../guide/02-valori.md), with one addition: the strings `'false'` and `'null'` are
false, whereas any other non-empty string is true. That exception exists so that a flag read from a
file or a command argument means what it says.

```
system.bool('0');           // true    - a non-empty string
system.bool('false');       // false   - the exception
system.bool([]);            // false
```

### `system.copy(value)`
A deep copy: nested lists and maps are copied too, so nothing in the result shares a reference with
the original. This is what to use before handing a list to a function that may modify it.

```
a = [[1, 2]];
b = system.copy(a);
list.put(b:0, 0, 9);
a;                          // [[1, 2]] - untouched
```

### `system.var(name)` · `system.vars(prefix)` · `system.undef(name)`
Reach variables by name at run time: `var` reads one, `vars` lists the names starting with a prefix,
`undef` removes a variable or a function (with a trailing `*` in the name, everything with that
prefix).

They are debugging tools. In ordinary code a direct access is clearer, faster, and checked at load
time, while a name built at run time is none of those things.

---

## Measurement

### `system.measure(expression)`
Runs the expression repeatedly for a fixed interval of 50 ms and returns how many times it managed
to. A higher number means a faster expression. It is the quick way to compare two implementations
without setting up a benchmark; the expression is re-evaluated, so it must be one that can safely
run many times.

```
system.measure(slow_version(data));     // 812
system.measure(fast_version(data));     // 3106
```

It answers "how long does this piece take", though, not "who is eating the tick". For the second
question there is the `/blossom profile` command, which measures function by function:

| Command | What it measures |
| --- | --- |
| `/blossom profile run <code>` | an expression on the fly, in a clean interpreter |
| `/blossom profile in <app> <code>` | code executed inside a loaded app |
| `/blossom profile start <app>` | turns measurement on for the app: from there it also counts what the app does on its own, that is, its event handlers |
| `/blossom profile stop <app>` | closes the measurement and prints the report |

The report has two tables. First the modules, ordered by self time; then the fifteen most expensive
functions, with **self time and total time**. The pair matters: the total is the time spent inside
the function, including the ones it calls; the self time is what is left after taking those away. A
function with a huge total and a tiny self time is not slow: it is just the one at the top of the
stack, and optimising it changes nothing.

`start`/`stop` is the pair that really matters on an event-driven app: `run` only measures what you
type on the command line, while the problem is usually a handler firing a hundred times a second.

Orders of magnitude for the language itself are in
[10. Performance](../guide/10-prestazioni.md) and in [`BENCHMARK.md`](../../BENCHMARK.md).

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `profile_expr(e)` | `measure(e)` |
| `system_variable_get(k)` · `system_variable_set(k, v)` | gone: they were a space shared by every app on the server, which is the implicit global the language does without. Talk to another app with an app event, keep data with [`core.io`](core.io.md) |
