# 6. Modules and projects

A file is a module. It has a declared name, a namespace of its own, and an explicit list of what it
lets out. There is no implicit global scope, and there are no naming conventions about prefixes:
what is public is public because it says so.

## The three keywords

```
module math.vector;                 // who I am: must match the file's path

import "core.math" as math;         // what I use, and what I call it here
import "geometry/angles" as ang;

pub fn add(a, b) -> [a:0 + b:0, a:1 + b:1];     // leaves the module
fn normalise(v) -> ...;                         // stays here

pub let ORIGIN = [0, 0];            // bindings are exported too
let mut cache = {};
```

**`module`** goes on the first line, and the name must correspond to the position inside `src/`:
`src/math/vector.bs` → `module math.vector;`. The loader checks this before executing anything, and
it exists so that a module's name is one thing rather than two that can drift apart.

**`import`** **always** carries an alias. There is no form without `as`: two libraries can never
fight over a name, because the name is chosen by you, in this file, and applies only here. The path
of a local module uses `/` (`"tower/construction"`), the path of a library module uses `.`
(`"core.mc.block"`).

**`pub`** decides what leaves. Touching another module's private symbol is an error **at load
time**, not a surprise at run time:

```
UndefinedSymbolError
  Module 'lib' does not export 'secret'.
```

## The project

```
my-app/
├── blossom.json
├── blossom.lock          ← written by the loader, not by you
└── src/
    ├── main.bs           ← module main          (the entrypoint)
    ├── ledger.bs         ← module ledger
    └── tower/
        ├── construction.bs   ← module tower.construction
        └── register.bs       ← module tower.register
```

```json
{
  "name": "my-app",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "2.0",
  "autoload": true,
  "dependencies": {
    "utils": { "path": "../utils", "version": "^1.2.0" }
  },
  "watchdog": { "timeout_ms": 5000, "max_depth": 512 }
}
```

| Field | Required | What it does |
| --- | --- | --- |
| `name` | yes | the project's name |
| `entrypoint` | yes | the file loading starts from |
| `version` | recommended | the project's semver; this is what others constrain |
| `api_level` | no (`2.0`) | which Blossom API it needs |
| `autoload` | no (`false`) | the pack loads itself as soon as the world is open |
| `dependencies` | no | see below |
| `watchdog` | no | execution limits, or `false` to remove them |
| `strict` | no (`false`) | reading a variable nothing was assigned to is an error instead of `null` |
| `event_priority` | no (`0`) | who hears an event first: the highest number first |
| `requires` | no | the mods the pack needs, with their versions |

A directory like that, copied under `<world>/blossom/`, is a **pack**:
`/blossom script load my-app`.

## Starting on its own

`"autoload": true` means that pack does not need anyone to type `/blossom script load`: it comes up
as soon as the world is open, on a dedicated server as well as in singleplayer. It is the
declaration of an app that must always be there (a warden, a ledger, a custom command), not of one
you are still trying out.

Loading happens **before** `server_starts` is fired, so an app that starts on its own can listen for
that event too. If it fails to load (broken manifest, missing module, error in the body) the error
goes to the log and to operators in chat, and the rest of the world starts anyway.

Single-file apps (`<name>.bs`, no directory and no manifest) have nowhere to declare it: to make one
start on its own, give it a `blossom.json` and turn it into a pack.

## Load order and cycles

Modules are **all registered before** the first body runs. Two modules that call each other are a
normal case, not an error:

```
// a.bs
module a;
import "b" as b;
pub fn ping(n) -> if(n <= 0, 'done', b.pong(n - 1));

// b.bs
module b;
import "a" as a;
pub fn pong(n) -> a.ping(n - 1);
```

What you cannot do is **read**, while a module is loading, a binding of a module in the same cycle
that has not run its own body yet: there one of the two necessarily comes first, and the error says
so instead of handing you a mysterious `null`.

```
ImportError

  Module 'b', imported as 'b', has not finished loading yet: 'from_b' does not exist yet. This
  happens with circular imports, where one of the two modules has to run first. Move the use inside
  a function, which is evaluated when you call it and not while the module loads.
```

Rule of thumb: in a module body do only declarations and handler registrations; put the real work
inside functions.

## Depending on another project

Three forms, mutually exclusive:

```json
"dependencies": {
  "utils":  { "path": "../utils", "version": "^1.2.0" },
  "tower":  { "pack": "tower", "version": "^1.0.0" },
  "vector": { "version": "^2.0.0" }
}
```

- **`path`**: a directory, usually next to the project: this is how you work on two projects at
  once.
