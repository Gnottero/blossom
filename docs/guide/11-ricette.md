# 11. Ricette

Pezzi di codice per problemi che tornano. Copiali e cambiali.

## Contare qualcosa e mostrarlo a chi gioca

```
module main;

import "core.mc.event" as event;
import "core.mc.text" as text;
import "core.map" as map;

let mut punteggi = {};

fn su_rottura(giocatore, blocco) -> (
    nome = entity.query(giocatore, 'name');
    punteggi:nome = (punteggi:nome || 0) + 1;
    text.hud(giocatore, 'blocchi rotti: ' + punteggi:nome);
    false
);

event.on('player_breaks_block', su_rottura);
```

`text.hud(giocatore, testo)` senza durata **resta** finché non la cambi; con una durata in tick
sparisce da sola. `text.hud(giocatore, null)` la toglie.

## Stato che sopravvive al riavvio

```
import "core.io" as io;

let mut stato = {};
let FILE = 'mia-app/stato.json';        // 'data' e' una radice che c'e' su ogni server

fn carica() -> stato = if(io.exists('data', FILE), io.read_json('data', FILE), {});
fn salva() -> io.write_json('data', FILE, stato);

carica();                               // nel corpo del modulo: succede al load
event.on('server_shuts_down', salva);
```

Salva anche ogni tanto, non solo allo spegnimento: un server che va giù male non chiama nessun
handler.

```
let mut tick = 0;
fn su_tick() -> (tick += 1; if(tick % 6000 == 0, salva()));    // ogni 5 minuti
```

## Un comando per l'amministratore

Non serve registrare comandi: le funzioni `pub` di un'app si chiamano già dalla console.

```
pub fn classifica() -> (
    ordinata = list.sort_key(map.pairs(punteggi), -_:1);
    list.map(ordinata, _:0 + ': ' + _:1)
);
```

```
/blossom script in mia-app classifica()
```

## Fare qualcosa fra N secondi

```
import "core.mc.event" as event;

event.schedule(100, ricontrolla, pos);      // 100 tick = 5 secondi
```

Per una cosa periodica, un contatore su `tick` (vedi sopra) è più economico di rischedularsi ogni
volta.

## Costruire senza far partire redstone e sabbia

```
import "core.mc.block" as block;

block.without_updates(
    loop(altezza, block.set([x, y + _, z], 'stone_bricks'))
);
```

## Scansionare un volume e contare

```
import "core.mc.scan" as scan;
import "core.mc.block" as block;

trovati = scan.scan(centro, [8, 4, 8], block.block(_) == 'diamond_ore');
```

`scan` restituisce **quante volte l'espressione è risultata vera**: contare è una riga, senza
accumulatori. Dentro il corpo `_x`, `_y`, `_z` sono le coordinate e `_` è il blocco.

## Lavoro lungo senza fermare il server

```
import "core.task" as task;

fn avvia(centro) -> task.start('lavoro', centro);

fn lavoro(centro) -> (
    dati = task.dock(scan.rect(centro, 32));       // leggi in una sosta
    piano = calcola(dati);                              // pensa fuori dal tick
    task.dock(applica(piano))                      // scrivi in una sosta
);
```

## Un default quando manca un valore

```
nome = configurazione:'nome' || 'senza nome';
```

`||` restituisce il primo operando vero, quindi funziona come "oppure". Attenzione se `0` o `''`
sono valori legittimi: lì il default scatterebbe per sbaglio, e serve `map.has`.

## Cercare in una lista

```
import "core.list" as list;

(l ~ x) != null;                    // c'è?
l ~ x;                              // l'indice, null se non c'è
list.find(l, _:'nome' == 'ada');   // il primo che soddisfa una condizione
list.has(l, 3);                     // esiste l'indice 3? — non "contiene 3"
```

Due trappole in una riga: `if(l ~ x, ...)` è sbagliato perché se `x` è il primo elemento l'indice è
`0`, che è falso; e `list.has(l, x)` su una lista chiede se **l'indice** esiste, non se il valore c'è
dentro. Su una mappa invece `map.has(m, chiave)` è esattamente quello che sembra.

## Raggruppare per chiave

```
fn raggruppa(elementi, chiave) -> (
    gruppi = {};
    for(elementi, (
        k = call(chiave, _);
        if(!map.has(gruppi, k), gruppi:k = []);
        list.put(gruppi:k, null, _)
    ));
    gruppi
);
```

## Gestire un fallimento previsto

```
import "core.json" as json;

fn leggi(testo) -> try(
    json.decode(testo),
    'json_error', (
        print('configurazione illeggibile, uso i valori di default');
        {}
    )
);
```

Con il filtro esplicito: senza, `try` cattura solo le eccezioni utente.

## Due app che si parlano

```
// app "miniera"
conteggio = event.signal_event('vena_esaurita', null, pos, quanti);

// app "registro"
event.handle_event('vena_esaurita', su_vena_esaurita);
```

`signal_event` restituisce `null` se nessuno ascolta: puoi saltare il lavoro di preparare i dati.

## Vedere cosa sta calcolando uno script

```
import "core.mc.effect" as effect;

effect.particle_box('happy_villager', angolo_a, angolo_b, 1);       // l'area di lavoro
effect.particle_line('flame', da, a, 1);                            // un percorso
entity.spawn('armor_stand', pos, {'Invisible' -> 1, 'CustomName' -> '"bersaglio"',
        'CustomNameVisible' -> 1, 'NoGravity' -> 1});                   // un punto, finché non lo togli
```

Con un client Blossom, [`core.mc.shape`](../api/core.mc.shape.md) disegna le stesse cose in modo
leggibile; senza, ricadono su particelle.

## Un modulo di sole costanti

```
module regole;

pub let ALTEZZA_MAX = 32;
pub let MATERIALI = ['stone', 'stone_bricks', 'deepslate'];
pub let COLORI = {'ok' -> 'l', 'attenzione' -> 'y', 'errore' -> 'r'};
```

```
import "regole" as regole;
if(altezza > regole.ALTEZZA_MAX, throw('troppo alta'));
```

## Provare una funzione senza far succedere l'evento

Tieni la logica fuori dagli handler:

```
fn su_campana(pos, direzione, chi) -> (costruisci(pos, 5); false);   // handler: sottile
pub fn costruisci(pos, altezza) -> ...;                              // logica: pubblica
```

```
/blossom script in mia-app costruisci([100, 64, 100], 5)
```
