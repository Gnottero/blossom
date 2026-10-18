# `core.mc.event`

Reacting to what happens in the world, and making something happen between different apps.

```
import "core.mc.event" as event;

fn on_block_broken(player, block) -> (
    print(player, 'you broke ' + block);
    false           // true = the breaking is cancelled
);

event.on('player_breaks_block', on_block_broken);
```

## How registration works

The function is passed **as a value**, not by name in a string: that way it stays bound to the module
that defined it and keeps resolving even when the caller is the mod, which knows nothing about that
module.

Four things are decided at registration time, not three hours later in a log file:

- **the name.** The catalogue is closed. `player_break_block` (without the `s`) is a load error, with
  the closest name as a suggestion.
- **the arity.** A handler whose signature does not match `event arity + fixed arguments` is a load
  error. `event.describe(name)` gives you the arity without opening this page.
- **the app.** Handlers belong to the app that registered them and disappear when it is reloaded or
  unloaded. That is why registration goes in the **body of a module** (the part that runs on load)
  and not inside another handler, which ends with ten listeners where one was wanted.
- **the source.** The handler is called with the command source of whatever raised the event: the
  player for a player event, the level for a world event, the server for a lifecycle event. That is
  what world-sensitive functions inside your handler will see.

## Cancelling

A handler that returns a **true** value cancels the action, if the event is cancellable. No magic
string needed: the returned value is already an answer.

```
fn no_obsidian(player, block) -> block == 'obsidian';
event.on('player_breaks_block', no_obsidian);
```

Hence the habit of ending a handler with an explicit `false` when it must **not** cancel: the value
of the last expression of the body is the return value, and a `print` that gives back its message is
a true value. A handler that ends in a `print` and no `false` cancels the action by accident.

Every game event is cancellable except fourteen: the life of the server and of the apps
(`server_starts`, `server_shuts_down`, `app_reloading`, `app_reloaded`, `app_unloading`), the three ticks, the two ends
of a session (`player_connects`, `player_disconnects`), `player_dies` and the three chunk events. The "Cancellable" column
of the catalogue is the authority, and `event.describe(name)` answers the same question from inside a
script.

For the events that fire **after** the game has acted, cancelling stops what is left rather than
undoing what is done, and each entry says exactly what that is: `player_places_block` puts the block
back, `explosion_outcome` saves the blocks but not the entities, `player_interacts_with_block` stops
the advancement and the arm swing but does not close the chest again.

Where even that would have to be invented rather than stopped, the event stays not cancellable and
points at the one that is asked earlier: `player_dies` at `player_takes_damage`, the two ends of a
session at the door they came through.

This makes that explicit `false` matter more than it used to: a handler on `statistic` or
`player_picks_up_item` that ends in a `print` now stops the statistic and the pickup, where before it
was merely untidy.

Two more properties of cancellation:

- **a cancel ends the round.** Handlers are asked in order of `event_priority` (declared in the
  manifest, highest first, `0` by default; among equals, in the order they registered), and the
  first one that cancels is the last one asked: the action will not happen, so the handlers after it
  do not hear about something that did not take place. Give the app that decides a higher priority
  than the ones that only observe.
- **a handler that throws does not cancel.** It reports the error and returns nothing, so the action
  goes through. If an event matters for safety, catch inside it.
- **a cancelled action is the game's action, not the event.** `player_breaks_block` cancelled means
  the block stays; the following events of that interaction simply never happen.

## Cost

Every event keeps its own list of handlers, and every firing site asks `isListened()` **before**
building the arguments, which is the expensive half. An event nobody listens to costs a field read.

That is also why the frequent events deserve care: `tick` fires twenty times a second per dimension,
`player_rides` on every input packet of a mounted player, `entity_loads` for every entity that
appears anywhere in the world. Whatever you put in them, you pay at that rate.

## Functions

### `event.on(event, function)` · `event.on(event, function, fixed_arguments)` · `event.on(event, function, fixed_arguments, player)`
Registers a handler and returns the event name.

`fixed_arguments` is a **list**, and its items arrive *after* the arguments of the event, so the
handler's arity is `event arity + length of the list`. Pass `null` to skip it.

With the fourth argument the handler only fires for that player, matched by scoreboard name. It is a
filter in Java, before the call: cheaper than the same `if` at the top of the handler.

