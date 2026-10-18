# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 1.1.0 — unreleased

Minecraft **26.4-snapshot-4** with Fabric loader **0.19.5**, and parity with **Scarpet 26.4-beta-3**:
what Scarpet has added since is here too, and the bugs it fixed are fixed here too, plus a few that
Scarpet still has. `api_level` goes up to **2.1**: an app that uses `core.mc.command` or the new
manifest keys declares it, the others load as before.

The test suites Scarpet has added (language, world, entities, inventories) were translated to
Blossom and run on the core and on a development server: 410 cases out of 474 pass. The rest need a
connected player, or test conventions Blossom changed on purpose (an event handler with the wrong
arity is an error at registration, not a `false`).

### App commands

- **`core.mc.command`** brings Scarpet's custom commands: every app has its own `/<app> ...`.
  `command.register('plant <pos> <block>', plant)` adds a path, with the function passed as a
  **value** as for events; `command.argument(name, spec)` declares a type, `command.permission(who)`
  who can use it, `command.allow_conflicts()` lets through two paths that take different arguments at
  the same point. `command.list()` and `command.types()` say what is there.
- **All of Scarpet's argument types** are there, and the type of `<name>` is found from the name as
  it is there: `<from_pos>` is a `pos`, `<count_int>` an `int`, a name that matches nothing a
  `string`. A type or a modifier that does not exist stops the load with the right name suggested,
  instead of becoming an `int` without a minimum.
- **Errors arrive at load time, not when someone types the command**: the number of parameters of
  the function is checked against the path at `register`, conflicts between paths and a name already
  taken by another command at the end of the load.
- The command lives as long as the app: unloading takes it away, reloading puts back the new one, and
  the game's `/reload`, which rebuilds the commands from scratch, finds it again.
- **Three Scarpet bugs** fixed while porting: a custom type based on `scoreholder` gave a list of
  players, one based on a game type lost its suggestions, and `biome` always read the argument called
  `biome` instead of its own.

The "Coming from Scarpet" table at the bottom of `docs/api/core.mc.command.md` translates
`__config()` line by line. `__command()` and public functions as subcommands are not there: every
path is declared.

### `/blossom script`

The tools Scarpet's `/script` had and Blossom did not:

- **`scan`**, **`fill`** and **`outline`**, also in the form `... in <app>`, to run an expression on
  every block of an area;
- **`invoke`**, **`invokepoint`** and **`invokearea`**, to call a function of an app from the command
  line, passing it a position or an area;
- **`globals`**, to look at the global variables of an app;
- **`event`**, to list, add and remove event handlers by hand;
- **`stop`** and **`resume`**: `stop` stops running code at its next watchdog check, tasks included,
  and holds events and scheduled calls until `resume`;
- **`explain`**, which shows how an expression was read, with the right brackets on the right of an
  operator of the same precedence.

### Manifest

The three keys of Scarpet's `__config()` the manifest did not have:

- **`"strict": true`**: a variable read before any assignment is an error that names it, instead of a
  `null` that travels on. The core already knew how to do it; there was no way to turn it on.
- **`"event_priority"`**: the order in which the apps hear the same event, highest number first.
  Without it, they are all `0`.
- **`"requires"`**: the mods the pack needs, with the version written as in `fabric.mod.json`
  (`{"minecraft": ">=26.4", "carpet": "*"}`), checked at load time.

### Events

- **`app_unloading(app)`**: Scarpet's `__on_close`, fired when an app is unloaded with
  `/blossom script unload`.
- **Breaking: a cancel ends the round.** Once a handler cancels, the action does not happen and the
  handlers after it are not called, as in Scarpet. The order is that of `event_priority`, and for the
  same priority the order of registration: the app that decides goes before those that only observe.
- An entity event with the wrong name suggests the right one: whoever comes from Scarpet and writes
  `on_damaged` is answered `on_damage`.

### Shapes

- **`seethrough`**: lines, fill and label text show through the blocks in front of them. It is for
  what has to be found rather than looked at: an ore behind a wall, a path underground.
- **`dim`**: the dimension the shape lives in, as a name, an entity or a block. It decides who sees it
  by default and where the client and the particles draw it.
