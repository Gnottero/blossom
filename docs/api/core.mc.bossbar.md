# `core.mc.bossbar`

The bars at the top of the screen, the ones vanilla drives with `/bossbar`.

```
import "core.mc.bossbar" as bossbar;

bossbar.bar('mine:siege');                          // create it
bossbar.bar('mine:siege', 'color', 'red');
bossbar.bar('mine:siege', 'max', 100);
bossbar.bar('mine:siege', 'value', 40);             // 40%
bossbar.bar('mine:siege', 'players', players);      // who sees it
```

A bar is identified by a namespaced id, exactly as in the command. Bars created this way are the
server's own custom bars: they survive a reload of the app and are visible to `/bossbar list`, so an
app that creates one is responsible for removing it.

---

### `bossbar.bar()`
Every existing bar, as a list of ids.

### `bossbar.bar(name)`
Creates the bar with that id and returns the id. If a bar with that id already exists it returns
`false` and changes nothing, so this doubles as "create it unless it is there".

### `bossbar.bar(name, property)`
Reads a property. `null` when no bar has that id.

### `bossbar.bar(name, property, value)`
Writes a property and returns `true`.

| Property | Read | Write |
| --- | --- | --- |
| `name` | the displayed title | a string, or formatted text from [`core.mc.text`](core.mc.text.md) |
| `color` | `pink`, `blue`, `red`, `green`, `yellow`, `purple`, `white` | one of those names; an unknown one returns `null` and changes nothing |
| `style` | `progress`, `notched_6`, `notched_10`, `notched_12`, `notched_20` | one of those names; an unknown one is an error |
| `value` | the current value | a number; the fraction filled is `value / max` |
| `max` | the value that means "full" | a number |
| `visible` | whether it is shown | a truthy value |
| `players` | the list of players who can see it | a player, or a list of players; it **replaces** the audience rather than adding to it |
| `add_player` | (write-only) | adds one player to the audience |
| `remove` | (write-only) | deletes the bar; the value argument is ignored |

An unknown property name is an error, not a silent no-op.

```
bossbar.bar('mine:siege', 'style', 'notched_10');
bossbar.bar('mine:siege', 'value');                 // 40
bossbar.bar('mine:siege', 'add_player', newcomer);
bossbar.bar('mine:siege', 'remove');                // when the siege is over
```

---

## Coming from `core.mc.scoreboard`

| Before | Now |
| --- | --- |
| `bossbar(...)` | `bar(...)` |
