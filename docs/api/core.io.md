# `core.io`

The disk outside the folder of the app, inside boundaries an administrator declares.

```
import "core.io" as io;

io.write_text('shared', 'notes/today.txt', 'hello');
io.read_text('shared', 'notes/today.txt');       // 'hello'
```

For the data of the app there is already [`core.mc.file`](core.mc.file.md), and for almost everything
that is enough: every app has its own folder and no other app sees it. `core.io` is for the case that
does not cover — talking to something that is already on the server: a folder shared between apps,
the logs of another tool, an exchange folder with an external process.

## The boundaries

There are no absolute paths in scripts. An administrator declares **roots** with a name, in
`config/blossomscript.json`:

```json
{
  "io": {
    "roots": {
      "shared": "/srv/blossom-shared",
      "log": "/var/log/my-server"
    },
    "writable": ["shared"]
  }
}
```

Every function starts with the name of the root, and the rest of the path is always relative to it.
Three consequences, all of them intended:

- **without a declaration you get nowhere.** `core.io` imported on a server that configured nothing
  exists but opens nothing, and says so.
- **the root is visible at the call site.** Reading the code is enough to know where the data ends
  up; there is no path built three functions further away.
- **writing is a separate permission.** A root is read-only unless it appears in `writable`.

A path that leaves the root is an error, and not only when it tries with `..`: the path is really
resolved before the comparison, so a symbolic link placed inside the root pointing outside is
refused in the same way.

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

`list` is not recursive and `delete_file` does not delete trees. Those are the two places where one
more convenience is paid for with an accident: a recursion over a big root blocks the tick without
the author noticing, and a recursive deletion behind a short name is the easiest way to lose data to
a path that is wrong by one character. Whoever wants to walk down or delete a tree writes it one
level at a time — and while writing it, sees it.

It is called `delete_file` and not `delete` because `delete` is already the one that removes an
element from a list or a map.

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