- **`polygon`**, with `points`, `mode` (`'polygon'`, `'strip'`, `'triangles'`), `inner` and
  `relative`. It had been left out; parity with Scarpet brings it back.
- The placeholders of `label`, `block` and `item` really reach players without the client as a
  particle: a zero-length line used to send none.

### Other additions

- **`entity.query(player, 'input')`**: the movement keys the client reported as held down, as a map
  with `forward`, `backward`, `left`, `right`, `jump`, `sneak` and `sprint`.
- **`task.start_in(pool, fn, ...)`**: a task that belongs to a pool, which `task.count(pool)` counts
  on its own. It is the `task_thread` removed in 1.0.0 as unreachable: `task.count(pool)` still
  counted by pool, but nothing could put a task in one any more.

### Breaking changes

Besides the end of the round on a cancel, above:

- **`convert_date` works in UTC** instead of the server's time zone, and without the milliseconds of
  the clock. The same script gave different dates on two servers.
- **`f((x = 5), 2)` passes two positional arguments.** The brackets used to leave no trace in the
  tree, and `(x = 5)` was read as a named argument followed by a positional one, that is, an error.
  Now a `=` in brackets is an assignment, and the error message for the other case suggests it.

API 2.0 was never released, so these breaking changes stay inside MAJOR 2.

### Fixed

In the language, each with its own case in `UpstreamParityTest`:

- **`_`, `_i`, `_a` and `_trace` go back to their previous value** also when a `for`, `sort`, `try`
  or `scan` exits with an error, and the iterator rewinds. Before, after an error caught by `try`,
  the values of the interrupted loop stayed.
- **`0xfe+2` is a sum**, not a malformed hexadecimal number.
- **`for` counts the iteration that exits with `break(true)`.**
- **Numbers above 2^53**: `bitwise_not`, `bitwise_popcount` and the formats `%d`, `%o`, `%x` read a
  long and not a double, and no longer lose the last digits.
- `slice` of an empty list or string does not throw, `range` with a step that overflows a long stops
  before overflowing, and an index beyond 32 bits wraps around the list like the others instead of
  being truncated.
- `min` and `max` of an empty list give `null`.
- A chain of `!=` with two different values that have the same hash called them equal.
- **`0xRRGGBBAA` colours no longer go negative**: every colour with red above `0x7f` came out negative
  (`fog_color`, `sky_color`, team colours). Scarpet has the same bug.

In the game:

- **`set_biome`** writes per block, the way 26.4 now stores biomes: it wrote the wrong cells.
- **`destroy` with a tool** no longer creates an entity without a world, which crashed.
- **`world.chunk_tickets()`** threw `ClassCastException`: the cleanup before the port had kept the
  wrong version.
- `scan` respects the upper bound, and `rect` has `y` and `z` in their place.
- The `game_target` key of the system information reads "26.4 Snapshot 4" as `26.4.0`.
- `structure.eligibility` without the size works: in Scarpet 26.4-beta-3 it always answers no.
  Blossom did not take that regression.

### Safety and robustness

- **Scheduled calls ran without a watchdog**: an infinite loop in a `schedule` hung the server. Now
  they have the time limit like everything else.
- **The volume check of `scan` and `fill` multiplied in `int`**: three large sides overflowed into a
  small number, passed the limit and allocated a huge array. Now it is a `long`.
- `toLowerCase` without a `Locale`, which on a server with a Turkish locale broke names with an `i`.

### Port to 26.4

The APIs moved or renamed between 26.3-snapshot-3 and 26.4-snapshot-4: block sounds (the
`BlockSoundSet` registry), density functions and their sampler, `StructureStart.INVALID_START` gone,
the swing packet turned into punch, the renderer (`RenderPipeline`, `PoseStack.rotate`,
`LevelRenderer.render`) and biomes stored per block. The `RandomState` mixin is no longer needed and
is removed. The Mixin audit on server and client confirms every injection finds its target.

### Editor

The VS Code extension goes to **0.9.0**: completion, hover and handbook for `core.mc.command` and
`task.start_in`, `polygon`, `seethrough` and `dim` in the shapes handbook, and the manifest schema
knows `strict`, `event_priority` and `requires`, which it used to flag as unknown keys.

### Repository

