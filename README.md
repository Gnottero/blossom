# Blossom

A scripting language for Minecraft, and the Fabric mod that runs it. It is built around three
things: **real modules**, **readable errors** and a **curated game API** instead of a huge, flat
surface.

A pack carries **behaviour and content**: the code that reacts to what happens in the world, and
the recipes, tags, loot tables and advancements that used to need a separate datapack.

It does one thing, running code that manipulates the world: no rules, no server optimisations, no
administration tools.

The interpreter descends from the code of Scarpet (fabric-carpet, MIT), from which it was extracted
and redesigned; the full attribution is in [`NOTICE`](NOTICE).

It is **server-side**: install it on the server and players on vanilla lose nothing. The same jar
placed on a client too adds a single thing — the native drawing of the shapes of
[`core.mc.shape`](docs/api/core.mc.shape.md), which without the client show up as particles.

## How it is laid out

```
blossom-core/     the interpreter: pure Java, zero net.minecraft.* imports, testable without the game
blossom/          the Fabric mod: commands, events, and the core.mc.* modules
docs/             the documentation of the language and the library
examples/         complete example projects
vscode-blossom/   the VS Code extension
```

The core does not know Minecraft exists. The mod hands it its own API through a single hook
(`Interpreter.Builder.withApi`), and that is why the language tests run in a second without
downloading the game.

## Installation

You need Minecraft **26.4-snapshot-4**, Fabric Loader 0.19.5+ and Java 25. Build the mod with:

```
./gradlew :blossom:build
```

The jar ends up in `blossom/build/libs/`. Copy it into the `mods/` folder of the server or the
client.

