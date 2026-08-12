# `core.mc.effect`

Suoni e particelle.

```
import "core.mc.effect" as effect;

effect.sound('entity.experience_orb.pickup', pos, 1, 1);
effect.particle('flame', pos, 20, 0.2, 0.01);
effect.particle_box('happy_villager', angolo_a, angolo_b, 1);
```

### `sound()` · `sound(nome, pos, volume?, tono?, categoria?)`
Senza argomenti, i suoni disponibili. Con gli argomenti, riproduce un suono in un punto per chi è
abbastanza vicino da sentirlo.

### `particle()` · `particle(nome, pos, quante?, dispersione?, velocità?, giocatore?)`
Senza argomenti, le particelle disponibili. Con gli argomenti, ne genera in un punto - a un solo
giocatore, se lo si indica.

### `particle_line(nome, da, a, densità?, giocatore?)`
Una linea di particelle fra due punti. Restituisce quante ne ha mandate.

### `particle_box(nome, angolo_a, angolo_b, densità?, giocatore?)`
Le dodici spigole di un parallelepipedo: è il modo veloce di far vedere l'area su cui uno script sta
lavorando.

### `post_effect(giocatori, azione, effetto?)`
I post-processing shader di uno o più giocatori, cioè quello che il gioco chiama `/posteffect`.

| Azione | Cosa fa | Restituisce |
| --- | --- | --- |
| `'add'` | aggiunge l'effetto | quanti giocatori sono cambiati |
| `'remove'` | lo toglie | quanti giocatori sono cambiati |
| `'clear'` | li toglie tutti | quanti giocatori sono cambiati |
| `'list'` | l'elenco degli effetti attivi | una lista di id (un giocatore solo) |

```
effect.post_effect(giocatore, 'add', 'minecraft:invert');
effect.post_effect(entity.player('all'), 'clear');
effect.post_effect(giocatore, 'list');       // ['minecraft:invert']
```

Passa dallo stesso pacchetto del comando vanilla, quindi **funziona su qualunque client**, anche
senza Blossom installato, e l'effetto resta anche dopo un rientro nel mondo. Non ha durata: si toglie
esplicitamente. È l'unica funzione "sul client" che il mod può offrire senza un client Blossom —
tutto il resto è vanilla che il server già sa comandare.