- Everything is in English: the README, this changelog, the examples and the code comments.
- The examples are renamed: `contapassi` → **`step-counter`**, `torre-guardiana` →
  **`guardian-tower`**, `vena-viva` → **`living-vein`**, with their modules and functions.
- The chapters of the guide have English file names (`01-getting-started.md`, ...).
- New guide chapter: [14. The tools of `/blossom script`](docs/guide/14-script-tools.md).

## 1.0.0 — unreleased

`api_level` goes up to **2.0**: it is a MAJOR break, and an app that declares `api_level: "1.0"` no
longer loads. That is on purpose: it is better than an app that loads and misbehaves. A manifest that
does **not** declare the field is instead worth the lowest MINOR of the current MAJOR — today `2.0` —
that is, "I use nothing recent", which is what the optional field always meant.

### Events

- **`player_clicks_item_on_item(player, carried, other, slot, action)`**: an item held on the cursor
  clicked onto another item in an open container — the gesture a bundle answers to, here for every
  item. `carried` and `other` are the two stacks as they are before the click, `slot` is the number
  of the slot in the open container and `action` is `'left'` or `'right'`. It fires only if both
  stacks are there, and cancelling it means the click was handled: no swap, no merge, the two stacks
  stay as they were.

- **Every game event is cancellable except thirteen**: the life of the server and of the apps
  (`server_starts`, `server_shuts_down`, `app_reloading`, `app_reloaded`), the three `tick`s, the two
  ends of a session (`player_connects`, `player_disconnects`), `player_dies` and the three chunk
  events. For all the others the hook was moved onto the call that really performs the action, so
  cancelling stops the game and not just the event. The "Cancellable" column of the catalogue and
  `event.describe(name)` remain the source.

- **Events that fire after the fact stop what is left**, and the catalogue entry says exactly what:
  `player_places_block` puts back what was there and does not consume the item; `explosion_outcome`
  fires between the two halves of the explosion, so it saves the blocks but not the entities, already
  hit; `player_interacts_with_block` stops the advancement and the arm swing, it does not close the
  chest again; `player_respawns` leaves the player on the death screen. `player_escapes_sleep` is
  cancellable for consistency, but in that branch the game does nothing: the documentation says so
  plainly.

  Where cancelling would have to be **invented** rather than stopped, the event stays
  non-cancellable and points to the one asked earlier: `player_dies` to `player_takes_damage` — with
  health already at zero, cancelling would also mean deciding how much health to give back — and the
  two ends of a session to the door one comes in through.

- **Breaking, three events change moment** so they can be cancelled:
  - `player_picks_up_item` fires **before** the item enters the inventory, so `item` is the stack on
    offer and no longer the part that fitted;
  - `player_changes_dimension` fires **before** the move: `to_pos` is the destination chosen by the
    game and is no longer `null` when leaving the End;
  - `player_trades` fires before the costs are spent.

- **Mind the final `false`.** A handler that ends with a `print` returns a true value, and on an
  event that became cancellable this now stops the action: on `statistic` the statistic is not
  counted, on `player_picks_up_item` the item stays on the ground.

### Game content inside the pack

A pack can have a **`data/`** folder with the tree of a real datapack. It is handed to the game from
inside the pack: whoever installs it copies one thing, and there is nothing to put in
`<world>/datapacks/`. Every category goes through — recipes, loot tables, tags, advancements, and
also the dynamic registries and worldgen, which the game fixes when the world opens — because the
hook is the pack source, not a mixin per category.

- **`core.mc.data`**: `write(id, value)` and `write_text(id, lines)` to **generate** the content
  instead of repeating it.
- **`/blossom data generate <app>`** calls the app's `generate()` and writes real json, plus the
  `pack.mcmeta`. On command and not when the world opens, so the generator is ordinary code with the
  server running.
- **`/blossom data list <app>`** says what is in `data/` and which of it is generated.
- **`/blossom data export <app>`** makes it a zip in `<world>/blossom/.export/`: a datapack that runs
  without Blossom too.
- `data/.blossom-generated` lists what the generators produced, and regenerating rewrites and cleans
  up **only that**: a json written by hand in the same folder is never touched.

A Blossom pack sits at the bottom of the datapack list and cannot be turned off: at the bottom
because a real datapack must be able to fix a broken recipe of an app whose source one does not
have, not disableable because the app's code takes its own content for granted.

