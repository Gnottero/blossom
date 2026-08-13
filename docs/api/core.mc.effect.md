# `core.mc.effect`

Sounds and particles.

```
import "core.mc.effect" as effect;

effect.sound('entity.experience_orb.pickup', pos, 1, 1);
effect.particle('flame', pos, 20, 0.2, 0.01);
effect.particle_box('happy_villager', corner_a, corner_b, 1);
```

### `sound()` · `sound(name, pos, volume?, pitch?, category?)`
Without arguments, the available sounds. With the arguments, it plays a sound at a point for whoever
is close enough to hear it.

### `particle()` · `particle(name, pos, how_many?, spread?, speed?, player?)`
Without arguments, the available particles. With the arguments, it spawns some at a point - for a
single player, when one is named.

### `particle_line(name, from, to, density?, player?)`
A line of particles between two points. It returns how many it sent.

### `particle_box(name, corner_a, corner_b, density?, player?)`
The twelve edges of a box: it is the quick way to show the area a script is working on.

### `post_effect(players, action, effect?)`
The post-processing shaders of one or more players, that is, what the game calls `/posteffect`.

| Action | What it does | Returns |
| --- | --- | --- |
| `'add'` | adds the effect | how many players changed |
| `'remove'` | removes it | how many players changed |
| `'clear'` | removes them all | how many players changed |
| `'list'` | the list of active effects | a list of ids (a single player) |

```
effect.post_effect(player, 'add', 'minecraft:invert');
effect.post_effect(entity.player('all'), 'clear');
effect.post_effect(player, 'list');       // ['minecraft:invert']
```

It goes through the same packet as the vanilla command, so it **works on any client**, even without
Blossom installed, and the effect survives a trip out of the world and back. It has no duration: it
is removed explicitly. It is the only "on the client" function the mod can offer without a Blossom
client — everything else is vanilla that the server already knows how to command.
