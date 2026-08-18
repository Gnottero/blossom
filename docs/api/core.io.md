# `core.io`

Files, inside boundaries an administrator declares.

```
import "core.io" as io;

io.write_text('data', 'my-app/notes/today.txt', 'hello');
io.read_text('data', 'my-app/notes/today.txt');       // 'hello'
```

This is **the** file API of the language: everything a script can open, it opens from here. There
used to be a second one, `core.mc.file`, which could only reach one folder per app,
`<world>/blossom/<app>.data/` — two ways of opening a file are one question too many at every call,
and both the module and the folder are gone.

Three of the functions below — `read_nbt`, `write_nbt`, `create_datapack` — are implemented in the
Minecraft layer, because NBT is a binary format of the game and a datapack is a concept of the game.
That is an implementation detail: they are imported from `core.io` like all the others, they take the
same `(root, path)` arguments, and they obey the same boundaries.

## The boundaries

There are no absolute paths in scripts. Every function starts with the name of a **root**, and the
rest of the path is relative to it.

Two roots are always there, without anybody declaring them:

| Root | Where | Writable |
| --- | --- | --- |
| `data` | `<world>/blossom/data/` | yes |
| `shared` | `<world>/blossom/shared/` | yes |

That is where an app keeps its data. Nothing separates one app from another automatically: an app
that wants its own data to itself uses a subfolder named after it —
`io.write_json('data', 'my-app/scores.json', ...)`. That is exactly what `<app>.data` did, except it
is now written at the call site instead of being decided by the mod, and an app that *wants* to share
a folder with another one can just say so.

An administrator adds more roots in `config/blossomscript.json`:

```json
{
  "io": {
    "roots": {
      "logs": "blossom/logs",
      "exchange": "../exchange"
    },
    "writable": ["exchange"]
  }
}
```

Four consequences, all of them intended:

- **the roots live in the world folder.** Their paths are written relative to it; an absolute path is
  accepted only when it points inside the world anyway, and is dropped with a warning in the log
  otherwise. The rest of the disk of the server is not reachable by any configuration — the
  `"../exchange"` above is refused for exactly that reason.
- **beyond the two defaults, you get nowhere without a declaration.** A root nobody declared does not
  open, and says so with the closest declared name as a suggestion.
- **the root is visible at the call site.** Reading the code is enough to know where the data ends
  up; there is no path built three functions further away.
- **writing is a separate permission.** A root an administrator adds is read-only unless it appears
  in `writable`.

A path that leaves the root is an error, and not only when it tries with `..`: the path is really
resolved before the comparison, so a symbolic link placed inside the root pointing outside — or a
root that is itself a link out of the world folder — is refused in the same way.

```
RuntimeError[io]
  Path '../secret.txt' escapes root 'data'.
```

## The functions

| Function | What it does |
| --- | --- |
| `roots()` | the names of the configured roots |
| `writable(root)` | whether it can be written to |
| `exists(root, path)` | whether the file or folder is there |
| `is_directory(root, path)` | whether it is a folder |
| `list(root, path)` | the names inside a folder, sorted |
| `read_text(root, path)` | the whole content as a string |
| `read_lines(root, path)` | a list of lines |
| `read_json(root, path)` | the content read as json |
| `write_text(root, path, text)` | writes, creating the missing folders |
| `append_text(root, path, text)` | appends at the end |
| `write_json(root, path, value)` | writes the value as json |
| `make_directory(root, path)` | creates a folder and its intermediate ones |
| `delete_file(root, path)` | deletes a file or an **empty** folder |
| `size(root, path)` | size in bytes, or `null` when it does not exist |
| `modified(root, path)` | last modification in milliseconds, or `null` |
| `read_nbt(root, path)` | a compressed NBT file, or `null` when it is not there |
| `write_nbt(root, path, tag)` | writes a compound tag, compressed |
| `create_datapack(name, content)` | builds a datapack from a map of files, and turns it on |

`list` is not recursive and `delete_file` does not delete trees. Those are the two places where one
more convenience is paid for with an accident: a recursion over a big root blocks the tick without
the author noticing, and a recursive deletion behind a short name is the easiest way to lose data to
a path that is wrong by one character. Whoever wants to walk down or delete a tree writes it one
level at a time — and while writing it, sees it.

