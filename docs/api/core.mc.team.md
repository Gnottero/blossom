# `core.mc.team`

Scoreboard teams: who is on them, and how the game treats their members.

```
import "core.mc.team" as team;

team.add('reds');
team.add('reds', player);
team.property('reds', 'color', 'red');
```

Teams are the server's own, the same ones `/team` manages, and they outlive the app that created
them. A player belongs to at most one team at a time.

---

### `team.list()`
The names of every team.

### `team.list(team)`
The members of a team, as a list of names. `null` when the team does not exist.

### `team.add(team)`
Creates the team and returns its name, or `null` when one with that name already exists.

### `team.add(team, who)`
Puts a player on the team, taking them off whichever team they were on. Returns `true`, or `null`
when the team or the player cannot be found.

### `team.remove(team)`
Disbands the team. Its members end up on no team.

### `team.leave(who)`
Takes a player off whatever team they are on, without naming it. Returns whether they were on one.

### `team.property(team, property)` · `team.property(team, property, value)`
Reads or writes a property. `null` when the team does not exist.

| Property | What it is |
| --- | --- |
| `color` | the team colour, which also colours member names |
| `displayName` | the name shown instead of the team's id; accepts formatted text |
| `prefix` · `suffix` | text put before and after every member's name |
| `friendlyFire` | whether members can hurt each other |
| `seeFriendlyInvisibles` | whether members see each other while invisible |
| `nametagVisibility` | `always`, `never`, `hideForOtherTeams`, `hideForOwnTeam` |
| `deathMessageVisibility` | the same four values, for death messages |
| `collisionRule` | `always`, `never`, `pushOtherTeams`, `pushOwnTeam` |

```
team.property('reds', 'friendlyFire', false);
team.property('reds', 'prefix', '[R] ');
team.property('reds', 'color');             // 'red'
```

---

## Coming from `core.mc.scoreboard`

| Before | Now |
| --- | --- |
| `team_list()` | `list()` |
| `team_add(t)` | `add(t)` |
| `team_remove(t)` | `remove(t)` |
| `team_leave(who)` | `leave(who)` |
| `team_property(t, p, v?)` | `property(t, p, v?)` |
