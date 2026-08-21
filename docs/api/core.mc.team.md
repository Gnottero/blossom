# `core.mc.team`

Teams.

```
import "core.mc.team" as team;

team.add('rossi');
team.add('rossi', player);
team.property('rossi', 'color', 'red');
```

### `list()` · `list(team)`
The teams, or the members of a team.

### `add(team)` · `add(team, who)` · `remove(team)` · `leave(who)`
Creates a team, adds somebody to it, disbands it, or makes somebody leave the one they are in.

### `property(team, property, value?)`
Reads or writes the properties of a team: `collisionRule`, `color`, `displayName`, `friendlyFire`,
`nametagVisibility`, `deathMessageVisibility`, `prefix`, `suffix`, `seeFriendlyInvisibles`.

---

## Coming from `core.mc.scoreboard`

| Before | Now |
| --- | --- |
| `team_list()` | `list()` |
| `team_add(t)` | `add(t)` |
| `team_remove(t)` | `remove(t)` |
| `team_leave(who)` | `leave(who)` |
| `team_property(t, p, v?)` | `property(t, p, v?)` |
