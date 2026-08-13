# `core.mc.scoreboard`

Objectives, teams and boss bars.

```
import "core.mc.scoreboard" as score;

score.scoreboard_add('points');
score.scoreboard('points', player, 10);
score.scoreboard_display('sidebar', 'points');
```

### `scoreboard()` · `scoreboard(objective)` · `scoreboard(objective, who)` · `scoreboard(objective, who, value)`
The existing objectives, the scores of an objective, the score of one, or the assignment of a score.

### `scoreboard_add(objective, criterion?)` · `scoreboard_remove(objective)` · `scoreboard_remove(objective, who)`
Creates an objective, deletes it, or removes the score of a single one.

### `scoreboard_property(objective, property, value?)`
Reads or writes the properties of an objective: `display_name`, `criterion`, `render_type`.

### `scoreboard_display(slot, objective?)`
Shows an objective in a slot (`'sidebar'`, `'list'`, `'belowName'`, or a team colour).

### `team_list()` · `team_list(team)`
The teams, or the members of a team.

### `team_add(team)` · `team_add(team, who)` · `team_remove(team)` · `team_leave(who)`
Creates a team, adds somebody to it, disbands it, or makes somebody leave the one they are in.

### `team_property(team, property, value?)`
Reads or writes the properties of a team: `collisionRule`, `color`, `displayName`, `friendlyFire`,
`nametagVisibility`, `deathMessageVisibility`, `prefix`, `suffix`, `seeFriendlyInvisibles`.

### `bossbar()` · `bossbar(name)` · `bossbar(name, property, value?)`
The existing bars, the creation of a bar, and its properties: `name`, `color`, `style`, `value`,
`max`, `visible`, `players`, `add_player`, `remove`.
