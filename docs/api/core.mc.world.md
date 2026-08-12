# `core.mc.world`

Il mondo intorno ai blocchi: tempo, chunk, biomi, strutture, dimensioni.

```
import "core.mc.world" as world;

world.current_dimension();        // 'minecraft:overworld'
world.day_time();                 // l'ora del mondo
world.biome([0, 64, 0]);          // 'minecraft:plains'
```

## Tempo

### `world_time()` · `day_time()` · `day_time(nuovo_valore)`
Il tempo totale del mondo e l'ora del giorno; con un argomento imposta l'ora.

### `tick_time()` · `last_tick_times()`
Il tick corrente del server, e i tempi degli ultimi 100 tick in millisecondi.

### `game_tick()` · `game_tick(millisecondi_massimi)`
Forza il server ad avanzare di un tick. Con l'argomento, continua a far girare tick finché non ha
consumato quel tempo. Serve a far avanzare il gioco da uno script che sta girando dentro un tick.

## Meteo e mondo

### `weather()` · `weather(tipo)` · `weather(tipo, durata)`
Legge o imposta pioggia e temporale.

### `seed()` · `save()`
Il seed del mondo, e un salvataggio forzato su disco.

### `top(tipo, pos)`
La prima altezza libera secondo una delle mappe di altezza (`'surface'`, `'motion_blocking'`, ...).

### `sample_noise(pos, tipo)` · `sample_noise()`
Campiona una funzione di densità del generatore in quel punto; senza argomenti elenca i nomi
disponibili.

## Chunk

### `loaded(pos)` · `loaded_status(pos)` · `loaded_ep(pos)`
Se il chunk è caricato, in che stato è, e se è caricato per l'elaborazione delle entità.

### `is_chunk_generated(pos)` · `generation_status(pos)`
Se il chunk esiste su disco, e a che punto è la sua generazione.

### `chunk_tickets()` · `add_chunk_ticket(pos, tipo, raggio)`
I ticket che tengono caricati i chunk, e come aggiungerne uno.

### `reload_chunk(pos)` · `reset_chunk(pos)`
Rimanda il chunk ai client, o lo rigenera da zero.

### `inhabited_time(pos)` · `in_slime_chunk(pos)` · `spawn_potential(pos)`
Quanto è stato frequentato, se è uno slime chunk, e il potenziale di spawn calcolato dal gioco.

## Biomi e strutture

### `biome(pos)` · `biome(nome, proprietà)` · `set_biome(pos, nome, aggiorna?)`
Legge il bioma di un punto, interroga le proprietà di un bioma, o lo cambia.

### `structures(pos)` · `structures(pos, nome)`
Le strutture che coprono quel punto.

### `structure_references(pos)` · `structure_references(pos, nome)`
I chunk che fanno riferimento alle strutture di quel punto.

### `structure_eligibility(pos, tipo?, dettagli?)`
Se in quel chunk una struttura *potrebbe* generarsi, senza generarla.

### `set_structure(pos, nome)` · `set_structure(pos, nome, null)`
Genera una struttura, o ne toglie il riferimento.

### `plop(pos, cosa)`
Piazza una feature o una struttura del generatore in quel punto (alberi, geyser, villaggi...).

## Punti di interesse

### `poi(pos)` · `poi(pos, raggio, ordina?, tipo?, stato?)`
I punti di interesse (letti, postazioni di lavoro, portali) in un'area.

### `set_poi(pos, tipo, occupazione?)` · `set_poi(pos, null)`
Crea o toglie un punto di interesse.

## Dimensioni

### `current_dimension()`
La dimensione in cui lo script sta girando.

### `in_dimension(dimensione, espressione)`
Valuta l'espressione come se fosse in un'altra dimensione: tutte le funzioni di mondo dentro di essa
guardano là.

### `view_distance()` · `enable_hidden_dimensions()`
La distanza di rendering del server, e l'attivazione delle dimensioni normalmente nascoste.

### `relight(pos)`
Ricalcola la luce attorno a un punto.