### Syntax

- **Functions without a name**: `fn(a, b) -> a + b`. They capture the frame they are born in, so
  they see what surrounds them without declaring it with `outer`. `fn name(...)` declares, `fn(...)`
  is a value: what tells them apart is the bracket right after `fn`.
- **Direct call of a function value**: `f(x)` with `f` bound to a function. `call(f, x)` stays, and
  is for calling by a name computed at runtime.
- **Interpolated strings**: `"hello ${name}"`. Single quotes stay literal, double quotes interpolate.
- **`match`**: `match(x, 0 -> 'zero', d: num -> ..., else -> ...)`. The subject is evaluated once,
  and the bodies of the arms not taken are not evaluated at all.
- **The `|>` operator**: `items |> list.filter(f) |> list.map(g)`. It is resolved while the code is
  read and costs nothing at runtime.
- **Default values and named arguments**: `fn f(a, b = 1)` and `f(1, b = 2)`. The default value is
  re-evaluated on every call that uses it. Named arguments go last, and the order is a load-time
  check.
- **Loops and `catch` accept a lambda** besides the expression with `_`:
  `list.map(l, fn(x) -> x * 2)`, `try(x, 'exception', fn(e) -> ...)`. In `reduce` a two-argument
  lambda reads `(accumulator, element)`. The short form with `_` stays, and is the fastest.
- The functional name of the `~` operator is no longer `match` but **`contains`**: `~` is a
  membership test, and `match` is for the construct of the same name.

### Renamed API (breaking)

Every page of `docs/api/` has its own `old -> new` table at the bottom. The rules:

| Rule | Example |
| --- | --- |
| no stutter with the module name | `score.scoreboard_add` -> `score.add` |
| one verb per operation | `delete`, `delete_file`, `*_remove` -> `remove` |
| one word for "how many" | `length` -> `size` |
| no opaque abbreviations | `loaded_ep` -> `loaded_for_entities`, `plop` -> `place_feature` |
| American English, chosen once | `neighbours` -> `neighbors`, `map_colour` -> `map_color` |
| a symbol lives in the module of its first argument | `split` only in `core.string`, `join` only in `core.list` |

Three modules are split, because they held different things together:

- `core.mc.scoreboard` -> **`core.mc.score`**, **`core.mc.team`**, **`core.mc.bossbar`**;
- inventories leave `core.mc.item` for **`core.mc.inventory`**;
- the `structure*` functions of `core.mc.world` join the templates in **`core.mc.structure`**.

### Removed

- **`system.system_variable_get`** and **`system.system_variable_set`**: they were a global space
  shared by every app of the same server, that is, the implicit state the language does without. To
  talk to another app there are events between apps, to keep data there is `core.io`.
- **`core.mc.system.task_join`**: it was the same symbol as `core.task`'s, exported twice.
- **`task_thread`**: registered but exported by no module, hence unreachable.

### Fixed

- **A call written after a value is now a call.** Brackets after `)` were a grouping: `f()()`
  silently answered the inner function instead of its result, and `f(1)(2)` gave a `ParseError` that
  talked about the state of the validator ("Too many numbers or variables"). As a consequence a
  function returning a function could not be called. `(fn(x) -> x * 2)(21)`, `add(1)(2)` and `f()()`
  now are what they look like.
- **`x |> fn(a) -> ...` is read as one thing.** The pipe binds tighter than `->`, so the subject ended
  up among the parameters of the signature and the error said "Only variables can be used in function
  signature, not 5". The brackets are no longer needed.
- **`_`, `_i` and `_a` are not zero outside a loop.** `list.map(l, fn() -> _ * 2)` answered a list
  of zeros: the implicits do not reach inside a lambda. Now it says so, and says which form to use.
- **`json.encode` writes only json.** `NaN` and infinity ended up in the text as they were, without
  quotes: a result no serious reader reads back. Now it is a `json_error`, filterable like the one of
  `decode`.
- **A numeric placeholder of `string.str` wants a number.** `string.str('%d', 'x')` answered `'0'`.
  Now it says what it received; a text written as a number still passes. Leftover arguments are an
  error instead of disappearing.
