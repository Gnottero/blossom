# `core.task`

Concurrency: running work off the server thread, waiting for it, coordinating with it, and getting
back onto the server thread when the world has to be touched.

```
import "core.task" as task;

t = task.start(fn() -> long_computation());
task.wait(t);      // waits and returns the result
```

The server has one thread that runs the world twenty times a second. Everything a script does inside
an event happens on that thread, and while it happens the world stands still. A task exists to move
work off it. It is **concurrency, not parallelism**: starting five tasks does not make a computation
five times faster, and the coordination has a cost of its own. See
[9. Concurrency](../guide/09-concurrency.md) for the patterns.

---

## Starting and waiting

### `task.start(function, ...arguments)`
Starts `function` on a thread of its own, with the given arguments, and returns a **handle** — a
value of its own type that identifies the task. The function may be passed by value or by name.
Starting does not block.

A task has **no time limit**: the watchdog's timeout does not apply to it, precisely because it
holds nothing still. The recursion depth limit does still apply.

### `task.start_in(pool, function, ...arguments)`
Like `task.start`, but the task belongs to a **pool**, any value used as a name (`'loaders'`), which
`task.count(pool)` can count separately from the others. Tasks of the same pool still run side by
side: a pool groups them, it does not queue them.

### `task.wait(t)`
Blocks until the task has finished and returns its value. If the task threw, the exception surfaces
here.

### `task.done(t)`
Whether the task has finished, without waiting. The way to poll from a `tick` handler without
freezing the server.

### `task.value(t)`
The value the task has produced so far, without waiting for it to finish. A task that failed has no
value and answers `null`: the exception belongs to whoever waits with `task.wait`.

### `task.count()` · `task.count(pool)`
How many tasks are currently in flight, all of them or those of one pool.

```
t = task.start('count_ores', centre);

// in an event handler: never wait, just look
if(task.done(t), report(task.value(t)));

// off the tick, waiting is fine
total = task.wait(t);
```

---

## Getting back on the game thread

### `task.dock(expression)`
Evaluates the expression on the **main thread of the game** and returns its value. The world is not
thread-safe: every call that reads or writes blocks, entities or any other game object must go
through here when it is made from inside a task.

A dock queues up with the tick, so it costs. The right shape is **one dock per unit of work**, not
one per individual read:

```
// no: a thousand stops
loop(1000, if(task.dock(block.block(p(_))) == 'diamond_ore', found += 1));

// yes: one stop, then think off the thread
data = task.dock(list.map(positions, block.block(_)));
result = process(data);
```

---

## Coordinating

### `task.synchronize(lock, expression)` · `task.synchronize(expression)`
Evaluates the expression while holding a named lock, so that two tasks cannot be inside the same
critical section at once. The lock name is any value; with one argument, a single shared lock is
used.

Every **single** operation on a shared list or map is already atomic: an assignment, a `+=`, a
`put`, a `remove`, a `size`, an iteration. What is not atomic is a **sequence** — `x = x + 1` reads
and writes in two steps, and another task can slip in between them. That is what this is for.

The lock is **reentrant** (a synchronised function may call another one holding the same lock) and
**fair** (waiters enter in the order they arrived, so nobody is starved). Use one lock per resource:
a single lock for the whole app serialises the whole app.

```
task.synchronize('register', (
    total = state:'total';
    state:'total' = total + 1
));
```

### `task.sleep(ms)` · `task.sleep(ms, on_interrupt)`
Suspends the current thread for that many milliseconds and returns the amount slept. If the thread
is interrupted — which is what happens when the app is unloaded — execution stops, returning the
optional second argument. Called on the server thread it stalls the world, so it belongs inside a
task.

---

## Coroutines

A task that calls `task.yield(value)` hands a value back and suspends itself until somebody asks for
the next one. That is a different thing from waiting for the end of the work, and it has its own
functions.

### `task.yield(value)` · `task.yield(value, wait)`
Called **from inside a task**: publishes a value to whoever is consuming the task and suspends until
the value is taken. With a truthy second argument it also waits for a value sent back with
`task.send`. Outside a task it is an error.

### `task.next(t)`
Takes the next value the task produced with `yield`, waiting if it has not produced one yet.
Returns `null` when the task is finished and there is nothing more coming. The task must be a
coroutine — one that actually yields — otherwise it is an error.

### `task.has_next(t)`
Whether a yielded value is available right now, without waiting.

### `task.send(t, value)`
Sends a value into a coroutine, which receives it as the result of its `yield`.

```
fn producer() -> loop(3, task.yield(_ * 10));

t = task.start('producer');
task.next(t);       // 0
task.next(t);       // 10
task.next(t);       // 20
task.next(t);       // null - finished
```

`wait` and `next` are two kinds of waiting with two names on purpose: `wait` waits for the work to
end and gives you its result, `next` steps a coroutine. The old names, `task_join` and `task_await`,
read like two ways of doing the same thing.

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
| `sleep(ms)` · `yield(v)` | `task.sleep(ms)` · `task.yield(v)` — they are in the module, not the prelude |
