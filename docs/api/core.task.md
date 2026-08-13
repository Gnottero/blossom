# `core.task`

Concurrency: tasks, waiting, and critical sections.

```
import "core.task" as task;

t = task(fn() -> long_computation());
task.task_join(t);      // waits and returns the result
```

### Starting and waiting

`task(function, ...arguments)` starts the execution on another thread and returns a handle.
`task_join(t)` waits for the end and returns the value, `task_await(t)` waits without returning it,
`task_completed(t)` says whether it has finished, `task_value(t)` reads the result when there is one.
`task_count()` says how many tasks are in flight.

### Communicating

`task_send(t, value)` sends a value to a task, `yield(value)` returns it from inside the task,
suspending its execution until the next request, `task_ready(t)` says whether a value is ready.

### Synchronisation

Every single operation on a shared value is already atomic: assigning, updating with `+=`, doing
`put`, `delete`, `length`, or iterating a shared list or map. What is not atomic is a *sequence* of
operations: `x = x + 1` reads and writes in two steps. For those there is

`synchronize(lock, expression)` — the lock is reentrant (a synchronised function can call another one
on the same lock) and fair (whoever waits gets in in the order they arrived, nobody is left out
indefinitely).

`task_dock(expression)` runs the expression on the main thread of the game: it is what you need to
touch objects of the world from inside a task. `sleep(milliseconds)` suspends.