It is called `delete_file` and not `delete` because `delete` is already the one that removes an
element from a list or a map.

A file that is not there is an `io_exception`, not a `null`: `read_text`, `read_lines` and
`read_json` all fail on it. The two ways of saying "if it is there":

```
state = if(io.exists('data', 'my-app/state.json'), io.read_json('data', 'my-app/state.json'), {});

state = try(io.read_json('data', 'my-app/state.json'), 'io_exception', {});
```

### NBT

`read_nbt(root, path)` reads a gzip-compressed NBT file — the format of the game's own `.dat` files —
and gives back an `nbt` value, or `null` when the file is not there. It is the one reader that does
**not** throw on a missing file, because an NBT state file that does not exist yet is the normal
first run:

```
state = io.read_nbt('data', 'my-app/state.nbt');
if(state == null, state = nbt('{}'));
```

`write_nbt(root, path, tag)` writes a compound tag, compressed, creating the missing folders. It
needs a **compound** tag: a list or a bare number is not a valid NBT file, and refusing it at the call
beats writing something no reader will accept.

```
io.write_nbt('data', 'my-app/state.nbt', {'score' -> 12, 'names' -> ['ada', 'grace']});
```

Use NBT when the data is already NBT — an entity's tag, a block entity, a structure. For anything a
script made up itself, json is the format to read in a text editor when something goes wrong.

### Datapacks

`create_datapack(name, content)` builds a datapack from `content` — a map of folders and files,
nested — writes it as a zip in the world's `datapacks/`, and enables it. It returns `null` if a pack
with that name is already there.

```
io.create_datapack('my-loot', {
    'data' -> {'my-app' -> {'loot_table' -> {'thing.json' -> {'type' -> 'minecraft:chest'}}}}
});
```

Maps become json, lists of strings become text files. Enabling the pack reloads the server's
resources, so this is a call for the loading of an app, not for something inside `tick`.

It is here and not in a module of its own because a datapack is a tree of files: writing one is
writing files, and the only thing that makes it special is that the game is told about it afterwards.

## Coming from `core.mc.file`

That module is gone. Every one of its functions has an equivalent here:

| Before | Now |
| --- | --- |
| `file.read_file('scores', 'json')` | `io.read_json('data', 'my-app/scores.json')` |
| `file.read_file('notes', 'text')` | `io.read_lines('data', 'my-app/notes.txt')` |
| `file.read_file('state', 'nbt')` | `io.read_nbt('data', 'my-app/state.nbt')` |
| `file.write_file('scores', 'json', v)` | `io.write_json('data', 'my-app/scores.json', v)` |
| `file.write_file('audit', 'text', l)` | `io.append_text('data', 'my-app/audit.txt', l)` |
| `file.write_file('state', 'nbt', t)` | `io.write_nbt('data', 'my-app/state.nbt', t)` |
| `file.read_file('x', 'shared_json')` | `io.read_json('shared', 'x.json')` |
| `file.list_files('notes', 'text')` | `io.list('data', 'my-app/notes')` |
| `file.delete_file('scores', 'json')` | `io.delete_file('data', 'my-app/scores.json')` |
| `file.load_app_data()` | `io.read_nbt('data', 'my-app/state.nbt')` |
| `file.store_app_data(tag)` | `io.write_nbt('data', 'my-app/state.nbt', tag)` |
| `file.create_datapack(n, c)` | `io.create_datapack(n, c)` |

Three differences worth knowing before the rewrite:

- **the root is an argument.** There is no per-app folder any more: you say `data` (or `shared`, or
  one an administrator declared) and where the file goes is readable at the call.
- **a missing file is an error, not a `null`.** `read_file` gave back `null`; `read_text`,
  `read_lines` and `read_json` throw `io_exception`. Guard with `io.exists`, or catch. `read_nbt` is
  the exception and still gives `null`.
- **the extension is part of the path.** `read_file` appended one from the type; here you write
  `state.nbt` yourself, and what is on disk is what is in the code.

## Errors

Disk errors are `io_exception`, so they can be caught:

```
try(
    io.read_text('data', 'missing.txt'),
    'io_exception', 'the file is not there'
);
```

Boundary violations, on the other hand, **cannot** be caught: an unknown root, a path that escapes, a
write on a read-only root are errors of the program, not conditions to handle.
