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

The function is passed **as a value**, not by name in a string: that way it stays bound to the module
that defined it and keeps resolving even when the caller is the mod, which knows nothing about that
module.

The number of arguments is checked at registration time: a handler with the wrong signature is a load
error, not a line in the log three hours later.

A handler returning a **true** value cancels the action, when the event is cancellable. No magic
string needed.

The handlers of an app disappear when the app is reloaded or unloaded.

## Functions

### `on(event, function)` · `on(event, function, fixed_arguments)` · `on(event, function, fixed_arguments, player)`
Registers a handler. The fixed arguments arrive after the ones of the event. With the fourth argument
the handler fires only for that player.

### `off()` · `off(event)`
Removes every handler of the app, or only the ones of an event.

### `available()` · `listeners(event)` · `describe(event)`
The names of the events, how many listen to an event, and its card: arity, cancellability, listeners.

### `handle_event(event, function, fixed_arguments...)`
Like `on`, but it also takes a name the game does not know: that becomes an **application event**,
declared by the first app that listens to it.

### `signal_event(event, player_or_null, arguments...)`
Raises an application event. It returns how many apps were listening, `null` when that event does not
exist. It is the way to make two apps talk without either knowing the functions of the other.

### `schedule(delay_in_ticks, function, arguments...)`
Calls a function N ticks from now. The times are in ticks and not in milliseconds: a script waiting
twenty ticks waits the same amount of *game* time even when the server slows down.

## The catalogue

The catalogue is closed: registering on a name that is not there is a load error, with the closest
name as a suggestion.

### Server

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `server_starts` | | |
| `server_shuts_down` | | |
| `tick` | | |
| `tick_nether` | | |
| `tick_ender` | | |

### Player: movement and state

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_jumps` | `player` | |
| `player_deploys_elytra` | `player` | |
| `player_wakes_up` | `player` | |
| `player_escapes_sleep` | `player` | |
| `player_rides` | `player, strafe, forward, jumping, sneaking` | |
| `player_starts_sneaking` | `player` | |
| `player_stops_sneaking` | `player` | |
| `player_starts_sprinting` | `player` | |
| `player_stops_sprinting` | `player` | |
| `player_swaps_hands` | `player` | yes |
| `player_swings_hand` | `player, hand` | |
| `player_switches_slot` | `player, from, to` | |

### Player: blocks and items

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_uses_item` | `player, item, hand` | yes |
| `player_clicks_block` | `player, block, facing` | yes |
| `player_right_clicks_block` | `player, item, hand, block, face, hitvec` | yes |
| `player_interacts_with_block` | `player, hand, block, face, hitvec` | |
| `player_placing_block` | `player, item, hand, block` | yes |
| `player_places_block` | `player, item, hand, block` | |
| `player_breaks_block` | `player, block` | yes |
| `player_releases_item` | `player, item, hand` | yes |
| `player_finishes_using_item` | `player, item, hand` | yes |
| `player_picks_up_item` | `player, item` | |
| `player_drops_item` | `player` | yes |
| `player_drops_stack` | `player` | yes |
| `player_chooses_recipe` | `player, recipe, full_stack` | yes |

### Player: entities and damage

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_interacts_with_entity` | `player, entity, hand` | yes |
| `player_attacks_entity` | `player, entity` | yes |
| `player_collides_with_entity` | `player, entity` | |
| `player_trades` | `player, merchant, buy, buy_b, sell` | |
| `player_takes_damage` | `player, amount, source, source_entity` | yes |
| `player_deals_damage` | `player, amount, entity` | yes |
| `player_dies` | `player` | |
| `player_respawns` | `player` | |
| `player_changes_dimension` | `player, from_pos, to_pos, from_dimension, to_dimension` | |

### Player: session and chat

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `player_connects` | `player` | |
| `player_disconnects` | `player, reason` | |
| `player_message` | `player, message` | yes |
| `player_command` | `player, command` | yes |
| `statistic` | `player, category, event, value` | |

### World

| Event | Arguments | Cancellable |
| --- | --- | --- |
| `lightning` | `pos, skeleton_horse` | |
| `explosion` | `pos, power, source, attacker, block_interaction, create_fire` | yes |
| `explosion_outcome` | `... , blocks, entities` | |
| `block_dispenses` | `pos, item` | yes |
| `bell_rings` | `pos, direction, entity` | yes |
| `chunk_unloaded` | `x, z` | |
| `chunk_generated` | `x, z` | |
| `chunk_loaded` | `x, z, generated` | |
| `entity_loads` | `entity` | |
| `piston_extends` | `piston, facing, blocks` | yes |
| `piston_retracts` | `piston, facing, blocks` | yes |
| `block_forms` | `previous_block, new_block` | yes |

`entity_loads` fires every time an entity appears in the world: read back from disk with its chunk,
generated by the world, or summoned by somebody. If only a few types matter to you, use
`entity_load_handler` from [`core.mc.entity`](core.mc.entity.md), which filters before calling you.

`chunk_generated` fires when a chunk is born for the first time; `chunk_loaded` every time a chunk
enters memory, with `generated` saying whether it was created now or read back from disk. Both
handlers are called on the server thread even when the chunk was prepared elsewhere, so inside them
you can do everything you do in an ordinary event.

`piston_extends` and `piston_retracts` arrive **before** the blocks move, and `blocks` is the list of
the ones that would move: cancelling stops the movement. `piston_retracts` fires only when the
retraction would really pull something — on a sticky piston, with a block attached and pullable —
otherwise the list would be a lie.

`block_forms` covers the formations by contact between fluids: concrete (falling into water or
touched by it), obsidian and cobblestone where water meets lava, basalt on soul soil. The two
arguments are the same place: how it was and how it is about to become.

## Events of a single entity

They are in [`core.mc.entity`](core.mc.entity.md), with `entity_event`: `on_tick`, `on_removed`,
`on_death`, `on_damage`, `on_move`. They live inside the entity and disappear with it.
