# `core.mc.entity`

Trovare, creare, interrogare e modificare le entità - giocatori compresi.

```
import "core.mc.entity" as entity;

entity.player();                            // chi ha lanciato lo script
entity.player('all');                       // tutti i giocatori collegati
entity.query(e, 'health');                  // vita
entity.modify(e, 'motion', [0, 1, 0]);      // spinta verso l'alto
```

## Trovare

### `player()` · `player(nome)` · `player('all' | 'survival' | 'creative' | 'spectating' | ...)`
Il giocatore che esegue, uno per nome, o l'elenco di quelli in una categoria.

### `entity_list(tipo)`
Tutte le entità di un tipo nel mondo (`'*'` per tutte, `'!player'` per tutto tranne i giocatori).

### `entity_area(tipo, centro, raggio)`
Le entità di un tipo dentro un'area.

### `entity_selector(selettore)`
Le entità che un selettore vanilla (`@e[type=cow,distance=..10]`) sceglierebbe.

### `entity_id(id | uuid)` · `entity_types(tipo?)`
Un'entità dal suo id numerico o uuid, e i tipi di entità registrati.

## Interrogare e modificare

### `query(entità, cosa, argomenti...)`
Legge un attributo. Ne esistono molti: `pos`, `x`/`y`/`z`, `motion`, `name`, `uuid`, `health`, `food`,
`air`, `on_ground`, `sneaking`, `sprinting`, `gamemode`, `holds`, `inventory`, `effect`, `nbt`,
`dimension`, `target`, `brain`, `look`, `facing`, `age`, `fire`, `is_burning`, `hunger`, `saturation`,
`portal_cooldown`, e altri. `query(entità, 'nbt')` dà tutto il resto.

### `modify(entità, cosa, valore...)`
Scrive un attributo: `pos`, `motion`, `motion_x`/`y`/`z`, `custom_name`, `health`, `food`, `air`,
`gamemode`, `nbt`, `effect`, `nbt_merge`, `persistence`, `jump`, `kill`, `dismount`, `mount`, `tag`,
`fire`, `silent`, `gravity`, `invulnerable`, `pickup_delay`, `portal_cooldown`, e altri.

### `spawn(tipo, pos, nbt?)`
Crea un'entità. Il NBT è quello che il gioco userebbe in un comando `/summon`.

## Eventi di una singola entità

### `entity_event(entità, evento, funzione, argomenti_fissi...)`
Attacca un handler a *quella* entità: `on_tick`, `on_removed`, `on_death`, `on_damage`, `on_move`.
L'handler vive dentro l'entità, quindi la segue fra i mondi e sparisce con lei. Passare `null` al
posto della funzione lo toglie.

Il primo argomento passato all'handler è sempre l'entità stessa; il numero di argomenti viene
controllato subito, non al primo evento.

## Marcatori

### `create_marker(nome, pos, blocco?, tag?)` · `remove_all_markers()`
Marcatori visibili usati per il debug di uno script; `remove_all_markers` pulisce quelli dell'app.

### `entity_load_handler(tipi, funzione)`
Chiama `funzione(entity)` ogni volta che compare un'entità di uno di quei tipi. `tipi` è un tipo o
una lista di tipi (`'zombie'`, `['zombie', 'minecraft:husk']`); con `null` si sentono tutte.

```
entity.entity_load_handler(['zombie', 'skeleton'], su_non_morto);
```

Il filtro per tipo viene fatto **prima** di chiamare l'handler, quindi ascoltare un tipo raro non
costa una chiamata per ogni entità che compare nel mondo. Per sentirle davvero tutte c'è anche
l'evento `entity_loads` in [`core.mc.event`](core.mc.event.md); questa funzione è la stessa cosa con
il filtro incluso.

In Scarpet questa era una famiglia di oltre cento eventi generati dal registro delle entità, uno per
tipo, con due varianti di firma di cui una già deprecata.

## Il client di chi gioca

### `has_client(giocatore)`
Se quel giocatore ha Blossom anche sul client — cioè se vedrà le forme di
[`core.mc.shape`](core.mc.shape.md) disegnate davvero invece che approssimate a particelle.

Non serve per disegnare: `draw_shape` decide da sola, giocatore per giocatore. Serve a chi vuole
offrire qualcosa di diverso a chi ha il client, e non doverlo indovinare.
