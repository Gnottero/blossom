# `core.mc.effect`

Sounds and particles.

```
import "core.mc.effect" as effect;

effect.sound('entity.experience_orb.pickup', pos, 1, 1);
effect.particle('flame', pos, 20, 0.2, 0.01);
effect.particle_box('happy_villager', corner_a, corner_b, 1);
```

### `effect.sound()`
The list of every registered sound event name.

### `effect.sound(name, pos, volume?, pitch?, category?)`
Plays a sound at a point, for every player close enough to hear it, and returns how many players it
reached. An unknown sound name throws.

| Argument | Meaning | Default |
| --- | --- | --- |
| `name` | a sound event id, as in `/playsound` | required |
| `pos` | where it comes from | required |
| `volume` | also sets how far it carries: above 1 it is audible further away | `1.0` |
| `pitch` | 0.5 is an octave down, 2.0 an octave up | `1.0` |
| `category` | the mixer it obeys: `master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient`, `voice` | `master` |

An unknown category name is an error that lists the valid ones.

```
effect.sound('entity.experience_orb.pickup', pos);
effect.sound('block.note_block.bell', pos, 1, 1.5, 'record');
```

### `effect.particle()`
The list of every registered particle type name.

### `effect.particle(name, pos, count?, spread?, speed?, player?)`
Spawns particles at a point, for everyone or for a single named player.

| Argument | Meaning | Default |
| --- | --- | --- |
| `name` | a particle id, optionally with its parameters (`'dust 1 0 0 1'`) | required |
| `pos` | the centre | required |
| `count` | how many | `10` |
| `spread` | the radius they scatter within | `0.5` |
| `speed` | their initial speed | `0` |
| `player` | the name of one player to show them to | everyone nearby |

A count of `0` is the vanilla trick for a single particle moving with exactly the given velocity,
rather than a cloud.

```
effect.particle('flame', pos, 20, 0.2, 0.01);
effect.particle('happy_villager', pos, 5, 0.5, 0, 'ada');
```

### `effect.particle_line(name, from, to, density?, player?)`
Draws a line of particles between two points and returns how many it sent. `density` is the spacing
between them, defaulting to 1, and must be positive.

### `effect.particle_box(name, corner_a, corner_b, density?, player?)`
The twelve edges of the box between the two corners: the quick way to show the area a script is
working on. Same `density` rules.

```
effect.particle_box('happy_villager', corner_a, corner_b, 1);
effect.particle_line('flame', from, to, 0.5, 'ada');
```

For shapes that are drawn rather than approximated with particles, there is
[`core.mc.shape`](core.mc.shape.md), which falls back to these when the viewer has no Blossom
client.

### `effect.post(players, action, effect?)`
The post-processing shaders of one or more players, that is, what the game calls `/posteffect`.

| Action | What it does | Returns |
| --- | --- | --- |
| `'add'` | adds the effect | how many players changed |
| `'remove'` | removes it | how many players changed |
| `'clear'` | removes them all | how many players changed |
| `'list'` | the list of active effects | a list of ids (a single player) |

```
effect.post(player, 'add', 'minecraft:invert');
effect.post(entity.player('all'), 'clear');
effect.post(player, 'list');       // ['minecraft:invert']
```

It goes through the same packet as the vanilla command, so it **works on any client**, even without
Blossom installed, and the effect survives a trip out of the world and back. It has no duration: it
is removed explicitly. It is the only "on the client" function the mod can offer without a Blossom
client: everything else is vanilla that the server already knows how to command.
