# `core.mc.score`

Scoreboard objectives and the scores kept in them.

```
import "core.mc.score" as score;

score.add('points');                    // create the objective
score.score('points', player, 10);      // set a score
score.score('points', player);          // read it back
score.display('sidebar', 'points');     // show it
```

Objectives are the server's own, the same ones `/scoreboard` manages: they outlive the app that
created them, and are visible to commands and to datapacks. An app that creates one is responsible
for removing it.

A score holder is a player value, or any string: the fake player names that command-driven systems
use work exactly the same way here.

---

### `score.score()`
The names of every objective.

### `score.score(objective)`
The score holders that have a score in that objective, as a list of names. `null` when the objective
does not exist.

### `score.score(objective, who)`
The score of that holder, or `null` when they have none.

### `score.score(objective, who, value)`
Sets the score and returns the previous one.

```
score.score();                              // ['points', 'deaths']
score.score('points');                      // ['ada', 'grace']
score.score('points', player);              // 10
score.score('points', player, 12);          // 10, and now it is 12
```

### `score.add(objective)` · `score.add(objective, criterion)`
Creates an objective and returns its name, or `false` when one with that name already exists. The
criterion defaults to `dummy`, the one that only scripts and commands write to. Any vanilla
criterion works (`deathCount`, `playerKillCount`, `minecraft.used:minecraft.carrot`, …); an unknown
one throws `unknown_criterion`.

### `score.remove(objective)`
Deletes the objective and every score in it. Returns `true`, or `false` when there was nothing to
delete.

### `score.remove(objective, who)`
Removes that holder's score, leaving the objective in place.

### `score.property(objective, property)` · `score.property(objective, property, value)`
Reads or writes a property of an objective. `null` when the objective does not exist.

| Property | What it is |
| --- | --- |
| `display_name` | the title shown in the sidebar; accepts formatted text |
| `criterion` | what the game increments on its own |
| `render_type` | `integer` or `hearts` |

### `score.display(slot, objective)`
Shows an objective in a display slot, or clears the slot when the objective is `null`. Both
arguments are required. Returns the slot's name, or `null` when no objective by that name exists.

The slots are `sidebar`, `list` (the tab menu), `below_name`, and `sidebar.team.<colour>` for a
per-team sidebar. An invalid slot name is an error.

```
score.display('sidebar', 'points');
score.display('sidebar', null);         // cleared
```

---

## Coming from `core.mc.scoreboard`

That module held three different things and is gone. Teams are in
[`core.mc.team`](core.mc.team.md), boss bars in [`core.mc.bossbar`](core.mc.bossbar.md).

| Before | Now |
| --- | --- |
| `scoreboard(...)` | `score(...)` |
| `scoreboard_add(o, c?)` | `add(o, c?)` |
| `scoreboard_remove(o)` | `remove(o)` |
| `scoreboard_property(o, p, v?)` | `property(o, p, v?)` |
| `scoreboard_display(s, o)` | `display(s, o)` |