```
event.on('player_jumps', count_jumps);
event.on('player_jumps', count_jumps_for, ['group-a']);   // fn(player, group)
event.on('player_jumps', only_for_her, null, 'ada');
```

### `event.off()` · `event.off(event)`
Removes every handler of this app, or only the ones it registered on that event. Reloading the app
does this for you.

### `event.list()`
The names of every event in the catalogue, application events included once somebody has declared
them.

### `event.listeners(event)`
How many handlers are registered on it, across all apps. `0` for an event nobody knows.

### `event.describe(event)`
The card of an event: `{'name' -> ..., 'arity' -> ..., 'cancellable' -> ..., 'listeners' -> ...}`.
Unknown name is a load error, like everywhere else.

### `event.declare(event, function, fixed_arguments...)`
Like `on`, but it also accepts a name the game does not know: that becomes an **application event**,
declared by the first app that listens to it, with the arity of the function that declared it. Here
the fixed arguments are passed loose, not in a list.

Passing `null` as the function removes this app's handlers for that event.

### `event.emit(event, player_or_null, arguments...)`
Raises an event, an application one normally. Returns how many handlers were listening, or `null`
when no such event exists, which is also the cheap way for the sender to skip the work entirely.

With a player, the handlers run with that player's source; with `null`, with the source of the
caller.

```
// the app that listens
event.declare('vein_exhausted', on_exhausted);

// the app that tells
heard = event.emit('vein_exhausted', null, pos, how_many);
```

It is the intended way to couple two apps without either knowing the functions of the other, and
without load order mattering.

### `event.schedule(delay_in_ticks, function, arguments...)`
Calls a function N ticks from now, on the server thread. Times are in ticks and not in milliseconds:
a script waiting twenty ticks waits the same amount of *game* time even when the server slows down.

Scheduled calls belong to the app and are dropped when it is reloaded, like handlers.

## The catalogue

Cancellable events are marked **yes**. A `block` argument is a block value carrying its position; a
`pos` is a `[x, y, z]` list; `hand` is `'mainhand'` or `'offhand'`.

### Server and apps

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `server_starts` | | |
| `server_shuts_down` | | |
| `app_reloading` | `app` | |
| `app_reloaded` | `app` | |
| `app_unloading` | `app` | |
| `tick` | | |
| `tick_nether` | | |
| `tick_ender` | | |

#### `server_starts()`
The server has finished starting. Autoloaded apps are loaded *before* this fires, on purpose: an app
that starts by itself must be able to hear the event that would otherwise already be over by the time
it arrives.

#### `server_shuts_down()`
The server is closing. It is the last useful moment to save; nothing after it is guaranteed to run.
It does **not** fire when the process is killed, so an app that only saves here loses everything on a
crash: save periodically as well.

#### `app_reloading(app)`
An app is about to be reloaded, and has not been torn down yet. `app` is its name.

This is the last moment at which the handlers of the outgoing version still exist, so it is where an
app saves what it wants to survive its own replacement:

```
fn save_before_reload(app) -> (
    if(app == 'my-app', io.write_json('data', 'my-app/state.json', state));
    false
);
event.on('app_reloading', save_before_reload);
```

It fires for **every** app being reloaded, not only for yours (that is what makes it useful to a
supervising app), so a handler that only cares about itself compares the name, as above.

A **first** load fires nothing: there is no previous version to speak of. Only a `load` of an app
that is already loaded is a reload. `/blossom script unload` does not fire it either: unloading is
not reloading, and it has its own event, `app_unloading`.

#### `app_reloaded(app)`
The app has been reloaded and the body of its modules has already run.

Because it fires after the new code has run, the **new** version can hear its own reload: it
registers the handler in its module body, and the handler is already in place when the event arrives
a moment later. That is the other half of the state hand-over:

```
fn restore_after_reload(app) -> (
    if(app == 'my-app', state = io.read_json('data', 'my-app/state.json'));
    false
);
event.on('app_reloaded', restore_after_reload);
```

Like `app_reloading`, it fires only for a real reload, and only when the load **succeeded**: an app
whose code throws on load is removed instead, and `app_reloaded` never comes. So the pair is either
`app_reloading` alone (the reload failed) or both in order (it worked), and a supervising app can
tell those two apart.

