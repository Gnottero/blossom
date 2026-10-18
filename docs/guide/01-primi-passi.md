# 1. Getting started

## Requirements

Fabric Loader 0.19.3 or newer and Java 25, on the server. Build the jar from the repository:

```
./gradlew :blossom:build
```

The artifact lands in `blossom/build/libs/`. Copy it into `mods/`. Carpet is not required, and
neither is anything else.

The mod is **server-side**. Players connecting with a vanilla client lose no functionality. Putting
the same jar on the client adds one thing only: real rendering for the shapes of
[`core.mc.shape`](../api/core.mc.shape.md). Without the client-side jar those shapes are drawn as
particles instead.

To try it without installing anything:

```
./gradlew :blossom:runServer
```

On the first run the server stops and asks you to accept Mojang's EULA. Write `eula=true` into
`blossom/run/eula.txt` and start it again.

## The shortest file that does something

The scripts of a world live in `<world>/blossom/`. A single `.bs` file is an app:

```
// <world>/blossom/greeter.bs
module greeter;

import "core.mc.text" as text;
import "core.mc.event" as event;

fn welcome(player) -> (
    text.send(player, 'welcome');
    false
);

event.on('player_connects', welcome);
print('greeter ready');
```

In game:

```
/blossom script load greeter
```

The trailing `print` appears immediately: the body of a module runs when the app is loaded, and that
is where handlers are registered. From that moment on, `welcome` fires on every connection.

Three things that file declares, none of them optional:

1. **`module greeter;`** on the first line, and the name must match the file's path. The loader
   checks this before executing anything.
2. **Every `import` carries an alias.** `import "core.mc.text" as text;`: leaving out `as` is an
   error. Two libraries can never fight over a name, because the name is chosen by you, in this
   file.
3. **Nothing is available by magic.** `sqrt(4)` without an import does not work: the error tells you
   which module `sqrt` lives in and which line to add.

## Trying code without saving a file

```
/blossom script run 2 + 3 * 4
/blossom script run import "core.math" as math; math.sqrt(16)
```

`run` evaluates in a clean interpreter that cannot see the loaded apps. To evaluate **inside** an
app, with its functions and its state in scope:

```
/blossom script in greeter welcome(entity.player())
```

## Every command

| Command | What it does |
| --- | --- |
| `/blossom script run <code>` | evaluates code on the fly, in a clean interpreter |
| `/blossom script in <app> <code>` | evaluates code inside a loaded app |
| `/blossom script load <app>` | loads `<world>/blossom/<app>.bs`, or the pack `<world>/blossom/<app>/` |
| `/blossom script reload <app>` | reloads an app that is already loaded |
| `/blossom script unload <app>` | unloads an app together with its event handlers |
| `/blossom script list` | what is loaded, and what is on disk |
| `/blossom script scan` · `fill` · `outline` | an expression over every block of an area: count, fill, draw the shell |
| `/blossom script in <app> invoke <fn> [arguments]` | calls a function of the app from the command line; `invokepoint` and `invokearea` pass it a position or an area |
| `/blossom script in <app> globals` | the functions and bindings of the app's main module |
| `/blossom script event` · `event add` · `event remove` | the handlers of every event, and attaching or detaching one by hand |
| `/blossom script explain` | how the parser reads an expression, a function or an app |
| `/blossom script stop` · `resume` | stops every script on the server, and lets them run again |
| `/blossom pack update <app>` | re-resolves dependencies and rewrites `blossom.lock` |
| `/blossom data generate <app>` | calls the app's `generate()` and writes the content into `data/` |
| `/blossom data list <app>` | what is in `data/`, and which of it is generated |
| `/blossom data export <app>` | makes it a zipped datapack that runs without Blossom too |
| `/blossom profile run <code>` | measures where the time goes, function by function |
| `/blossom profile in <app> <code>` | the same, inside a loaded app |
| `/blossom profile start <app>` · `stop <app>` | measures a live app, event handlers included |

The tools of `/blossom script` have [their own chapter](14-strumenti.md).

Reloading an app **removes its handlers before re-running the file**, so two copies of the same
listener are never attached to the same event. That is why registering handlers in the module body,
as in the example above, is the right thing to do.

## From a file to a project

As soon as the file grows, it becomes a directory with a manifest:

```
<world>/blossom/my-app/
├── blossom.json
└── src/
    ├── main.bs             ← module main
    └── ledger/
        └── register.bs     ← module ledger.register
```

```json
{
  "name": "my-app",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "2.0"
}
```

It loads with the same command: `/blossom script load my-app`. How to split modules, what they
export and how to depend on another project is [chapter 6](06-moduli.md).

## Where the data goes

| What | Where |
| --- | --- |
| the app's code | `<world>/blossom/<app>.bs` or `<world>/blossom/<app>/` |
| per-app data (the `data` root of [`core.io`](../api/core.io.md)) | `<world>/blossom/data/` |
| data shared between apps (the `shared` root) | `<world>/blossom/shared/` |
| dependencies downloaded from a registry | `<world>/blossom/.cache/` |
| the server configuration ([chapter 12](12-configurazione.md)) | `config/blossomscript.json` |

Everything a script can open lives under the world directory: the roots of
[`core.io`](../api/core.io.md) are declared in there and cannot escape it, and the rest of the
server's disk is unreachable from any configuration.

One app does not see another app's data by accident. To pass something along there are the
`shared_*` files and application events ([chapter 8](08-eventi.md)).
