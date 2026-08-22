# `core.mc.text`

Talking to the people playing.

```
import "core.mc.text" as text;

text.send(player, 'hello');
text.send(entity.player('all'), 'hello everyone');
text.send(player, text.format('rb watch out', 'w  something went wrong'));
text.title(player, 'title', text.format('gb Level 2'));
```

### `text.send(message)` · `text.send(target, message)`
Writes a message: to whoever launched the script, or to a given target. The target is a player or a
**list of players**, so the list `entity.player('all')` returns goes in as it is, with no loop around
it. It returns the message, so it can be slipped in the middle of an expression without breaking it.

### `text.format(pieces...)`
Formatted text. Every piece is `'style text'`, where the style is made of letters:

| | | | |
| --- | --- | --- | --- |
| `w` white | `y` yellow | `m` magenta | `r` red |
| `c` cyan | `l` light green | `t` blue | `f` dark grey |
| `g` grey | `d` gold | `p` purple | `n` dark red |
| `q` dark cyan | `e` dark green | `v` dark blue | `k` black |
| `i` italic | `b` bold | `u` underlined | `s` struck through |
| `o` obfuscated | `#rrggbb` exact colour | | |

A piece starting with a symbol attaches an action to the previous piece: `^` text on hover, `!` runs
a command, `?` writes it in chat, `@` opens an address, `&` copies to the clipboard.

### `text.title(targets, type, text?, fade_in?, stay?, fade_out?)`
Writes big on the screen. The types are `'title'`, `'subtitle'`, `'actionbar'`,
`'player_list_header'`, `'player_list_footer'`, and `'clear'`/`'reset'` to remove.

### `text.hud(players, text, duration?)` · `text.hud(player)`
A line of text above the hotbar.

```
text.hud(player, 'ores found: ' + found);              // stays until you change it
text.hud(player, 'unstable area', 60);                 // stays 3 seconds, then goes on its own
text.hud(entity.player('all'), text.format('y watch out'));
text.hud(player, null);                                // gone now
text.hud(player);                                      // what they are seeing, or null
```

`duration` is in ticks. **Without it, the line stays**: there is no hidden default duration. They are
two different uses and both are normal (a counter keeps the player company, a warning has to leave
on its own) and an implicit duration would have broken the first one. The expiry is checked every
tick, so "stays 5 ticks" stays 5 ticks.

A duration of zero or less is an error, not "disappears immediately": whoever writes it has almost
always miscomputed an interval, and making it disappear silently would hide the wrong arithmetic. To
remove the line there is `hud(player, null)`, which says so.

It takes the same text as `format`, so colours and styles work. It returns how many players were
touched.

**How it works, and what it implies.** The game has no packet saying "keep this text there": the
title and the action bar have a duration and then fade. This function resends the action bar before
it fades, so the line stays, and since it is all vanilla, it **works on any client**, with nothing
installed. The text goes out immediately when you change it, and is resent half a second at a time
for as long as it has to stay: one packet every ten ticks per player, not one per tick.

Three consequences, stated:

- **there is only one line.** It is the space of the action bar, and that is that.
- **it shares the space with everything else.** The item name appearing when changing hotbar slot, a
  `title(..., 'actionbar', ...)`, another mod: the HUD line overwrites them within half a
  second. That is what a HUD needs, but it is worth knowing.
- **the position is not up for choice.** Above the hotbar, full stop.

Something without those limits would need code on the client, that is, a function that stops existing
for whoever plays vanilla. See `DECISIONI.md` D-073.
