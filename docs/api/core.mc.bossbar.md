# `core.mc.bossbar`

The bars at the top of the screen.

```
import "core.mc.bossbar" as bossbar;

bossbar.bar('assedio');
bossbar.bar('assedio', 'color', 'red');
bossbar.bar('assedio', 'value', 40);
```

### `bar()` · `bar(name)` · `bar(name, property, value?)`
The existing bars, the creation of a bar, and its properties: `name`, `color`, `style`, `value`,
`max`, `visible`, `players`, `add_player`, `remove`.

---

## Coming from `core.mc.scoreboard`

| Before | Now |
| --- | --- |
| `bossbar(...)` | `bar(...)` |