#### `app_unloading(app)`
An app is about to be unloaded with `/blossom script unload`, and its handlers still exist: the
moment to save what it wants to find again the next time it loads. It is the unload counterpart of
`app_reloading`, and between the two of them and `server_shuts_down` every way an app can stop
running is covered.

Like the other two it fires for every app, so a handler compares the name.

#### `tick()` · `tick_nether()` · `tick_ender()`
One server tick, twenty times a second. The three differ only in the source handed to the handler:
the overworld, the nether, the end. They are three events and not one with a dimension argument
because most apps want exactly one of them, and the other two should cost nothing.

`tick_nether` and `tick_ender` do not fire when that dimension does not exist on the server.

The two techniques that almost always come with `tick`:

```
let mut ticks = 0;
fn every_second() -> (
    ticks += 1;
    if(ticks % 20 == 0, periodic_work())
);
event.on('tick', every_second);
```

and deferring, to avoid doing everything in the same tick:

```
event.schedule(40, check_again, pos);       // in 40 ticks, that is 2 seconds
```

### Player: movement and state

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_jumps` | `player` | yes |
| `player_deploys_elytra` | `player` | yes |
| `player_wakes_up` | `player` | yes |
| `player_escapes_sleep` | `player` | yes |
| `player_rides` | `player, strafe, forward, jumping, sneaking` | yes |
| `player_starts_sneaking` | `player` | yes |
| `player_stops_sneaking` | `player` | yes |
| `player_starts_sprinting` | `player` | yes |
| `player_stops_sprinting` | `player` | yes |
| `player_swaps_hands` | `player` | yes |
| `player_swings_hand` | `player, hand` | yes |
| `player_switches_slot` | `player, from, to` | yes |

#### `player_jumps(player)` — cancellable
The player left the ground by jumping. It comes from the movement packet, so it is one event per
jump, not one per tick in the air. Cancelling means the jump does not happen on the server: the
client, which has already jumped, gets pulled back down.

#### `player_deploys_elytra(player)` — cancellable
The player started gliding. Cancelling keeps them on the ground — or falling, if they were.

#### `player_wakes_up(player)` — cancellable · `player_escapes_sleep(player)`
Two halves of the same action: `player_wakes_up` when the player was actually asleep in a bed,
`player_escapes_sleep` when they were not — the button was pressed while merely trying.

Cancelling `player_wakes_up` keeps the player in the bed. Cancelling `player_escapes_sleep` drops the
leave-bed action, and the honest version of that sentence is that today it changes nothing anybody
can see: in that branch the game does nothing at all, so there is nothing to stop.

#### `player_rides(player, strafe, forward, jumping, sneaking)` — cancellable
The movement inputs a mounted player is giving to their mount. `strafe` and `forward` are the two
numeric axes, the other two are booleans.

Cancelling hands the mount an input that asks for nothing, so it does not move; sneaking and
sprinting are left alone, because those belong to the rider and not to the mount.

It fires on every input packet of a mounted player — several times a second, per rider. Read it, do
not print from it.

#### `player_starts_sneaking(player)` · `player_stops_sneaking(player)` · `player_starts_sprinting(player)` · `player_stops_sprinting(player)` — cancellable
Edges, not states: one event per transition. Cancelling one leaves the player in the state they were
in, so cancelling `player_stops_sprinting` is how a sprint is made to continue.

#### `player_swaps_hands(player)` — cancellable
The player pressed the swap-hands key. Cancelling leaves the two hands as they were.

#### `player_swings_hand(player, hand)` — cancellable
Cancelling hides the swing from everybody else; it does not stop the hit, which is a different packet
(`player_attacks_entity`).

The player's arm swung — an animation packet, which arrives for hitting air as well as for hitting
something. It is not a reliable "the player attacked" signal; for that there is
`player_attacks_entity`.

#### `player_switches_slot(player, from, to)` — cancellable
The selected hotbar slot changed, from index to index. Cancelling keeps the old slot, and the client
is told to put its own selection back: the two sides do not drift apart.

### Player: blocks and items

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_uses_item` | `player, item, hand` | yes |
| `player_clicks_block` | `player, block, facing` | yes |
| `player_right_clicks_block` | `player, item, hand, block, face, hitvec` | yes |
| `player_interacts_with_block` | `player, hand, block, face, hitvec` | yes |
| `player_placing_block` | `player, item, hand, block` | yes |
| `player_places_block` | `player, item, hand, block` | yes |
| `player_breaks_block` | `player, block` | yes |
| `player_releases_item` | `player, item, hand` | yes |
| `player_finishes_using_item` | `player, item, hand` | yes |
| `player_picks_up_item` | `player, item` | yes |
| `player_drops_item` | `player` | yes |
| `player_drops_stack` | `player` | yes |
| `player_chooses_recipe` | `player, recipe, full_stack` | yes |
| `player_clicks_item_on_item` | `player, carried, other, slot, action` | yes |

