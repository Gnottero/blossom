# `core.task`

Concurrency: tasks, waiting, and critical sections.

```
import "core.task" as task;

t = task.start(fn() -> long_computation());
task.wait(t);      // waits and returns the result
```

### Starting and waiting

`start(function, ...arguments)` starts the execution on another thread and returns a handle.

`wait(t)` waits for the end and returns the value. `done(t)` says whether it has finished, without
waiting. `value(t)` reads the result when there is one. `count()` says how many tasks are in flight.

### Coroutines

A task that calls `yield(value)` hands a value back and suspends itself until somebody asks for the
next one. That is a different thing from waiting for the end, and it has its own pair:

`next(t)` takes the next value the task produced with `yield`, `has_next(t)` says whether one is
ready, and `send(t, value)` sends a value in the other direction.

The old names hid this: `task_join` and `task_await` read like two ways of waiting, and they are
not. One waits for the end of the work, the other steps a coroutine.

### Synchronisation

Every single operation on a shared value is already atomic: assigning, updating with `+=`, doing
`put`, `remove`, `size`, or iterating a shared list or map. What is not atomic is a *sequence* of
operations: `x = x + 1` reads and writes in two steps. For those there is

`synchronize(lock, expression)` — the lock is reentrant (a synchronised function can call another one
on the same lock) and fair (whoever waits gets in in the order they arrived, nobody is left out
indefinitely).

`dock(expression)` runs the expression on the main thread of the game: it is what you need to
touch objects of the world from inside a task. `sleep(milliseconds)` suspends.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `task(f, ...)` | `start(f, ...)` |
| `task_join(t)` | `wait(t)` |
| `task_await(t)` | `next(t)` — it steps a coroutine, it does not wait for the end |
| `task_ready(t)` | `has_next(t)` |
| `task_completed(t)` | `done(t)` |
| `task_value(t)` | `value(t)` |
| `task_count()` | `count()` |
| `task_dock(e)` | `dock(e)` |
| `task_send(t, v)` | `send(t, v)` |
