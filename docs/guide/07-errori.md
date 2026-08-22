# 7. Errors

An error in Blossom has a **category**, a **position** and a **stack that crosses modules**:

```
UndefinedSymbolError
  in math.vector.add  (src/math/vector.bs:4:12)
  called from main    (src/main.bs:8:1)

  Symbol 'ad' not found in module 'math.vector'.
  Did you mean: 'add'?
```

The suggestion appears when a name is close enough to one that exists, and only then: if you wrote
`qwertyuiop`, nobody pretends to know what you meant.

## The two families

The distinction that matters when writing code is not between categories, but between **who raises
them**.

| | Language errors | Thrown exceptions |
| --- | --- | --- |
| Raised by | the interpreter and the library functions | `throw(...)`, and the operations that can fail because of the world (json, nbt, files, an item or block that does not exist) |
| Examples | `ParseError`, `ArityError`, `TypeMismatchError`, `UndefinedSymbolError`, `IndexOutOfBoundsError`, `NullReferenceError`, `ImportError`, `CompatibilityError` | `user_exception`, `value_exception`, `unknown_item`, `io_exception`, `json_error`, `nbt_error`, `b64_error` |
| Does `try` catch them? | **no** | yes |
| What they mean | there is a bug in the script | something anticipated went wrong |

`try` does **not** catch an `ArityError` or a `TypeMismatchError`, and that is deliberate: those are
not situations to handle, they are wrong code that must land in the face of whoever wrote it, with a
position and a stack. Hiding them behind a generic `catch` is how a script survives for months doing
the wrong thing in silence.

Many language errors arrive before the script even runs: a module name that does not match the file,
a private symbol used from outside, a non-existent type in a signature, an event that does not
exist, a handler with the wrong arity. An app that loads has already passed those checks.

## `try`

```
try(expression);                                    // swallows user exceptions only
try(expression, fallback);                          // the same, with a fallback value
try(expression, 'filter', fallback);                // that type only
try(expression, 'f1', r1, 'f2', r2, ..., fallback); // several filters, in order
```

The fallback branch can be a **lambda**, and then the error has a name:

```
try(work(), 'exception', fn(e) -> print('could not do it: ' + e));
try(work(), 'exception', fn(e, tr) -> log(e, tr:'token'));   // the second one is _trace
```

This is the form to prefer: `_` is also the current element of every loop, so a `for` inside a
`catch` takes it away, and the exception value has to be saved by hand beforehand. With a name that
problem does not exist.

In the form without a lambda, `_` holds the thrown value:

```
import "core.json" as json;

fn read_configuration(text) -> try(
    json.decode(text),
    'json_error', (
        print('unreadable configuration: ' + _);
        {}                          // a default, and the app starts anyway
    )
);
```

Filters follow a **hierarchy**: `json_error` sits under `io_exception`, which sits under
`exception`. Filtering on `exception` catches everything catchable; filtering on an unrelated type
lets the exception through to the enclosing `try`.

| Filter | Catches |
| --- | --- |
| `exception` | everything catchable |
| `user_exception` | whatever `throw(value)` raises |
| `value_exception` | invalid values, and beneath it `unknown_item`, `unknown_block`, `unknown_biome`, `unknown_particle`, `unknown_poi`, `unknown_dimension`, `unknown_structure`, `unknown_criterion`, `unknown_screen` |
| `io_exception` | disk and formats, and beneath it `nbt_error`, `json_error`, `b64_error` |

Watch out for the short form: `try(code)` and `try(code, fallback)` without a filter catch **user
exceptions only**. A `json_error` passes straight through. If you want to catch everything, write
it: `try(code, 'exception', fallback)`.

## `throw`

```
throw();                                    // user_exception, null value
throw('message');                           // user_exception, with a value
throw('value_exception', 'not valid');      // of a specific type
```

The thrown value can be anything, not just a string: a map is how you get more than one datum to the
`catch`.

```
fn withdraw(account, amount) -> (
    if(amount > account:'balance',
        throw({'error' -> 'funds', 'short_by' -> amount - account:'balance'}));
    account:'balance' += -amount
);

try(withdraw(account, 100), 'short by ' + _:'short_by');
```

An exception type that does not exist is an immediate error, with a suggestion:

```
UndefinedSymbolError
  Unknown exception type: nope_exception.
```

## `_trace`

Inside a `catch` branch, alongside `_` there is `_trace`, a map with three keys:

| Key | What it holds |
| --- | --- |
| `stack` | a list of `[module, function, line, column]`, from the throw site downwards |
| `locals` | the local variables at the point where the exception started |
| `token` | `[text, line, column]` of the exact spot |

It is for diagnostic logging, not for logic:

```
try(work(), 'exception', (
    io.append_text('data', 'errors.txt', 'failed at ' + _trace:'token' + ': ' + _ + '\n');
    null
));
```

## The watchdog

An app that loops forever inside an event does not slow itself down: it **stops the tick**, and the
world with it. That is why every execution has a budget:

```
ExecutionBudgetError[time]
  in tower.watch  (src/tower.bs:12:5)

  Execution stopped after 2000 ms: the script is running too long and the server is waiting for
  it. If it really needs all that time, move it to a task.start() or raise 'timeout_ms' in the manifest.
```

| When | Time | Call depth |
| --- | --- | --- |
| inside a game event | 2 s | 256 |
| from `/blossom script run`, or at load time | 15 s | 256 |
| inside a `task.start()` | no limit | 256 |

They are changed in the manifest, and then they apply to all three cases:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` removes them: legitimate for an app you trust, but from that moment on an
infinite loop in it requires restarting the server.

The watchdog error **cannot be caught** with `try`: if it could, an infinite loop inside a `try`
would keep running.

## Errors inside an event handler

A handler that blows up takes down neither the app nor the other apps: the error is printed with its
stack and the event carries on.

Printed **where**: in the server log and in chat, to connected operators. A handler has nobody to
answer to (nobody typed a command, a piston moved), and for a long time the only place these errors
existed was the console, which players do not watch. The same repeated error appears in chat once
every five seconds, with a count of the ones suppressed in the meantime: a piston machine with a
broken handler fails many times a second, and that is not a good reason to make chat unreadable.
The log has all of them regardless.

The same applies to calls deferred with `schedule` and to screen callbacks. But "carries on" means
the action is **not** cancelled, because the return value never existed. If a cancellable event has
to fail safely, put the `try` there yourself and decide what to return:

```
fn on_bell(pos, direction, who) -> (
    try(
        build(pos),
        'exception', (
            reason = _;         // '_' changes meaning inside the for: save it first
            for(entity.player('all'), text.send(_, 'could not do it: ' + reason))
        )
    );
    false           // the sound goes ahead anyway
);
```

That `reason = _` is not pedantry: `_` is the same name `for`, `map` and `filter` use for the
current element. Inside a loop, the exception's value is gone.

Today the better answer is not to use `_` at all and to write the `catch` as a lambda, which already
has the name:

```
fn on_bell(pos, direction, who) -> (
    try(
        build(pos),
        'exception', fn(reason) -> for(entity.player('all'), text.send(_, 'could not do it: ' + reason))
    );
    false
);
```