- **A name of the language does not become a variable.** `fn print(a) -> a` was already an error, but
  `print = 1` passed, and the same name meant two things. It also applies to parameters. `l`, `m` and
  `concat`, which the tokenizer builds by itself for `[...]`, `{...}` and interpolated strings, stay
  free names.
- **Bit shifts count the bits.** Java masks the width to the low 6 bits, and it showed through:
  `bitwise_shift_left(1, 64)` was `1` and `(1, 200)` was `256`. Beyond 64 bits nothing is left now,
  and a negative width is an error. Rotations stay defined modulo the width, which is what a rotation
  is.
- **What `==` calls equal is the same key.** `'1' == 1` was true but `{1 -> 'a'}` could not be found
  with `'1'`, because the hash code was the type's: the `HashMap` contract broken. Now the hash
  follows equality.
- **User function names are no longer those of the built-ins.** `fn sum(...)`, `fn get(...)`,
  `fn map(...)`, `fn size(...)` failed at definition: the check looked at the flat list of
  implementations, 160 names, 56 of them without a module to suggest ("'sum' is not available
  without an import", and there was nothing to import). A name without a qualifier always belongs to
  the user; the library is reached through an alias, so there was nothing to shadow. Only the prelude
  stays reserved (`if`, `loop`, `print`, `try`, ...), and using one of them as a function name says
  so instead of complaining about `->`.
- **A manifest without `api_level` did not load.** The default was written as `1.0` by hand while
  the runtime provided `2.0`: now it is the lowest MINOR of the current MAJOR, as the field says.
- **Constant folding at parse time no longer skips `try`.** A pure call with all-constant arguments
  was executed while the code was read: `try(json.decode('{bad'), 'json_error', ...)` caught nothing,
  `if(false, json.decode('{bad'), 'ok')` blew up on the dead branch, and a function containing that
  line made the **module load** fail without ever being called. If the early computation fails, the
  folding is now given up and the call fails where it is written.
- **A task's exception reaches whoever waits for it.** `task.wait` returned `null` and the error only
  ended up in the log, so a `try` around it saw nothing — the opposite of what `core.task.md`
  promised. The log stays, for the task nobody waits for.
- **The tokenizer no longer runs off the string.** A space at the end of a placeholder (`"${1 }"`) or
  a brace inside (`"${ {'k' -> 1} }"`) gave a raw `StringIndexOutOfBoundsException`: the bounds
  check came after the read instead of before. And `\${` is now a literal `${`, which could not be
  written before.
- **Signatures checked when they are read**: `...rest` must be the last parameter (before,
  `fn f(a, ...rest, b)` passed and bound the arguments to the wrong names), a parameter cannot be
  declared twice, and the lambda passed to `map`/`reduce` must have the parameters it is given —
  `list.reduce(l, fn(a) -> a, 0)` silently returned the initial accumulator.
- **`size` measures what has a size**: on a number it counted the digits and on a lazy list the
  characters of its representation (`list.size(list.range(5))` answered `14`). Now they are two
  `TypeMismatchError`s, and the one for the lazy list says how to materialise it.
- **No internal errors in place of language errors**: `l:0 <> l:1` said "please report this issue to
  Blossom", and `json.decode('1e999999')` let a JVM `NumberFormatException` out instead of a
  `json_error`.
- **The messages of `core.task` use the name one writes** (`task.wait`), not the implementation's
  (`task_join`).
- `modify` and `entity_types` were **registered twice**, with the body copied identically: the second
  registration overwrote the first.
- `core.task` documented `task_await` as "waits without returning the value", which is not what it
  does: it takes the next value produced with `yield` by a coroutine. Now it is called `next`, and
  `wait` is the one that waits for the end.
- `opacity` and `blocks_daylight` were commented out with an "investigate": the methods still exist,
  with a new name and no arguments. Implemented and exported.

### Tools

- **`tools/check-old-names.py`**: reads the rename table from the catalogs themselves and checks that
  no old name survives in `docs/`, `examples/` and the editor data.
- A test checks that **no symbol leaves two modules**, with the exceptions declared one by one.

## Before 1.0.0

### Added

- **Documentation comments**: `///` attached to the declaration, with `@param`, `@returns`,
  `@throws`, `@example`, `@see` and `@deprecated`. For the interpreter they stay comments — there is
  nothing new to learn and nothing that can break — and the editor is what reads them: the VS Code
  extension shows them on hover, in completion and in parameter help, and can write the skeleton of a
  block with one `@param` per parameter. The convention is described in
  `docs/guide/04-functions.md`.

- The events **`app_reloading`** and **`app_reloaded`**, with the name of the app as argument: the
  first fires before the outgoing version is torn down — the last moment its handlers still exist —
  the second after the new code has run, so the new version hears its own reload. Together they are
  the way to make state survive a `reload`. Neither is cancellable, and neither fires on a first load
  or on an `unload`.
- **`core.io`**: `read_nbt(root, path)` and `write_nbt(root, path, tag)`, on the same roots and with
  the same boundaries as the rest of the module.
- Two `core.io` roots that **always** exist, without declaring them: `data` and `shared`, both
  writable, under the world folder. They are created at startup.
- **Extended documentation of every event** in `docs/api/core.mc.event.md`: a card per event with
  when it fires, what it passes, what cancelling it means and the pitfalls; plus the sections on
  registration, cancellation and cost.

- **`core.http`**: HTTP requests to declared hosts — `get`, `post`, `request`, `get_json`,
  `post_json`, `hosts`, `encode_url`, `decode_url`, `encode_query`. No host is reachable until it is
  declared in `config/blossomscript.json`, redirects (off by default) go back through the allowlist
  at every hop, and a request on the server thread is an error that says to put it in a `task`. A
  status outside 2xx is a response, not an error; network failures arrive as `http_exception`.
  Documentation in `docs/api/core.http.md`.
- A library module can export a symbol under a name different from its implementation's: `http.get`
  exists even though `get` already belongs to lists and maps.
- The **`watchdog`** section in `config/blossomscript.json`: `timeout_ms` and `max_depth` for the apps
  that do not declare limits of their own.
- **`docs/guide/12-configuration.md`**: every key of `blossomscript.json` in one place, with type,
  default and effect.
- Errors born outside a command — event handlers, calls deferred with `schedule`, callbacks — show up
  **in chat for operators** as well as in the log. The same error repeated is shown once every five
  seconds, with the count of the suppressed ones: a broken handler inside a piston machine no longer
  makes the chat unreadable.
- `"autoload": true` in the manifest: the pack loads by itself as soon as the world is open, before
  `server_starts` fires.

### Changed

- **A single file API.** `core.io` and `core.mc.file` did the same job. `core.mc.file` no longer
  exists: `read_nbt`, `write_nbt` and `create_datapack` moved into `core.io`, which is the only place
  a script opens a file from. The three stay implemented in the game layer — NBT is a Minecraft binary
  format, a datapack is a concept of the game — but that does not show from a script, because
  `StdLib.extendModule` lets symbols be added to a core module instead of declaring a second one.
- `player_command` is no longer declared cancellable: it never really was, because the mixin ignored
  the return value.

- **The frame of a call is no longer a hash map**, and a value counts as a node of itself: the
  lambdas that wrapped every argument and every result are gone. Function calls **-24%**, arithmetic
  loop **-32%**, list access **-25%**.
- **The name of a variable no longer lives on the value.** The mutable field every `Value` carried to
  say which variable it was bound to is gone, and with it the defensive copy every argument went
  through when entering a call: the name belongs to the node that reads the variable, from parsing.
  Four-argument calls **-20%**, one-argument **-12%**, arithmetic loop **-6%**.
- `docs/guide/10-performance.md` re-measured: the orders of magnitude account for the changes above
  (~55 ns per iteration, ~178 ns per call, ~900,000 operations in a tick), and "what the interpreter
  already does" mentions name resolution at parse time and the two-array frame. New: the
  per-argument cost of a call, ~65 ns beyond the first.
- `_x`, `_y` and `_z` are no longer prepared in every frame: `scan` writes them before evaluating the
  body, and outside a scan they are undefined variables like any other (that is, `null`, not `0`).

- **An app is a single one for the whole server, like a plugin.** The distinction between global and
  per-player apps is gone: with it go the per-player hosts, the per-app shared globals (module
  bindings have that role) and the keys `system_info('app_scope')` and `system_info('app_players')`.
- **Scarpet's names leave the API and the messages.** `system_info('scarpet_version')` becomes
  `system_info('blossom_version')`, the NBT marker `__scarpet_marker` becomes `__blossom_marker`, the
  `scarpet_custom` key of `plop` becomes `blossom_custom`, and a generated data pack declares
  `'source' -> 'blossom'`.

- **The `core.io` roots live inside the world folder.** Their paths are written relative to the
  world; an absolute one is valid only if it still points inside, otherwise the root is discarded with
  a warning in the log. The boundary is checked again after resolving symbolic links, so not even a
  root that is a link pointing outside passes. A configuration that pointed outside the world must be
  rewritten: those roots now open nothing.

### Guide and examples

- **`docs/guide/`**: chapters that explain the language instead of listing its functions —
  getting started, values, operators, functions, control flow, modules, errors, events, concurrency,
  performance, recipes.
- The guide fragments that do not need Minecraft are **run by the tests** (`GuideSnippetsTest`),
  together with the pure-logic module of the `living-vein` example: a guide that lies breaks the
  build.
- Two new example projects: **`step-counter`** (one file: events, HUD, state on disk) and
  **`living-vein`** (four modules, a task outside the tick, a critical section, an event between
  apps).

### Removed

- **The `<world>/blossom/<app>.data/` folder**: apps write into the `data` and `shared` roots of
  `core.io`, keeping their data apart with a subfolder named after them.
- **The whole `core.mc.file` module**, with `read_file`, `write_file`, `list_files`, `delete_file`,
  `load_app_data` and `store_app_data`. Migration table in `docs/api/core.io.md`. Three differences
  to keep in mind: the root is an argument, a missing file is an `io_exception` and not a `null`
  (`read_nbt` is the exception and stays `null`), and the extension is part of the path.
- **Eleven game functions that added nothing**: `material`, `item_category` and
  `enable_hidden_dimensions` always answered the same thing; `world_time`, `last_tick_times`, `seed`,
  `view_distance` and `current_dimension` duplicated a key of `system_info`; `property` and
  `block_properties` are a subset of `block_state`; `create_marker` and `remove_all_markers` were a
  prepackaged armor stand, which `spawn` does more generally.

- `core.mc.world.relight` and `core.mc.world.reset_chunk` **no longer exist**: they were declared,
  documented and implemented as `return null` — the real implementation had been commented out since
  before the extraction. A function that does not do what it says is worse than one that is not there.
- Dead code: two classes (`GlocalFlag`, `LoadException`), twenty-seven methods nobody called
  (leftovers of the Carpet layer in `access/Host`, of Scarpet's import system in `ScriptHost`, and a
  few converters never used), the blocks of commented-out code left from Scarpet in `WorldTools`,
  `EntityValue`, `WorldAccess` and `Expression`, and 85 unused imports.
- `system_info('world_carpet_rules')`: it answered an empty map by definition, because Carpet is not
  there, so it told nothing.
- The inherited deprecation warnings, and the machinery that emitted them (`issueDeprecation`,
  `Expression.alias`). A function either is there or it is not: at this point of the project there is
  nothing to migrate from, and the functions that carried the warning (`world_time`, `seed`,
  `view_distance`, `material`, `property`, ...) are documented and stay.

### Fixed

- **The execution order of a project's modules got lost.** `ProjectLoader` built the map of modules
  in dependency order and then handed it over with `Map.copyOf`, which does *not* keep insertion
  order; `inDependencyOrder()` read that map, and `Interpreter.forProject` iterated over it to start
  the bodies. The result: the body of a module could run before the body of a module it imports, and
  an `alias.function()` call in the body failed with `has not finished loading yet` depending on the
  hash of the names. Now the map stays an unmodifiable `LinkedHashMap`, here and in the two copies
  that end up in `BlossomHost`. Regression test:
  `ModuleSystemTest.runsEveryModuleAfterTheOnesItImports`.
- `modify(player, 'breaking_progress', n)` threw a `NullPointerException` when the player was not
  breaking anything: now, without a block under the cursor, it does nothing.
- Clouds were visible **in full** behind a translucent fill, as if the shape were not there: the game
  draws every translucent phase before the clouds, and a translucent face does not write depth. Now
  the translucent fill is drawn after the clouds, and covers them in proportion to its alpha — behind
  a 50% sphere the cloud shows at half.
- `core.math.sign`, `core.math.exp` and `core.list.reverse` were declared by their modules but not
  implemented: calling them answered `Function 'sign' is not defined yet`. Now they exist.
- `_trace` inside a `catch` raised an internal `NullPointerException` when the stack held a function
  without a module (code from `/blossom script run`).
- The "unknown exception type" message was in Italian; error messages are in English.
- Documentation aligned with the real behaviour: `put` on a list **replaces** by default (`'insert'`
  inserts, `'extend'` concatenates), `has` on a list asks about the **index** and not membership, a
  lazy list does not know its own length, and `try` does not catch language errors. The
  `guardian-tower` example used `_e` for the caught value, which does not exist: it is `_`.

### Complete game API

- Scarpet's API was ported in full: **156 functions in 13 `core.mc.*` modules** (blocks, world, volume
  scanning, entities, items, screens, scores, NBT, text, effects, events, files, system). The earlier
  "curated" API (`block.get`, `world.time`, `player.all`) was retired: its short names shadowed
  library symbols (`list.get`) and it covered a fifth of the functions.
