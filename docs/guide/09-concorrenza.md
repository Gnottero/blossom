# 9. Concurrency

The server has one thread that runs the world, twenty times a second. Everything a script does
inside an event, it does **on that thread**, and while it does the world stands still. Everything
else in this chapter follows from that.

## When a task is needed

A `task` runs on a thread of its own: it does not hold the tick, and it has no deadline.

```
import "core.task" as task;

fn count_ores(centre) -> ...;                   // long work

t = task.start('count_ores', centre);           // starts immediately, does not block
task.done(t);                                   // has it finished?
task.wait(t);                                   // wait and take the result
```

You need it when you scan a large volume, generate something expensive, or wait for something. You
do **not** need it to go faster: it is concurrency, not automatic parallelism, and the coordination
costs.

| Function | What it does |
| --- | --- |
| `task.start(f, ...args)` | starts the work and returns a handle |
| `task.wait(t)` | waits for the end and returns the value |
| `task.done(t)` | whether it has finished |
| `task.value(t)` | the value produced so far, without waiting |
| `task.count()` | how many tasks are in flight |
| `task.dock(expr)` | evaluates `expr` on the main thread |
| `task.synchronize(lock, expr)` | runs `expr` while holding a named lock |
| `task.sleep(ms)` | suspends the current thread |
| `task.yield(v)` · `task.next(t)` · `task.has_next(t)` · `task.send(t, v)` | exchanging values with a running coroutine |

`wait` and `next` are two different kinds of waiting, and that is why they have two names: `wait`
waits for the work to finish and hands you its result, `next` takes the next value the coroutine has
produced with `yield`.

## Touching the world from inside a task

The world is not thread-safe. From a task, every call that reads or writes blocks and entities must
be wrapped in `task.dock`, which runs it on the main thread:

```
fn survey(centre) -> (
    found = 0;
    loop(1000, (
        p = point(centre, _);
        if(task.dock(block.block(p)) == 'diamond_ore', found += 1)
    ));
    found
);
```

`task.dock` costs: it queues up with the tick. The right shape is **one dock per unit of work**, not
one per individual read: read everything you need in a single dock, then process outside.

```
data = task.dock(list.map(positions, block.block(_)));      // a single stop
result = process(data);                                     // off the game thread
```

## Shared values

Individual operations on a shared list or map are already atomic: assignment, `+=`, `put`, `remove`,
`size`, iteration. What is not atomic is a **sequence**: `x = x + 1` reads and writes in two steps,
and between the two another task can slip in.

For sequences there is `synchronize`:

```
task.synchronize('register', (
    total = state:'total';
    state:'total' = total + 1
));
```

The lock is **reentrant** (a synchronised function may call another one on the same lock) and
**fair** (waiters enter in the order they arrived). The lock's name is any string: use one per
resource, not one for the whole app, or you have serialised the app.

## The watchdog

Every execution has a time budget and a maximum call depth:

| When | Time | Depth |
| --- | --- | --- |
| inside a game event | 2 s | 256 |
| from `/blossom script run`, or at load time | 15 s | 256 |
| inside a `task.start()` | no limit | 256 |

The task has no deadline **on purpose**: it runs on its own thread, holds nothing still, and is the
thing to use when a job really is long. The depth limit applies there too: an infinite recursion
fills a stack regardless.

The manifest can change them:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` removes them. The watchdog error cannot be caught with `try`.

## The pattern that almost always works

Long work that has to land in the world is written in three pieces:

```
fn start_work(centre) -> task.start('work', centre);

fn work(centre) -> (
    data = task.dock(collect(centre));      // 1. read, in a single stop
    plan = compute(data);                   // 2. think, off the tick
    task.dock(apply(plan))                  // 3. write, in a single stop
);
```

Read in bulk, think outside, write in bulk. The two stops are two busy ticks; everything else never
touches the server.