To try it without installing anything: `./gradlew :blossom:runServer` starts a development server
(the first time, accept Mojang's EULA by writing `eula=true` in `blossom/run/eula.txt`).

## The first script

The scripts of a world live in `<world>/blossom/`. One file, one app:

```
// <world>/blossom/greeter.bs
module greeter;

import "core.mc.text" as text;
import "core.mc.entity" as entity;
import "core.mc.event" as event;

fn welcome(player) -> (
    text.send(player, 'welcome to this server');
    false
);

event.on('player_connects', welcome);
print('greeter ready');
```

In game:

```
/blossom script load greeter
/blossom script in greeter entity.player('all')
/blossom script run 2 + 3 * 4
```

| Command | What it does |
| --- | --- |
| `/blossom script run <code>` | evaluates code on the fly, in a clean interpreter |
| `/blossom script in <app> <code>` | evaluates code inside a loaded app, which sees its functions and state |
| `/blossom script load <app>` | loads (or reloads) `<world>/blossom/<app>.bs`, or the pack `<world>/blossom/<app>/` |
| `/blossom script reload <app>` | like `load`, for apps already loaded |
| `/blossom script unload <app>` | unloads an app and its event handlers |
| `/blossom script list` | what is loaded, and what is on disk |
| `/blossom script scan` · `fill` · `outline` | an expression over every block of an area: count, fill, draw the shell |
| `/blossom script in <app> invoke <fn> [arguments]` | calls a function of the app from the command line; `invokepoint` and `invokearea` pass it a position or an area |
| `/blossom script in <app> globals` | the functions and bindings of the app's main module |
| `/blossom script event` · `event add` · `event remove` | the handlers of every event, and attaching or detaching one by hand |
| `/blossom script explain` | how the parser reads an expression, a function or an app |
| `/blossom script stop` · `resume` | stops every script on the server, and lets them run again |
| `/blossom pack update <app>` | re-resolves the dependencies and rewrites `blossom.lock` |
| `/blossom data generate <app>` | calls the app's `generate()` and writes the content into `data/` |
| `/blossom data list <app>` | what is in `data/`, and which of it is generated |
| `/blossom data export <app>` | makes it a zipped datapack, which runs without Blossom too |
| `/blossom profile run <code>` | measures where the time goes, function by function |
| `/blossom profile in <app> <code>` | the same, inside a loaded app |
| `/blossom profile start <app>` / `stop <app>` | measures a live app, event handlers included |

## Game content

A pack can carry **content** too: recipes, loot tables, tags, advancements — what used to need a
separate datapack. It lives in a `data/` folder next to `src/`, with the tree and the json of a real
datapack, and it is handed to the game from inside the pack: whoever installs it copies one thing.

```
my-pack/
├── blossom.json
├── src/main.bs
└── data/
    ├── pack.mcmeta
    └── mine/recipe/sword.json
```

The json is written by hand, or **generated** by the code — which is the reason to have a language
instead of forty nearly identical files:

```
pub fn generate() -> list.map(map.keys(METALS), fn(m) -> data.write("mine:recipe/sword_${m}", {
    'type' -> 'minecraft:crafting_shaped',
    'pattern' -> [' # ', ' # ', ' / '],
    'key' -> {'#' -> "minecraft:${m}_ingot", '/' -> 'minecraft:stick'},
    'result' -> {'id' -> "minecraft:${METALS:m}_sword"}
}));
```

```
/blossom data generate my-pack
5 resources written in data/. Run /reload to apply them; a new registry entry or worldgen needs a restart.
```

What comes out is real json, which stays on disk and can be looked at with `git diff`. A file
written by hand in the same folder is never touched: the list of what is generated is kept in
`data/.blossom-generated`. The details are in
[13. Game content](docs/guide/13-game-content.md).

## A multi-file project

A project is a folder with a manifest and a `src/` folder:

```
my-project/
├── blossom.json
└── src/
    ├── main.bs              ← module main
    └── math/
        └── vector.bs        ← module math.vector
```

```json
{
  "name": "my-project",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "2.0",
  "autoload": true,
  "dependencies": {
    "shared_utils": { "path": "../shared-utils", "version": "^1.2.0" }
  }
}
```

`"autoload": true` loads the pack on its own as soon as the world is open, without
`/blossom script load` — before `server_starts` fires, so it can listen to that too.

Three keys take the place of what a Scarpet app said in `__config()`: `"strict": true` makes reading
a never-assigned variable an error, `"event_priority"` decides which app hears an event first (and a
cancel ends the round), `"requires"` lists the mods the pack needs, with the version written as in
`fabric.mod.json`. The details are in [chapter 6 of the guide](docs/guide/06-modules.md).

The name declared at the top of each file must match its position inside `src/`: the loader checks
it before running anything.

### Dependencies

A project can depend on another in three ways, which exclude each other:

```json
"dependencies": {
  "utils":  { "path": "../utils", "version": "^1.2.0" },
  "tower":  { "pack": "tower", "version": "^1.0.0" },
  "vector": { "version": "^2.0.0" }
}
```

- **`path`** — a folder, usually next to the project. It is the way to work on two projects together.
- **`pack`** — another pack already installed in the same world, under `<world>/blossom/`. It is the
  way to depend on something an administrator installed: whoever writes the manifest does not have to
  invent paths that do not exist on their machine.
- **`version` alone** — from the registry, if the server has one configured. Off by default.

The `version` is a [semver](https://semver.org) constraint and **it really is checked**: `^1.2.0`
accepts from 1.2.0 up to 2.0.0 excluded, `~1.2.0` only patches, `>=1.2, <2` combines, `*` accepts
everything. Below 1.0.0 every increment may break, so `^0.2.3` stops at 0.3.0. A pre-release
(`1.0.0-rc1`) is never picked by a constraint that does not name it.

If the version found does not satisfy the constraint, the app does not load:

```
CompatibilityError
  in /world/blossom/app/blossom.json:1:1

  Dependency 'utils' (path ../utils) is at version 2.0.0, which does not satisfy the constraint
  ^1.2.0 declared in the manifest.
```

Imports can be **circular**: two modules calling each other are a normal case as soon as a project
grows. All the modules are registered before the first body runs, so as long as the calls sit inside
functions there is no ordering problem. What cannot be done is *reading*, while a module loads, a
binding of a module of the same cycle that has not run yet — there one of the two has to come first,
and the error says so:

```
ImportError

  Module 'b', imported as 'b', has not finished loading yet: 'from_b' does not exist yet. This
  happens with circular imports, where one of the two modules has to run first. Move the use inside
  a function, which is evaluated when you call it and not while the module loads.
```

Dependencies are **transitive** and each has its own namespace: inside `utils`, `import "math/vec"`
means what the manifest of `utils` says, not yours. Two projects can call two different libraries
`math` without knowing anything about each other.

### The lock

A constraint like `^1.0.0` is a question, and the answer changes over time. So that the same app
loaded on two servers picks the same version, the choice is written to `blossom.lock` next to the
manifest:

```json
{
  "format": 1,
  "dependencies": {
    "vector": { "source": "registry", "version": "1.4.0", "sha256": "9f86d0..." },
    "tower":  { "source": "pack", "version": "1.0.0" }
  }
}
```

From then on that version is reused until you ask to change it:

```
/blossom pack update <app>
```

The lock changes its mind on its own in one case only: when the locked version no longer satisfies
the constraint of the manifest, that is, when you rewrote it.

**Path dependencies are not locked**: they are the folder next door, which you are editing right
now, and locking it would make the project fail on every save. The `sha256` hash is there only for
those taken from the registry, which are the only ones whose content comes from outside: if it
changes without the version changing, the app does not load.

### The registry

Off by default. It is turned on in `config/blossomscript.json`:

```json
{ "registry": { "directory": "/srv/blossom-packs" } }
{ "registry": { "url": "https://example.org/blossom/" } }
```

**Directory-backed** — `<root>/<name>/<version>/`, with an ordinary project inside. No server to
maintain, the content can be inspected with `ls`. It is the right shape for the internal libraries of
a server or a group.

**Over http** — a static `index.json` and some zip files:

```json
{
  "vector": {
    "1.4.0": { "url": "vector-1.4.0.zip", "sha256": "9f86d0..." }
  }
}
```

The `sha256` is mandatory: an archive that arrives from the network and is unpacked without a check
is arbitrary code running on a server, and anyone in the middle of the connection can replace it. An
entry without a hash is ignored. Each version is downloaded once and stays in
`<world>/blossom/.cache/`, so a restart does not redo twenty downloads and a server without a
connection still loads the apps it already has.

### `api_level`

`api_level` is `MAJOR.MINOR` and describes **the Blossom API**, not the app — the app's version is
`version`, and that is semver. They are two separate numbering schemes on purpose: one says "what I
am", the other "what I need".

- **MAJOR** changes when something that worked stops working: a symbol removed or renamed, a
  signature or a meaning that changes, a grammar rule that invalidates code already written, an event
  that stops firing.
- **MINOR** changes when something is added: a module, a function, an optional parameter, an event.

An app runs if the MAJOR matches and its MINOR does not exceed the mod's. The two directions fail for
different reasons and the message says which: a different MAJOR means the app is written for another
API; a higher MINOR means it uses something this version does not have yet, and the mod needs
updating. Not declaring it means the lowest MINOR of the current MAJOR, today `2.0`: "I use nothing
recent". The level of this version is **2.1**, which adds `core.mc.command` and the keys `strict`,
`event_priority` and `requires`.

A folder like this, copied under `<world>/blossom/`, is a **pack**: it loads with
`/blossom script load <folder-name>` like any other app, even with the server running.

In [`examples/`](examples) there are three complete projects: [`step-counter`](examples/step-counter)
(a single file: events, HUD, state on disk), [`guardian-tower`](examples/guardian-tower) (several
modules, private symbols, a cancellable event, a `try`) and [`living-vein`](examples/living-vein)
(four modules, a task, a critical section, an event between apps).

### Execution limits

An app that loops forever inside an event does not slow down itself: it stops the tick, and the
world with it. That is why every execution has a budget, and going over it is an error:

```
ExecutionBudgetError[time]
  in tower.watch  (src/tower.bs:12:5)

  Execution stopped after 2000 ms: the script is running too long and the server is waiting for
  it. If it really needs all that time, move it to a task.start() or raise 'timeout_ms' in the manifest.
```

There are three default limits, because the three situations are different:

| When | Time | Call depth |
| --- | --- | --- |
| inside a game event | 2 s | 256 |
| code started by `/blossom script run` or `load` | 15 s | 256 |
| inside a `task.start()` | no limit | 256 |

A `task.start()` has no deadline on purpose: it runs on its own thread, it does not hold up the
tick, and it is the thing to use when a job really is long.

An app can declare its own limits in the manifest, and then they apply in all three cases:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` removes them entirely — a legitimate choice for an app you trust, but from then
on an infinite loop in it requires restarting the server.

The watchdog's error **cannot be caught** with `try`: if it could, an infinite loop inside a `try`
would keep running.

## Server configuration

A single, optional file: `config/blossomscript.json`. Four sections, each one off until you write it.

```json
{
  "registry": { "directory": "/srv/blossom-packs" },
  "io": { "roots": { "exchange": "exchange" }, "writable": ["exchange"] },
  "http": { "hosts": ["api.example.org"] },
  "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
}
```

- **`registry`** — where the dependencies that are not already on disk come from.
- **`io`** — the folders [`core.io`](docs/api/core.io.md) can open, **in addition** to `data` and
  `shared`, which always exist and are where apps keep their own data. The paths are relative to the
  **world folder** and cannot leave it: the rest of the server's disk is out of reach of any
  configuration.
- **`http`** — the hosts [`core.http`](docs/api/core.http.md) may contact, one by name. Without this
  section scripts have no network.
- **`watchdog`** — the execution limits for the apps that do not declare their own.

Every key, with its type and default, is in
[12. Server configuration](docs/guide/12-configuration.md).

## The language in two minutes

```
module example;

import "core.list" as list;                 // the alias is mandatory
import "core.math" as math;

let mut counter = 0;                        // module state, reassignable
let threshold = 10;                         // module state, immutable

fn private_double(x) -> x * 2;              // without 'pub' it does not leave the module

pub fn count() -> (
    counter += 1;
    if(counter > threshold, 'too many', counter)
);

pub fn double_all(l) -> list.map(l, private_double(_));
```

### Types on parameters

Parameters can declare a type. It is optional, one at a time:

```
fn distance(a: list, b: list) -> ...
fn repeat(times: num, what) -> ...           // 'what' stays unconstrained
fn apply(f: func, x: any) -> call(f, x);     // 'any' documents without constraining
```

The types are `num`, `str`, `bool`, `list`, `map`, `func`, `null`, `any`, plus the game's:
`entity`, `block`, `text`, `nbt`, `screen`. (It is called `func` and not `fn` because `fn` is
already the keyword a function is declared with.)

A misspelled type is an error **when you define the function**, not the first time someone calls it:

```
ParseError
  Unknown type 'nmu' in the signature: the types are num, str, bool, list, map, func, null, any...
  Did you mean: num?
```

An argument of the wrong type is an error **at the boundary of the function**, which is the point:
it says which parameter and what it received, instead of turning up three lines into the body on an
unrelated operation.

```
TypeMismatchError
  in distance  (src/geo.bs:4:1)

  Parameter 'a' of distance wants a list, but got a string (two).
```

Outside a signature `:` stays what it was, that is, access to an element: `items:0`, `table:'key'`.

In short:

- **a file is a module**, with its own namespace: no `global_` shared by convention
- **imports always have an alias**, so two libraries can never fight over names
- **`pub` decides what leaves**; accessing a private symbol is a load error, not a runtime one
- **`fn` and `let`** instead of conventions on name prefixes
- **optional types on parameters**, which move the error to the boundary of the function
- **no implicit global space**: `sqrt(4)` without an import is an error that tells you what to import
- **errors have a category, a position and a stack** that crosses modules

The rest is the syntax of an expression language: `for`/`while`/`map`/`filter`/`reduce`, lists,
maps, operators.

## Errors

```
UndefinedSymbolError
  in math.vector.add  (src/math/vector.bs:4:12)
  called from main    (src/main.bs:8:1)

  Symbol 'ad' not found in module 'math.vector'.
  Did you mean: 'add'?
```

The categories form two families, and the difference matters when writing a `try`:

- **language errors** — `ParseError`, `ImportError`, `UndefinedSymbolError`, `TypeMismatchError`,
  `ArityError`, `IndexOutOfBoundsError`, `NullReferenceError`, `RuntimeError`: they are wrong code,
  and `try` deliberately does **not** catch them. Many arrive already at load time.
- **thrown exceptions** — `throw()` and the operations that can fail because of the world
  (`value_exception`, `unknown_item`, `io_exception`, `json_error`, ...): those are caught, by type,
  with the hierarchy of filters.

The details, with the table of filters, are in [`docs/guide/07-errors.md`](docs/guide/07-errors.md).

## The VS Code extension

[`vscode-blossom/`](vscode-blossom) has the editor support: highlighting, completion on the modules
and on the real symbols of the API, hovers with signatures, and the errors that can be told without
running anything (imports without an alias, symbols that do not exist, wrong events, a module name
that does not match its path). The data is not written by hand: it is regenerated from the sources
of the mod with `node vscode-blossom/tools/generate-api.js`.

## Documentation

- [The guide](docs/guide/README.md) — fourteen chapters: syntax, modules, errors, events,
  concurrency, performance, recipes, game content, the tools of `/blossom script`
- [API overview](docs/api/Overview.md) — every module
- [`CHANGELOG.md`](CHANGELOG.md) — what changed, version by version

## License

MIT, like fabric-carpet from which the core derives. The original copyright is kept in
[`NOTICE`](NOTICE).