#### `player_uses_item(player, item, hand)` — cancellable
Right click with no block under the cursor: eating, drawing a bow, throwing an ender pearl. `item` is
a copy of the stack as it was **before** the use. Cancelling stops the use.

#### `player_clicks_block(player, block, facing)` — cancellable
A **left** click on a block — the beginning of breaking it, or the whole of it in creative. `facing`
is the name of the face that was hit. Cancelling stops the breaking from starting.

#### `player_right_clicks_block(player, item, hand, block, face, hitvec)` — cancellable
A right click on a block, **before** the block itself decides what to do. `hitvec` is the hit point
relative to the block corner, three numbers in `[0, 1]`.

This is the one to cancel: it is early enough that neither the block nor the item in hand has acted
yet.

#### `player_interacts_with_block(player, hand, block, face, hitvec)` — cancellable
The same click, **after** the block answered — the chest opened, the lever flipped.

That answer cannot be taken back, so cancelling here does **not** close the chest again: what it
stops is the tail of the interaction, the advancement and the arm swing the other players see. To
stop the interaction itself, cancel `player_right_clicks_block`, which is asked before the block.

Note that `item` is not among the arguments here: the hand may well be empty by the time the block
has taken what was in it.

#### `player_placing_block(player, item, hand, block)` — cancellable
A block is about to be placed. `block` is the position it would occupy, still holding whatever was
there before. Cancelling leaves the world untouched and the item in the hand.

#### `player_places_block(player, item, hand, block)` — cancellable
It has been placed; `block` is now the new block.

Cancelling puts back whatever was in that spot before — air, grass, water — and stops everything the
placement still had to do: no sound, no advancement, and the item does not leave the hand. The
earlier `player_placing_block` is still the cheaper place to say no, since there the block is never
placed at all; this one is for the cases where the decision needs the block as it turned out.

#### `player_breaks_block(player, block)` — cancellable
The player is breaking a block. `block` still carries the state from **before** the break, which is
what you want in a handler: after it, there would be nothing to look at. Cancelling leaves the block
where it is.

#### `player_releases_item(player, item, hand)` — cancellable
The player let go of a right click they were holding — a bow released, a trident thrown.

#### `player_finishes_using_item(player, item, hand)` — cancellable
A held use reached its end on its own: the food was eaten, the potion drunk. Cancelling stops the
effect, so the food is not consumed.

#### `player_picks_up_item(player, item)` — cancellable
An item is about to enter the player's inventory. `item` is the whole stack being offered — the event
fires **before** the pickup, which is the only point at which it can still be refused; cancelling
leaves it on the ground exactly as a full inventory would.

#### `player_drops_item(player)` — cancellable · `player_drops_stack(player)` — cancellable
One item dropped, or a whole stack (the drop key with a modifier). They are two events because the
game sends two different actions; the dropped stack is not passed, since at this point it is still
the one in the hand — read it with [`core.mc.item`](core.mc.item.md) if you need it.

#### `player_chooses_recipe(player, recipe, full_stack)` — cancellable
The player clicked a recipe in the recipe book. `recipe` is the identifier as a string, `full_stack`
whether they asked for as many as possible.

#### `player_clicks_item_on_item(player, carried, other, slot, action)` — cancellable
An item held on the cursor was clicked onto another item in an open container: the gesture a bundle
answers to, here for every item there is.

`carried` is the stack on the cursor and `other` the stack in the slot that was clicked, both as they
are before anything happens; `slot` is the slot's number in the open container, the same numbering
`core.mc.screen` uses; `action` is `'left'` or `'right'`.

