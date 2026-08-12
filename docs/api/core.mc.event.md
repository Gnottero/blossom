# `core.mc.event`

Reagire a quello che succede nel mondo, e far succedere qualcosa fra app diverse.

```
import "core.mc.event" as event;

fn su_blocco_rotto(giocatore, blocco) -> (
    print(giocatore, 'hai rotto ' + blocco);
    false           // vero = la rottura viene annullata
);

event.on('player_breaks_block', su_blocco_rotto);
```

La funzione si passa **come valore**, non per nome in una stringa: così resta legata al modulo che
l'ha definita e continua a risolversi anche quando la chiama il mod, che di quel modulo non sa nulla.

Il numero di argomenti viene controllato al momento della registrazione: un handler con la firma
sbagliata è un errore di caricamento, non una riga nel log tre ore dopo.

Un handler che restituisce un valore **vero** annulla l'azione, se l'evento è annullabile. Non serve
nessuna stringa magica.

Gli handler di un'app spariscono quando l'app viene ricaricata o scaricata.

## Funzioni

### `on(evento, funzione)` · `on(evento, funzione, argomenti_fissi)` · `on(evento, funzione, argomenti_fissi, giocatore)`
Registra un handler. Gli argomenti fissi arrivano in coda a quelli dell'evento. Con il quarto
argomento l'handler scatta solo per quel giocatore.

### `off()` · `off(evento)`
Toglie tutti gli handler dell'app, o solo quelli di un evento.

### `available()` · `listeners(evento)` · `describe(evento)`
I nomi degli eventi, quanti ascoltano un evento, e la sua scheda: arietà, annullabilità, ascoltatori.

### `handle_event(evento, funzione, argomenti_fissi...)`
Come `on`, ma accetta anche un nome che il gioco non conosce: quello diventa un **evento di
applicazione**, dichiarato dalla prima app che lo ascolta.

### `signal_event(evento, giocatore_o_null, argomenti...)`
Genera un evento di applicazione. Restituisce quante app lo ascoltavano, `null` se quell'evento non
esiste. È il modo per far parlare due app senza che l'una conosca le funzioni dell'altra.

### `schedule(ritardo_in_tick, funzione, argomenti...)`
Chiama una funzione fra N tick. I tempi sono in tick e non in millisecondi: uno script che aspetta
venti tick aspetta lo stesso tempo *di gioco* anche se il server rallenta.

## Il catalogo

Il catalogo è chiuso: registrarsi a un nome che non c'è è un errore di caricamento, con il nome più
vicino come suggerimento.

### Server

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `server_starts` | | |
| `server_shuts_down` | | |
| `tick` | | |
| `tick_nether` | | |
| `tick_ender` | | |

### Giocatore: movimento e stato

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `player_jumps` | `player` | |
| `player_deploys_elytra` | `player` | |
| `player_wakes_up` | `player` | |
| `player_escapes_sleep` | `player` | |
| `player_rides` | `player, strafe, forward, jumping, sneaking` | |
| `player_starts_sneaking` | `player` | |
| `player_stops_sneaking` | `player` | |
| `player_starts_sprinting` | `player` | |
| `player_stops_sprinting` | `player` | |
| `player_swaps_hands` | `player` | sì |
| `player_swings_hand` | `player, hand` | |
| `player_switches_slot` | `player, from, to` | |

### Giocatore: blocchi e oggetti

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `player_uses_item` | `player, item, hand` | sì |
| `player_clicks_block` | `player, block, facing` | sì |
| `player_right_clicks_block` | `player, item, hand, block, face, hitvec` | sì |
| `player_interacts_with_block` | `player, hand, block, face, hitvec` | |
| `player_placing_block` | `player, item, hand, block` | sì |
| `player_places_block` | `player, item, hand, block` | |
| `player_breaks_block` | `player, block` | sì |
| `player_releases_item` | `player, item, hand` | sì |
| `player_finishes_using_item` | `player, item, hand` | sì |
| `player_picks_up_item` | `player, item` | |
| `player_drops_item` | `player` | sì |
| `player_drops_stack` | `player` | sì |
| `player_chooses_recipe` | `player, recipe, full_stack` | sì |

### Giocatore: entità e danni

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `player_interacts_with_entity` | `player, entity, hand` | sì |
| `player_attacks_entity` | `player, entity` | sì |
| `player_collides_with_entity` | `player, entity` | |
| `player_trades` | `player, merchant, buy, buy_b, sell` | |
| `player_takes_damage` | `player, amount, source, source_entity` | sì |
| `player_deals_damage` | `player, amount, entity` | sì |
| `player_dies` | `player` | |
| `player_respawns` | `player` | |
| `player_changes_dimension` | `player, from_pos, to_pos, from_dimension, to_dimension` | |

### Giocatore: sessione e chat

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `player_connects` | `player` | |
| `player_disconnects` | `player, reason` | |
| `player_message` | `player, message` | sì |
| `player_command` | `player, command` | sì |
| `statistic` | `player, category, event, value` | |

### Mondo

| Evento | Argomenti | Annullabile |
| --- | --- | --- |
| `lightning` | `pos, skeleton_horse` | |
| `explosion` | `pos, power, source, attacker, block_interaction, create_fire` | sì |
| `explosion_outcome` | `... , blocks, entities` | |
| `block_dispenses` | `pos, item` | sì |
| `bell_rings` | `pos, direction, entity` | sì |
| `chunk_unloaded` | `x, z` | |
| `chunk_generated` | `x, z` | |
| `chunk_loaded` | `x, z, generato` | |
| `entity_loads` | `entity` | |
| `piston_extends` | `piston, facing, blocks` | sì |
| `piston_retracts` | `piston, facing, blocks` | sì |
| `block_forms` | `previous_block, new_block` | sì |

`entity_loads` scatta ogni volta che un'entità compare nel mondo: riletta dal disco col suo chunk,
generata dal mondo, o evocata da qualcuno. Se ti interessa solo qualche tipo, usa
`entity_load_handler` di [`core.mc.entity`](core.mc.entity.md), che filtra prima di chiamarti.

`chunk_generated` scatta quando un chunk nasce per la prima volta; `chunk_loaded` ogni volta che un
chunk entra in memoria, con `generato` a dire se è stato creato adesso o riletto dal disco. I due
handler vengono chiamati sul thread del server anche se il chunk è stato preparato altrove, quindi
dentro si può fare tutto quello che si fa in un evento normale.

`piston_extends` e `piston_retracts` arrivano **prima** che i blocchi si muovano, e `blocks` è
l'elenco di quelli che si muoverebbero: annullare ferma il movimento. `piston_retracts` scatta solo
quando la retrazione tirerebbe davvero qualcosa — su un pistone appiccicoso, con un blocco attaccato
e tirabile — altrimenti l'elenco sarebbe una bugia.

`block_forms` copre le formazioni per contatto fra fluidi: cemento (che cade nell'acqua o ne viene
toccato), ostruzione e pietrisco dove l'acqua incontra la lava, basalto sul soul soil. I due
argomenti sono lo stesso posto: com'era e come starebbe per diventare.

## Eventi di una singola entità

Sono in [`core.mc.entity`](core.mc.entity.md), con `entity_event`: `on_tick`, `on_removed`,
`on_death`, `on_damage`, `on_move`. Vivono dentro l'entità e spariscono con lei.