- **`pack`**: another pack installed in the same world, under `<world>/blossom/`: this is how you
  depend on something an administrator put there, without inventing paths that do not exist on their
  machine.
- **`version` alone**: from the registry, if the server has one configured. Off by default.

`version` is a [semver](https://semver.org) constraint and it is genuinely enforced: `^1.2.0`
accepts from 1.2.0 up to but excluding 2.0.0, `~1.2.0` only patches, `>=1.2, <2` combines, `*`
accepts anything. Below 1.0.0 every increment may break, so `^0.2.3` stops at 0.3.0. A pre-release
(`1.0.0-rc1`) is never selected by a constraint that does not name it.

Dependencies are **transitive**, and each one has its own namespace: inside `utils`,
`import "math/vec"` means what `utils`'s manifest says it means, not what yours says. Two projects
can call two different libraries `math` without knowing anything about each other.

If the version found does not satisfy the constraint, the app does not start:

```
CompatibilityError
  in /world/blossom/app/blossom.json:1:1

  Dependency 'utils' (path ../utils) is at version 2.0.0, which does not satisfy the constraint
  ^1.2.0 declared in the manifest.
```

## The lock file

A constraint like `^1.0.0` is a question, and the answer changes over time. The choice that was made
is written into `blossom.lock` next to the manifest, so that the same app loaded on two servers
picks the same version:

```json
{
  "format": 1,
  "dependencies": {
    "vector": { "source": "registry", "version": "1.4.0", "sha256": "9f86d0..." },
    "tower":  { "source": "pack", "version": "1.0.0" }
  }
}
```

To change your mind: `/blossom pack update <app>`. The lock updates itself in exactly one case:
when the locked version no longer satisfies the constraint, that is, when you were the one who
rewrote the manifest.

**Path dependencies are not locked**: that is the directory next door, the one you are editing right
now. The `sha256` fingerprint exists only for those taken from the registry, which are the only ones
whose contents come from outside.

## The registry

Off by default; turned on in `config/blossomscript.json`:

```json
{ "registry": { "directory": "/srv/blossom-packs" } }
{ "registry": { "url": "https://example.org/blossom/" } }
```

**Directory-backed**: `<root>/<name>/<version>/`, with an ordinary project inside. No server to
maintain. **Over http**: a static `index.json` and some zip files, with `sha256` **mandatory**: an
archive downloaded and executed without a check is arbitrary code on a server. Each version is
downloaded once and stays in `<world>/blossom/.cache/`.

## `strict`, `event_priority`, `requires`

The three settings a Scarpet app gave from inside, in `__config()`, and that here live with the
others where they can be read without running anything.

`"strict": true` turns a variable read before anything was assigned to it into an error that names
it, instead of a `null` that travels on: the way to find the misspelled name a script keeps reading
as nothing. It applies to the whole pack.

`"event_priority": 10` orders the apps on the same event: the highest number hears it first, the
others in the order they registered. It matters because **a cancel ends the round**: once a handler
cancels, the action will not happen and the ones after it are not asked. The app that decides should
come before those that only observe. Without it every app has `0`.

`"requires"` lists the mods the pack needs, with the versions written as in a Fabric
`fabric.mod.json`, and is checked when the pack loads:

```json
"requires": { "minecraft": ">=26.4", "carpet": "*", "lithium": "^0.15" }
```

A missing mod, or one with a version out of the range, is a load error that says which. The core
does not check them: it does not know which mods are installed, the mod does.

## `api_level`

`api_level` is `MAJOR.MINOR` and describes **the Blossom API**, not the app: the app's version is
`version`, and that is semver. They are two separate numbering schemes on purpose: one says "what I
am", the other "what I need".

- **MAJOR** changes when something that worked stops working.
- **MINOR** changes when something is added.

An app runs if the MAJOR matches and its MINOR does not exceed the mod's. Not declaring it means the
lowest MINOR of the MAJOR this mod provides (today `2.0`), that is, "I use nothing recent".

## How to split an app

A split that holds up, from the experience of the examples in `examples/`:

| Module | What goes in it |
| --- | --- |
| `main` | the `event.on` calls, the startup `print`, the public functions an admin calls with `script in` |
| one module per domain (`tower.construction`, `vein.geology`) | the logic, knowing nothing about events |
| a state module (`register`) | the `let mut` bindings, saving to disk, and the functions to read it back |

The part that touches the world and the part that decides **what** to do stay separate: the second
can be tried with `/blossom script in <app> ...` without having to make the real event happen.