It fires only when **both** stacks are there. Picking an item up from a slot, or putting one down in
an empty one, is not an item clicked onto another item, and those clicks do not raise it.

Cancelling means the click was handled and nothing else happens: no swap, no merge, both stacks stay
as they were. That is the same answer a bundle gives when it swallows an item, which is what leaves
the field free to do the swallowing yourself:

```
import "core.mc.event" as event;
import "core.mc.text" as text;

// a stick refuses to be stacked onto by anything, with a word of explanation
fn on_click(player, carried, other, slot, action) -> (
    if(other:0 == 'stick',
        text.send(player, 'that stick wants nothing to do with your ' + carried:0);
        true,
        false
    )
);

event.on('player_clicks_item_on_item', on_click);
```

### Player: entities and damage

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_interacts_with_entity` | `player, entity, hand` | yes |
| `player_attacks_entity` | `player, entity` | yes |
| `player_collides_with_entity` | `player, entity` | yes |
| `player_trades` | `player, merchant, buy, buy_b, sell` | yes |
| `player_takes_damage` | `player, amount, source, source_entity` | yes |
| `player_deals_damage` | `player, amount, entity` | yes |
| `player_dies` | `player` | |
| `player_respawns` | `player` | yes |
| `player_changes_dimension` | `player, from_pos, to_pos, from_dimension, to_dimension` | yes |

#### `player_interacts_with_entity(player, entity, hand)` — cancellable
A right click on an entity: shearing a sheep, opening a villager's trades, putting a saddle on a pig.
Cancelling stops the interaction.

#### `player_attacks_entity(player, entity)` — cancellable
A left click on an entity, before the damage is worked out. Cancelling means no hit at all — no
damage, no knockback, no durability spent.

#### `player_collides_with_entity(player, entity)` — cancellable
The player's hitbox is overlapping another entity's. It fires while the overlap lasts, not once per
collision.

The push itself has already happened by then and cancelling does not undo it: what it stops is what
the touched entity was going to do — the item is not picked up, the experience orb is not absorbed,
the cactus does not sting.

#### `player_trades(player, merchant, buy, buy_b, sell)` — cancellable
Cancelling gives the player nothing and leaves the cost items in the trading slots: the villager is
not told and gains no experience, so as far as everybody is concerned the trade never happened.

A trade was taken from a villager or a wandering trader. `merchant` is the entity, or `null` when the
merchant is not an entity at all. `buy` and `buy_b` are the two cost stacks — `buy_b` is empty for a
one-item trade — and `sell` is what the player got.

#### `player_takes_damage(player, amount, source, source_entity)` — cancellable
`amount` is the damage before armour and resistances. `source` is the damage type's message id as a
string (`'fall'`, `'lava'`, `'mob'`), and `source_entity` the entity behind it, or `null` for the
world. Cancelling means the damage never lands.

#### `player_deals_damage(player, amount, entity)` — cancellable
The mirror of the above, from the attacker's side: this player is the source, `entity` the victim,
`amount` again the raw damage. Both fire for one player hitting another, in that order.

#### `player_dies(player)`
The player died. Not cancellable: the health is already at zero when this fires, so stopping the
death would mean also deciding how much life to hand back, and that is a decision for your script and
not for the shape of an event. To keep somebody alive, cancel `player_takes_damage`, which is asked
before the blow lands.

#### `player_respawns(player)` — cancellable
The player is respawning. It fires at the start of the respawn, so the position is still the old one;
read the new one a tick later with `event.schedule(1, ...)`.

Cancelling means they do not come back: they stay on the death screen and can ask again, so a handler
that always cancels leaves them stuck there. It is the same road back from the end, where cancelling
keeps the player in the end.

#### `player_changes_dimension(player, from_pos, to_pos, from_dimension, to_dimension)` — cancellable
A teleport that goes through the game's dimension machinery, fired **before** the move: `from_pos` is
where the player still is, `to_pos` the destination already chosen, and the dimensions are identifier
strings. The two dimensions are the same for a teleport that stays put.

Cancelling leaves the player exactly where they are — which is also how a portal is made one-way, or
a dimension closed to somebody.

### Player: session and chat

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_connects` | `player` | |
| `player_disconnects` | `player, reason` | |
| `player_message` | `player, message` | yes |
| `player_command` | `player, command` | yes |
| `statistic` | `player, category, event, value` | yes |