- **50 events** with a typed catalogue: arity and cancellability declared, checked at registration
  time, and documented in `docs/api/core.mc.event.md`. Plus 5 events that can be attached to a single
  entity with `entity_event`.
- A handler cancels the action by returning a **true** value: the magic string `'cancel'` no longer
  exists.
- Application events: `handle_event` declares an invented event, `signal_event` raises it. Two apps
  can talk to each other without knowing each other.
- `schedule(tick, function, ...)` defers a call to a future tick.
- At startup the mod checks that every symbol declared in a module really exists: an import that
  passes and a call that fails later is not an error anyone wants to be able to have.

### Packs and files

- `/blossom script load <name>` also loads **packs**: a folder with a `blossom.json`, its `src/` and
  every module the entrypoint imports. The world folder is read again at every listing, so a pack
  copied while the server runs can be loaded right away.
- Source extensions go from `.sc`/`.scl` to **`.bs`/`.bsl`**.

## [0.1.0] — not yet released

First version: extraction of Scarpet's interpreter and redesign of the language around modules,
typed errors and a curated game API.

### Added

- **`blossom-core`**: the interpreter as a pure Java module, with no `net.minecraft.*` import,
  testable and measurable without downloading the game. An `Interpreter` facade with a builder for
  importable modules, disk storage and registration of external APIs.
