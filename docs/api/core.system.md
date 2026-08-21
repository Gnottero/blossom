# `core.system`

Time, introspection and measurement.

```
import "core.system" as system;

system.time();              // high resolution milliseconds, to measure durations
system.type([1, 2]);        // 'list'
```

### Time

`time()` returns a monotonic counter in milliseconds with a decimal part — it is the one to use to
measure how long something takes. `unix_time()` returns absolute time in milliseconds.
`convert_date(timestamp)` breaks it into `[year, month, day, hour, minute, second, weekday,
day_of_year]`, and the same function does the opposite starting from a list.

### Introspection

`type(value)` returns the name of the type. `var(name)` reads a variable by name and `vars(prefix)`
lists the ones starting with a prefix — useful for debugging, not for normal logic, where a direct
access is clearer and faster.

### Measurement

`measure(expression)` runs the expression repeatedly for a fixed interval and returns how many
times it managed to: it is the quick way to compare two implementations without setting up a
benchmark.

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
function with a huge total and a tiny self time is not slow — it is just the one at the top of the
stack, and optimising it changes nothing.

`start`/`stop` is the pair that really matters on an event-driven app: `run` only measures what you
type on the command line, while the problem is usually a handler firing a hundred times a second.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `profile_expr(e)` | `measure(e)` |
| `system_variable_get(k)` · `system_variable_set(k, v)` | gone — they were a space shared by every app on the server, which is the implicit global the language does without. Talk to another app with an app event, keep data with [`core.io`](core.io.md) |