#### `player_connects(player)`
The player entered the world, with their data already loaded — so their position and inventory are
real, not defaults.

Not cancellable, like `player_disconnects`: a session is not an action of the game to be stopped.
Somebody who must not come in is turned away by whatever guards the door — the whitelist, the ban
list — not by an event that lets them in and then throws them back out.

#### `player_disconnects(player, reason)`
Not cancellable: the connection is already going, and the alternative would be to skip saving the
player's data.

The player left. `reason` is currently always `'disconnected'`: the kick or timeout message does not
reach this point, and passing a made-up one would be worse than passing a constant.

#### `player_message(player, message)` — cancellable
A chat message, as plain text, before it is broadcast. Cancelling means nobody sees it — which is how
a chat filter is written, together with a `text.send` back to the sender explaining why.

#### `player_command(player, command)` — cancellable
A command the player sent from chat, without the leading slash. It fires on the server thread, on the
body that runs the command and before it runs, so cancelling really does stop it — silently, with no
message: a handler that stops a command says why with a `text.send` of its own.

It is still not where permissions belong. Permissions belong to the command system, which knows about
levels and about the console; this is the place for an audit log, a rate limit, a temporary block.

#### `statistic(player, category, event, value)` — cancellable
Cancelling means the statistic is not counted at all: the total does not move, and neither do the
advancements that watch it.

A vanilla statistic moved. `category` is the stat type's path (`'custom'`, `'mined'`, `'killed'`,
…), `event` the thing counted, `value` the increment — not the total.

It is a chatty event: `custom`/`play_time` alone moves every tick for every online player.

### World

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `lightning` | `pos, skeleton_horse` | yes |
| `explosion` | `pos, power, source, attacker, block_interaction, create_fire` | yes |
| `explosion_outcome` | `pos, power, source, attacker, block_interaction, create_fire, blocks, entities` | yes |
| `block_dispenses` | `pos, item` | yes |
| `bell_rings` | `pos, direction, entity` | yes |
| `chunk_unloaded` | `x, z` | |
| `chunk_generated` | `x, z` | |
| `chunk_loaded` | `x, z, generated` | |
| `entity_loads` | `entity` | yes |
| `piston_extends` | `piston, facing, blocks` | yes |
| `piston_retracts` | `piston, facing, blocks` | yes |
| `block_forms` | `previous_block, new_block` | yes |

#### `lightning(pos, skeleton_horse)` — cancellable
A bolt is about to strike at `pos`. `skeleton_horse` is a **boolean**: whether this is one of the
bolts that summons a skeleton horse trap.

Cancelling stops the whole strike, the horse trap included — the two arrive together and it would
make no sense to keep half of it.

#### `explosion(pos, power, source, attacker, block_interaction, create_fire)` — cancellable
Before the blast goes off. `pos` is the centre, `power` its strength, `source` the exploding entity
(`null` for a bed or a respawn anchor), `attacker` whoever is blamed for it, `block_interaction` the
name of what it does to blocks (`'destroy'`, `'destroy_with_decay'`, `'keep'`, …), and `create_fire`
whether it sets fires.

Cancelling stops the explosion entirely — no blocks, no damage, no particles.

#### `explosion_outcome(pos, power, source, attacker, block_interaction, create_fire, blocks, entities)` — cancellable
The same six arguments, plus what the blast reached: `blocks` is the list of the blocks it is about to
destroy — filtered to those that would really go, so an empty list means the explosion moves nothing —
and `entities` the list of the entities it hit.

It sits between the two halves of a blast: the entities have already been hurt, the blocks have only
been chosen. So cancelling leaves every one of them standing and lights no fires — a blast that is
heard and felt but breaks nothing. To stop the whole thing, damage included, cancel `explosion`,
which is asked before any of it.

#### `block_dispenses(pos, item)` — cancellable
A dispenser at `pos` is about to fire `item`. Cancelling leaves the item in the dispenser and skips
the behaviour, which is how a dispenser is made to do something else instead.

