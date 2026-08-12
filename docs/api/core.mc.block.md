# `core.mc.block`

Leggere e scrivere i blocchi del mondo.

```
import "core.mc.block" as block;

block.block([10, 64, 10]);                             // il blocco in quel punto
block.set([10, 65, 10], 'oak_stairs', 'facing', 'north');
block.block_state([10, 65, 10]);                       // {'facing' -> 'north', ...}
```

Una posizione si scrive come lista `[x, y, z]` o come tre argomenti sciolti: le due forme sono
equivalenti ovunque nel layer di gioco.

Un valore blocco stampato è il suo nome (`'minecraft:stone'`), quindi si può confrontare, mettere in
una mappa e salvare in json senza convertirlo.

## Leggere

### `block(pos)`
Il blocco in quella posizione. Confrontato con una stringa risponde sul nome.

### `block_state(pos)` · `block_state(pos, proprietà)`
Le proprietà del blocco come mappa, o il valore di una sola proprietà.

### `block_properties(pos)`
I nomi delle proprietà che quel blocco ha.

### `property(pos, nome)`
Il valore di una proprietà, `null` se il blocco non ce l'ha.

### `block_data(pos)`
Il NBT del block entity, `null` se quel blocco non ne ha uno.

### `block_tags(pos)` · `block_tags(pos, tag)`
I tag a cui il blocco appartiene, o se appartiene a quello dato.

### `block_list()` · `block_list(tag)`
Tutti i blocchi registrati, o quelli di un tag.

## Proprietà fisiche

### `solid(pos)` · `air(pos)` · `liquid(pos)`
Se il blocco è solido, aria, o un liquido.

### `flammable(pos)` · `transparent(pos)`
Se brucia e se lascia passare la luce.

### `blocks_movement(pos)` · `suffocates(pos)`
Se ferma il movimento, e se soffoca chi ci sta dentro.

### `hardness(pos)` · `blast_resistance(pos)`
Quanto è duro da rompere e quanto resiste alle esplosioni.

### `block_sound(pos)` · `material(pos)` · `map_colour(pos)`
Gruppo di suoni, materiale e colore sulla mappa.

### `ticks_randomly(pos)` · `power(pos)`
Se riceve tick casuali, e il segnale di redstone che emette.

## Luce

### `light(pos)` · `block_light(pos)` · `sky_light(pos)` · `effective_light(pos)` · `emitted_light(pos)`
I vari livelli di luce in quel punto: totale, da blocchi, dal cielo, quella che conta per lo spawn, e
quella che il blocco stesso emette.

### `see_sky(pos)` · `brightness(pos)`
Se il punto vede il cielo, e la luminosità come numero fra 0 e 1.

## Scrivere

### `set(pos, blocco, proprietà...)`
Piazza un blocco. Le proprietà si passano a coppie nome/valore, o come mappa. Una proprietà che il
blocco non ha è un errore, con il suggerimento del nome giusto.

### `destroy(pos)` · `destroy(pos, -1)` · `destroy(pos, strumento)`
Rompe il blocco: con drop, senza drop, o come se fosse stato rotto con quello strumento.

### `harvest(giocatore, pos)`
Rompe il blocco come lo romperebbe quel giocatore, rispettandone strumento e incantesimi.

### `place_item(oggetto, pos, faccia?, considera_flag?)`
Usa un oggetto su un blocco, come farebbe un giocatore che ci clicca sopra.

### `update(pos)` · `block_tick(pos)` · `random_tick(pos)`
Forza un aggiornamento di blocco, un tick programmato o un tick casuale.

### `without_updates(espressione)`
Esegue l'espressione senza propagare aggiornamenti di blocco: utile per costruire in blocco senza far
partire redstone e cadute a ogni passo.

### `create_explosion(pos, potenza?, tipo?, fuoco?, sorgente?, causa?)`
Fa esplodere qualcosa in quel punto.

## Posizioni

### `pos(entità)` · `pos_offset(pos, direzione, quanto?)`
La posizione di un'entità, e una posizione spostata in una direzione.
