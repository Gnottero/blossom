# `core.mc.system`

The server seen from outside: commands, statistics, diagnostics.

```
import "core.mc.system" as system;

system.run('say hello');
system.info('server_mods');
```

### `system.run(command)`
Runs a vanilla command with the permissions of the app and collects its outcome, instead of printing
it in chat. It returns `[code, output]`.

### `system.info()` · `system.info(key)`
Information about the server and the process: memory, threads, version, loaded mods, uptime, loaded
worlds, loaded apps. Without arguments it lists the available keys.

### `system.mob_counts()` · `system.mob_counts(category)`
How many mobs of each category there are, and how many the cap would allow.

### `system.statistic(player, category, entry)`
A statistic of a player (`'used'`, `'mined'`, `'killed'`, `'custom'`, ...).

### `system.logger(message)` · `system.logger(level, message)`
Writes to the log of the server. The levels are `'error'`, `'warn'`, `'info'`, `'debug'`.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `system_info(k?)` | `info(k?)` |
| `get_mob_counts(c?)` | `mob_counts(c?)` |
| `task_join(t)` | it lives in [`core.task`](core.task.md), and is called `wait` |