#### `bell_rings(pos, direction, entity)` — cancellable
A bell was rung. `direction` is the side it was hit from, `entity` whoever rang it — `null` when it
was redstone rather than somebody. Cancelling means the bell does not ring at all.

#### `chunk_generated(x, z)` · `chunk_loaded(x, z, generated)` · `chunk_unloaded(x, z)`
The first fires when a chunk is born for the first time; the second every time a chunk enters memory,
with `generated` saying whether it was created now or read back from disk — so `chunk_generated` is
exactly `chunk_loaded` with `generated == true`, and listening to both means being called twice for
the same chunk.

`chunk_unloaded` fires when it leaves memory. None of the three is cancellable: a chunk that the game
has decided to load or drop is not something a script can argue with, and the closest thing to
"cancelling" an unload would leave the world holding a chunk it has already let go of.

The handlers run on the server thread even when the chunk was prepared on another one, so inside them
you can do everything you do in an ordinary event. What you must not do is heavy work: worldgen calls
this for every chunk of an exploring player.

#### `entity_loads(entity)` — cancellable
Cancelling **discards** the entity. There is no point earlier than this that covers all three ways an
entity appears, so the only "it must not be here" available is one that removes it the moment it
arrives: for a listener the difference is a tick of existence. It is ignored for players, which are
not something that can be discarded.

An entity appeared in the world: read back from disk with its chunk, generated by worldgen, or
summoned by somebody. It does not distinguish the three — this is "there is now an entity here", not
"an entity was born".

If only a few types matter to you, use `entity_load_handler` from
[`core.mc.entity`](core.mc.entity.md), which filters by type **before** calling you: listening for
zombies then costs nothing for every other entity that loads.

#### `piston_extends(piston, facing, blocks)` — cancellable · `piston_retracts(piston, facing, blocks)` — cancellable
A piston is about to move blocks. They arrive **before** anything moves; `piston` is the piston block,
`facing` the direction it points, and `blocks` the list of the blocks that would move. Cancelling
stops the movement.

`piston_retracts` fires only when the retraction would really pull something — a sticky piston, with
an attached and pullable block — because otherwise `blocks` would be a lie.

The list is built only when somebody is listening: in a piston machine this is many lists per second.

#### `block_forms(previous_block, new_block)` — cancellable
A block is forming from contact between fluids: concrete falling into or touched by water, obsidian
and cobblestone where water meets lava, basalt on soul soil.

The two arguments are the **same place**: how it was and how it is about to become. Passing both as
whole blocks saves the handler from having to remember that the second one means "there".

## Events of a single entity

These are not in this module: they are attached to one entity with `entity_event` in
[`core.mc.entity`](core.mc.entity.md). They live inside the entity, follow it between worlds, and
disappear with it — there is no cleanup to remember.

```
import "core.mc.entity" as entity;

entity.on(cow, 'on_death', on_cow_death);
```

| Kind | Arguments |
| --- | --- |
| `on_tick` | `entity` |
| `on_removed` | `entity` |
| `on_death` | `entity, cause` |
| `on_damage` | `entity, amount, source, source_entity` |
| `on_move` | `entity, motion, from, to` |

The first argument is always the entity itself.

## What not to put in a handler

- **long work**: the tick is waiting. The budget is 2 seconds and going over it is an error. If you
  really need it, use a `task` ([`core.task`](core.task.md)).
- **a `print` on a frequent event**: `tick`, `player_rides`, `statistic` and `entity_loads` fire
  constantly.
- **unhandled errors in a cancellable event**: a handler that blows up returns nothing, and the action
  is not cancelled. See the end of chapter 7 of the guide.
- **registering another handler**: it runs every time the event fires, and you end up with a listener
  per event instead of one.

---

## Coming from the old names

`on` and `declare` stay two functions and not one. `on` listens to an event of the game, and its
catalogue is closed on purpose: a misspelled name is a load error with a suggestion, instead of a
handler that never fires. `declare` brings an event of the application into being, which by
definition has no catalogue, and pairs with `emit`. Merging them would mean losing the check on the
names, which is the more useful of the two.

| Before | Now |
| --- | --- |
| `available()` | `list()` |
| `handle_event(name, f)` | `declare(name, f)` |
| `signal_event(name, source, ...)` | `emit(name, source, ...)` |
