# `core.mc.system`

Il server visto da fuori: comandi, statistiche, diagnostica.

```
import "core.mc.system" as system;

system.run('say ciao');
system.system_info('server_mods');
```

### `run(comando)`
Esegue un comando vanilla con i permessi dell'app e ne raccoglie l'esito, invece di stamparlo in
chat. Restituisce `[codice, output]`.

### `system_info()` · `system_info(chiave)`
Informazioni sul server e sul processo: memoria, thread, versione, mod caricati, tempo di attività,
mondi caricati, app caricate. Senza argomenti elenca le chiavi disponibili.

### `get_mob_counts()` · `get_mob_counts(categoria)`
Quanti mob di ogni categoria ci sono, e quanti ne permetterebbe il limite.

### `statistic(giocatore, categoria, voce)`
Una statistica di un giocatore (`'used'`, `'mined'`, `'killed'`, `'custom'`, ...).

### `logger(messaggio)` · `logger(livello, messaggio)`
Scrive nel log del server. I livelli sono `'error'`, `'warn'`, `'info'`, `'debug'`.

### `task_join(task)`
Aspetta un task lanciato con `core.task`, ma dal thread del server: è la variante che sa aspettare
senza bloccare il tick.
