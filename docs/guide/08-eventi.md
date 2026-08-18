# 8. Eventi

Un'app che fa qualcosa da sola è un'app che ascolta. Il catalogo completo degli eventi sta in
[`core.mc.event`](../api/core.mc.event.md); qui c'è come si usa.

## Registrare

```
module main;

import "core.mc.event" as event;
import "core.mc.text" as text;

fn su_rottura(giocatore, blocco) -> (
    text.print(giocatore, 'hai rotto ' + blocco);
    false
);

event.on('player_breaks_block', su_rottura);
```

Quattro cose che questa riga decide:

**La funzione si passa come valore**, non come nome in una stringa. Così resta legata al modulo che
l'ha definita, e continua a risolversi quando la chiama il mod, che di quel modulo non sa niente.

**L'arietà viene controllata subito.** Un handler con la firma sbagliata è un errore di caricamento,
non una riga nel log tre ore dopo. Guarda la tabella del catalogo per gli argomenti di ogni evento.

**Il nome dell'evento viene controllato subito.** Il catalogo è chiuso: `player_break_block` (senza
la `s`) non carica, e ti suggerisce il nome giusto.

**Gli handler muoiono con l'app.** `reload` e `unload` li tolgono prima di rieseguire il file: non
restano due copie dello stesso listener attaccate all'evento.

Si registrano nel **corpo del modulo**, che è la parte eseguita al caricamento. Registrare un
handler dentro un altro handler è possibile, ma finisce quasi sempre con dieci listener dove ne
serviva uno.

## Annullare

Un handler che restituisce un valore **vero** annulla l'azione, se l'evento è annullabile (la
colonna "Annullabile" del catalogo). Non serve nessuna stringa magica.

```
fn niente_ossidiana(giocatore, blocco) -> blocco == 'obsidian';
event.on('player_breaks_block', niente_ossidiana);
```

Da cui l'abitudine di chiudere ogni handler con un `false` esplicito quando **non** deve annullare:
il valore dell'ultima espressione del corpo diventa il ritorno, e un `text.print` che restituisce il
messaggio è un valore vero. Un handler che finisce con un `print` e nessun `false` annulla l'azione
per sbaglio.

```
fn su_campana(pos, direzione, chi) -> (
    text.print(chi, 'din don');
    false                           // senza questa riga, la campana non suona più
);
```

## Argomenti fissi e filtro per giocatore

```
event.on('player_jumps', conta_salti, ['gruppo-a']);        // arriva in coda agli argomenti
event.on('player_jumps', solo_per_lei, null, giocatore);    // scatta solo per quel giocatore
```

## Un tick alla volta

`tick` scatta venti volte al secondo, e tutto quello che ci metti dentro viene pagato venti volte al
secondo. Le due tecniche che servono quasi sempre:

```
import "core.mc.event" as event;

let mut tick_corrente = 0;

fn ogni_secondo() -> (
    tick_corrente += 1;
    if(tick_corrente % 20 == 0, lavoro_periodico())
);

event.on('tick', ogni_secondo);
```

e il rinvio, per non fare tutto nello stesso tick:

```
event.schedule(40, ricontrolla, pos);       // fra 40 tick, cioè 2 secondi
```

I tempi sono in **tick** e non in millisecondi: uno script che aspetta venti tick aspetta lo stesso
tempo *di gioco* anche se il server rallenta.

## Ricaricare un'app senza perdere lo stato

Un `reload` ributta via tutto: le variabili di modulo ripartono da zero. I due eventi che coprono il
buco sono `app_reloading`, che scatta **prima** che la versione vecchia venga smontata, e
`app_reloaded`, che scatta **dopo** che il codice nuovo ha girato.

```
module main;

import "core.mc.event" as event;
import "core.io" as io;

let mut stato = {};
let FILE = 'mia-app/stato.json';

fn prima(app) -> (
    if(app == 'mia-app', io.write_json('data', FILE, stato));
    false
);

fn dopo(app) -> (
    if(app == 'mia-app', stato = io.read_json('data', FILE));
    false
);

event.on('app_reloading', prima);
event.on('app_reloaded', dopo);
```

Tre cose da sapere:

- **arrivano per ogni app**, non solo per la tua: il nome è l'argomento, e chi guarda solo sé stesso
  lo confronta come qui sopra. È quello che li rende utili anche a un'app che ne sorveglia altre.
- **la versione nuova sente il proprio `app_reloaded`**, perché l'evento scatta dopo il corpo dei
  moduli: l'handler è già registrato quando arriva.
- **un primo caricamento non genera niente**, e nemmeno un `unload`: non è un ricaricamento. Se il
  codice nuovo esplode al caricamento, arriva `app_reloading` e non arriva `app_reloaded` — dalla
  coppia si capisce com'è andata.

## Eventi di una singola entità

Vivono dentro l'entità, la seguono fra i mondi e spariscono con lei:

```
import "core.mc.entity" as entity;

entity.entity_event(mucca, 'on_death', fn_di_morte);
```

Gli eventi disponibili sono `on_tick`, `on_removed`, `on_death`, `on_damage`, `on_move`. Il primo
argomento passato all'handler è sempre l'entità stessa.

Per reagire alla **comparsa** di entità di certi tipi c'è `entity.entity_load_handler(tipi, f)`, che
filtra per tipo **prima** di chiamarti: ascoltare gli zombie non costa una chiamata per ogni entità
che compare nel mondo.

## Far parlare due app

Un'app non chiama le funzioni di un'altra: dichiara un evento e lo genera.

```
// l'app che ascolta
event.handle_event('miniera_esaurita', su_esaurimento);

// l'app che avvisa
conteggio = event.signal_event('miniera_esaurita', null, pos, quanti);
```

`handle_event` accetta anche un nome che il gioco non conosce: quello diventa un **evento di
applicazione**, dichiarato dalla prima app che lo ascolta. `signal_event` restituisce quante app
l'hanno sentito, o `null` se quell'evento non esiste — cioè se nessuno ascolta, e allora chi lo
genera può risparmiarsi il lavoro.

È il modo previsto per accoppiare due app senza che nessuna delle due debba conoscere le funzioni
dell'altra, e senza che l'ordine di caricamento conti.

## Cosa non mettere in un handler

- **lavoro lungo**: il tick sta aspettando. Il budget è 2 secondi e superarlo è un errore. Se serve
  davvero, un `task` ([capitolo 9](09-concorrenza.md)).
- **un `print` per ogni evento frequente**: `player_rides` e `tick` scattano di continuo.
- **errori non gestiti in un evento annullabile**: se l'handler esplode, non restituisce niente e
  l'azione non viene annullata. Vedi la fine del [capitolo 7](07-errori.md).
