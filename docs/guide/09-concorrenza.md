# 9. Concorrenza

Il server ha un thread che fa girare il mondo, venti volte al secondo. Tutto quello che uno script
fa dentro un evento lo fa **su quel thread**, e mentre lo fa il mondo è fermo. Da qui tutto il resto
di questo capitolo.

## Quando serve un task

Un `task` gira su un thread suo: non tiene fermo il tick, e non ha scadenza.

```
import "core.task" as task;

fn conta_minerali(centro) -> ...;                   // lavoro lungo

t = task.start('conta_minerali', centro);            // parte subito, non blocca
task.done(t);                             // ha finito?
task.wait(t);                                  // aspetta e prendi il risultato
```

Serve quando: scansioni un volume grosso, generi qualcosa di costoso, aspetti qualcosa. **Non** serve
per andare più veloce — è concorrenza, non parallelismo automatico, e il coordinamento costa.

| Funzione | Cosa fa |
| --- | --- |
| `task(f, ...args)` | avvia e restituisce un handle |
| `task_join(t)` | aspetta la fine e restituisce il valore |
| `task_await(t)` | aspetta senza restituire |
| `task_completed(t)` · `task_value(t)` | se ha finito, e il risultato se c'è |
| `task_count()` | quanti task sono in volo |
| `task_send(t, v)` · `yield(v)` · `task_ready(t)` | scambiare valori con un task in corso |
| `sleep(ms)` | sospende |

## Toccare il mondo da dentro un task

Il mondo non è thread-safe. Da un task, ogni chiamata che legge o scrive blocchi ed entità va
incapsulata in `task_dock`, che la esegue sul thread principale:

```
fn scansione(centro) -> (
    trovati = 0;
    loop(1000, (
        p = punto(centro, _);
        if(task.dock(block.block(p)) == 'diamond_ore', trovati += 1)
    ));
    trovati
);
```

Il `task_dock` costa: fa la coda con il tick. La forma giusta è **un dock per blocco di lavoro**, non
uno per ogni singola lettura — leggi tutto quello che ti serve in un dock solo, poi elabora fuori.

```
dati = task.dock(list.map(posizioni, block.block(_)));      // una sola sosta
risultato = elabora(dati);                                       // fuori dal thread del gioco
```

## Valori condivisi

Le singole operazioni su una lista o una mappa condivisa sono già atomiche: assegnare, `+=`, `put`,
`delete`, `length`, iterare. Quello che non è atomico è una **sequenza**: `x = x + 1` legge e scrive
in due tempi, e fra i due un altro task può passare.

Per le sequenze c'è `synchronize`:

```
task.synchronize('registro', (
    totale = stato:'totale';
    stato:'totale' = totale + 1
));
```

Il lock è **reentrant** (una funzione sincronizzata può chiamarne un'altra sullo stesso lock) e
**equo** (chi aspetta entra nell'ordine in cui è arrivato). Il nome del lock è una stringa qualunque:
usane uno per risorsa, non uno solo per tutta l'app, o hai serializzato l'app.

## Il watchdog

Ogni esecuzione ha un budget di tempo e una profondità massima di chiamata:

| Quando | Tempo | Profondità |
| --- | --- | --- |
| dentro un evento di gioco | 2 s | 256 |
| da `/blossom script run` o al caricamento | 15 s | 256 |
| dentro un `task()` | nessun limite | 256 |

Il task non ha scadenza **apposta**: gira su un thread suo, non tiene fermo niente, ed è la cosa da
usare quando un lavoro è davvero lungo. La profondità invece vale anche lì: una ricorsione infinita
riempie comunque uno stack.

Il manifesto può cambiarli:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` li toglie. L'errore del watchdog non è catturabile con `try`.

## Lo schema che funziona quasi sempre

Un lavoro lungo che deve finire nel mondo si scrive in tre pezzi:

```
fn avvia_lavoro(centro) -> task.start('lavoro', centro);

fn lavoro(centro) -> (
    dati = task.dock(raccogli(centro));         // 1. leggi, in una sosta sola
    piano = calcola(dati);                           // 2. pensa, fuori dal tick
    task.dock(applica(piano))                   // 3. scrivi, in una sosta sola
);
```

Leggi in blocco, pensa fuori, scrivi in blocco. Le due soste sono due tick occupati; tutto il resto
non tocca il server.
