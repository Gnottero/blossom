# 8. Events

An app that does something on its own is an app that listens. The complete event catalogue is in
[`core.mc.event`](../api/core.mc.event.md); this chapter is about how to use it.

## Registering

```
module main;

import "core.mc.event" as event;
import "core.mc.text" as text;

fn on_break(player, block) -> (
    text.send(player, 'you broke ' + block);
    false
);

event.on('player_breaks_block', on_break);
```

Four things that line decides:

**The function is passed as a value**, not as a name in a string. That way it stays bound to the
module that defined it, and keeps resolving when the mod (which knows nothing about that module)
calls it.

**Arity is checked immediately.** A handler with the wrong signature is a load-time error, not a log
line three hours later. Look at the catalogue table for each event's arguments.

**The event name is checked immediately.** The catalogue is closed: `player_break_block` (without
the `s`) does not load, and it suggests the right name.

**Handlers die with the app.** `reload` and `unload` remove them before re-running the file, so two
copies of the same listener are never attached to the same event.

They are registered in the **module body**, which is the part executed at load time. Registering a
handler inside another handler is possible, but it almost always ends with ten listeners where one
was needed.

## Cancelling

A handler that returns a **truthy** value cancels the action, if the event is cancellable (the
"Cancellable" column of the catalogue). No magic string is required.

```
fn no_obsidian(player, block) -> block == 'obsidian';
event.on('player_breaks_block', no_obsidian);
```

Hence the habit of ending every handler with an explicit `false` when it must **not** cancel: the
value of the body's last expression becomes the return value, and a `text.send` that returns the
message is a truthy value. A handler ending in a `print` with no `false` cancels the action by
accident.

```
fn on_bell(pos, direction, who) -> (
    text.send(who, 'ding dong');
    false                           // without this line, the bell stops ringing
);
```

## Fixed arguments and per-player filtering

```
event.on('player_jumps', count_jumps, ['group-a']);        // appended to the arguments
event.on('player_jumps', only_for_them, null, player);     // fires only for that player
```

## One tick at a time

`tick` fires twenty times a second, and everything you put inside it is paid for twenty times a
second. The two techniques you almost always need:

```
import "core.mc.event" as event;

let mut current_tick = 0;

fn every_second() -> (
    current_tick += 1;
    if(current_tick % 20 == 0, periodic_work())
);

event.on('tick', every_second);
```

and deferral, so as not to do everything in the same tick:

```
event.schedule(40, recheck, pos);       // in 40 ticks, that is 2 seconds
```

Times are in **ticks**, not milliseconds: a script that waits twenty ticks waits the same amount of
*game* time even when the server slows down.

## Reloading an app without losing state

A `reload` throws everything away: module variables start again from zero. The two events that cover
the gap are `app_reloading`, which fires **before** the old version is torn down, and
`app_reloaded`, which fires **after** the new code has run.

```
module main;

import "core.mc.event" as event;
import "core.io" as io;

let mut state = {};
let FILE = 'my-app/state.json';

fn before(app) -> (
    if(app == 'my-app', io.write_json('data', FILE, state));
    false
);

fn after(app) -> (
    if(app == 'my-app', state = io.read_json('data', FILE));
    false
);

event.on('app_reloading', before);
event.on('app_reloaded', after);
```

Three things to know:

- **they arrive for every app**, not just yours: the name is the argument, and an app minding only
  itself compares it as above. That is what makes them useful to an app that watches over others.
- **the new version hears its own `app_reloaded`**, because the event fires after the module bodies
  have run: the handler is already registered when it arrives.
- **a first load generates neither**, and neither does an `unload`: that is not a reload. If the new
  code blows up while loading, `app_reloading` arrives and `app_reloaded` does not: the pair tells
  you how it went.

## Events of a single entity

They live inside the entity, follow it between worlds and vanish with it:

```
import "core.mc.entity" as entity;

entity.on(cow, 'on_death', on_death_fn);
```

The available events are `on_tick`, `on_removed`, `on_death`, `on_damage` and `on_move`. The first
argument passed to the handler is always the entity itself.

To react to the **appearance** of entities of certain types there is `entity.on_load(types, f)`,
which filters by type **before** calling you: listening for zombies does not cost one call for every
entity that appears in the world.

## Making two apps talk

An app does not call another app's functions: it declares an event and fires it.

```
// the app that listens
event.declare('mine_exhausted', on_exhausted);

// the app that reports
count = event.emit('mine_exhausted', null, pos, how_many);
```

`declare` also accepts a name the game does not know: that becomes an **application event**, declared
by the first app that listens for it. `emit` returns how many apps heard it, or `null` when that
event does not exist, that is, when nobody is listening, in which case the sender can skip the
work.

It is the intended way to couple two apps without either one having to know the other's functions,
and without load order mattering.

## What not to put in a handler

- **long work**: the tick is waiting. The budget is 2 seconds and exceeding it is an error. If you
  genuinely need it, use a `task` ([chapter 9](09-concurrency.md)).
- **a `print` for every frequent event**: `player_rides` and `tick` fire constantly.
- **unhandled errors in a cancellable event**: if the handler blows up it returns nothing, and the
  action is not cancelled. See the end of [chapter 7](07-errors.md).
