# `core.task`

Concorrenza: task, attese, e sezioni critiche.

```
import "core.task" as task;

t = task(fn() -> lungo_calcolo());
task.task_join(t);      // aspetta e restituisce il risultato
```

### Avviare e aspettare

`task(funzione, ...argomenti)` avvia l'esecuzione su un altro thread e restituisce un handle.
`task_join(t)` aspetta la fine e restituisce il valore, `task_await(t)` aspetta senza restituirlo,
`task_completed(t)` dice se ha finito, `task_value(t)` legge il risultato se c'è.
`task_count()` dice quanti task sono in volo.

### Comunicare

`task_send(t, valore)` manda un valore a un task, `yield(valore)` lo restituisce da dentro il task
sospendendone l'esecuzione fino alla prossima richiesta, `task_ready(t)` dice se c'è un valore
pronto.

### Sincronizzazione

Ogni singola operazione su un valore condiviso è già atomica: assegnare, aggiornare con `+=`, fare
`put`, `delete`, `length` o iterare una lista o una mappa condivisa. Quello che non è atomico è una
*sequenza* di operazioni: `x = x + 1` legge e scrive in due tempi. Per quelle serve

`synchronize(lock, espressione)` — il lock è reentrant (una funzione sincronizzata può chiamarne
un'altra sullo stesso lock) ed equo (chi aspetta entra nell'ordine in cui è arrivato, nessuno resta
fuori indefinitamente).

`task_dock(espressione)` esegue l'espressione sul thread principale del gioco: serve per toccare
oggetti del mondo da dentro un task. `sleep(millisecondi)` sospende.
