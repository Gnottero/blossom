# 12. Server configuration

Everything an administrator decides, and that an app cannot decide for itself, lives in a single
file: `config/blossomscript.json`, next to the other mods' configuration files.

The file is **optional**. Without it the mod starts with the defaults: no registry, no directory
reachable from `core.io`, no host reachable from `core.http`, standard watchdog. A file that is
unreadable or malformed does not prevent startup: it is reported in the log and the defaults are
used, because a mod that refuses to load over one comma too many breaks a server over something it
was not even using.

The file is read **when the world is opened**. Changing it with the server running has no effect: it
takes a restart (in singleplayer, reopening the world).

## The complete file

```json
{
  "root": "/srv/blossom-scripts",

  "registry": { "directory": "/srv/blossom-packs" },

  "io": {
    "roots": { "shared_data": "shared_data", "log": "blossom/log" },
    "writable": ["shared_data"]
  },

  "http": {
    "hosts": ["api.example.org", "*.status.example.org"],
    "schemes": ["https"],
    "timeout_ms": 5000,
    "max_response_bytes": 1048576,
    "follow_redirects": false
  },

  "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
}
```

Every section is independent, and every missing section turns its own part off. `root` and the four
sections are all there is: a key that is not one of these is ignored.

## `root`: where apps are loaded from

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `root` | string | `<world>/blossom` | the directory the mod looks in for apps and packs |

Without this key, apps live in `<world>/blossom/`, and every world has its own. Declaring it makes
every world on that server look where you say.

A **relative** path resolves inside the world directory (`"scripts"` → `<world>/scripts/`); an
**absolute** one is taken as it is, even outside the world (a script directory shared between
several worlds is the case this key exists for). This is unlike `io.roots`, which cannot leave the
world: there the boundary protects against scripts, here the choice belongs to whoever administers
the server and writes this file.

The directory is created if it is missing. Changing it moves nothing: apps that were in the old
directory stay there and are simply no longer seen.

## `registry`: where dependencies come from

Off by default. Two mutually exclusive forms; if both are present, `directory` wins.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `registry.directory` | string | n/a | a directory `<root>/<name>/<version>/`, with an ordinary project inside |
| `registry.url` | string | n/a | a static `index.json` and some zip files, served over http |

A `registry` that names neither `directory` nor `url` is ignored with a warning in the log. For the
shape of the index, and for why `sha256` is mandatory, see
[6. Modules and projects](06-modules.md).

The download cache lives in `.cache/` inside the script directory, so it follows `root`.

## `io`: the directories reachable from `core.io`

Two roots always exist, even without this section:

| Root | Where | Writable |
| --- | --- | --- |
| `data` | `<world>/blossom/data/` | yes |
| `shared` | `<world>/blossom/shared/` | yes |

They are where an app keeps its own data. There is no automatic separation between one app and
another: an app that wants its data to itself uses a subdirectory named after itself
(`io.write_json('data', 'pedometer/counts.json', ...)`), which is visible at the call site instead
of being decided by the mod. The directories are created on first startup.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `io.roots` | map of name → path | `data`, `shared` | the reachable directories, one per name |
| `io.writable` | list of names | `data`, `shared` | which of those roots may also be written |

What you declare here is **added** to the two defaults; a root declared with the same name as one of
them replaces it.

**Paths are relative to the world directory.** An absolute path is valid only if it still points
inside the world; if it points outside, the root is discarded with a warning in the log and its
calls fail as though it had never been declared. Outside the world directory lies the rest of the
server's disk, and no configuration should be able to open that to a script.

The boundary applies to symbolic links as well: a root that is a link pointing outside the world, or
a link inside a root that points outside, is refused at call time.

A name that appears in `writable` but not in `roots` is almost always a typo and is reported. The
opposite (declaring a directory and forgetting to make it writable) announces itself on the first
write.

The functions are in [`core.io`](../api/core.io.md).

## `http`: the hosts reachable from `core.http`

No host by default: `core.http` exists but reaches nothing.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `http.hosts` | list of strings | `[]` | the allowed patterns: exact name, `*.domain`, or `*` for everything |
| `http.schemes` | list of strings | `["https"]` | the permitted schemes |
| `http.timeout_ms` | number | `5000` | how long a response is awaited before failing |
| `http.max_response_bytes` | number | `1048576` | past this size the body is refused rather than read |
| `http.follow_redirects` | boolean | `false` | whether to follow 3xx, re-checking the host at every hop |

`*` among the hosts and `http` among the schemes are permitted but reported in the log: the first
turns every installed app into a channel to any server on the internet, the second sends requests in
the clear.

Redirects, when enabled, stop after five hops and every hop goes through the allowlist again: a
`302` cannot carry a script outside the declared hosts.

The functions are in [`core.http`](../api/core.http.md).

## `watchdog`: the execution limits

These apply to apps that do not declare limits of their own in their `blossom.json` (see
[9. Concurrency](09-concurrency.md)).

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `watchdog.timeout_ms` | number | `2000` | how long a block of code may run before being stopped |
| `watchdog.max_depth` | number | `256` | how deep recursion may go |

A non-positive `timeout_ms` removes the time limit: it is only useful to someone who is profiling,
and it is reported in the log because on a server it is the way to make a tick disappear without
understanding why. Code launched by hand from a command gets a wider threshold anyway: whoever
pressed enter is waiting, and no game event is hanging on it.

## What is **not** configured here

- **an app's manifest**: `blossom.json`, inside the app's directory: dependencies, entrypoint,
  `autoload`, its own limits. That is the app describing itself, not the server governing it.
- **which apps start on their own**: that is `"autoload": true` in the app's manifest.
- **command permissions**: those are Minecraft's, by operator level.
