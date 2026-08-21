# `core.mc.score`

Objectives and scores.

```
import "core.mc.score" as score;

score.add('points');
score.score('points', player, 10);
score.display('sidebar', 'points');
```

### `score()` · `score(objective)` · `score(objective, who)` · `score(objective, who, value)`
The existing objectives, the scores of an objective, the score of one, or the assignment of a score.

### `add(objective, criterion?)` · `remove(objective)` · `remove(objective, who)`
Creates an objective, deletes it, or removes the score of a single one.

### `property(objective, property, value?)`
Reads or writes the properties of an objective: `display_name`, `criterion`, `render_type`.

### `display(slot, objective?)`
Shows an objective in a slot (`'sidebar'`, `'list'`, `'belowName'`, or a team colour).

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
| `scoreboard_display(s, o?)` | `display(s, o?)` |