- **Module system**: `module`, `import "..." as alias`, `pub`, `fn`, `let`, `let mut`. Static
  resolution of symbols, detection of circular imports, a `blossom.json` manifest with local path
  dependencies, the `src/` layout.
- **Typed errors** with category, file, line, column, a stack that crosses modules and a fuzzy
  suggestion: `ParseError`, `ImportError`, `UndefinedSymbolError`, `TypeMismatchError`,
  `ArityError`, `IndexOutOfBoundsError`, `NullReferenceError`, `RuntimeError` with a subcode.
- **A packaged standard library**: `core.math`, `core.string`, `core.list`, `core.map`,
  `core.json`, `core.task`, `core.system`, plus a minimal prelude.
- **`blossom`**: a Fabric mod independent of Carpet, the `/blossom script` command, and the game
  modules `core.mc.block`, `core.mc.world`, `core.mc.entity`, `core.mc.player`, `core.mc.event`.
- **Events**: `tick`, `player_joins`, `player_leaves`, `block_dispenses`, `bell_rings`. Handlers are
  registered by passing the function as a value and disappear when the app is reloaded.
- Documentation for every module in `docs/api/`, an example project in `examples/guardian-tower`,
  and a reference benchmark (`./gradlew :blossom-core:benchmark`).

### Changed from Scarpet

A deliberate and intended compatibility break: `.sc` apps written for Scarpet do not run.

- `global_` variables are no longer the way to share state between files: modules are
- `import(name, symbols...)` as a function no longer exists, the import is a preamble declaration
- library functions are no longer global: they have to be imported, and the error says which import
  is needed
- the bare name of a function now *is* the function, so passing it to another module always works
- `fn`, `let`, `mut`, `pub`, `module`, `import`, `as` are reserved names
- `delete` on an index outside the list is an error instead of deleting the wrong element
