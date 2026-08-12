# `core.system`

Tempo, introspezione e misura.

```
import "core.system" as system;

system.time();              // millisecondi ad alta risoluzione, per misurare durate
system.type([1, 2]);        // 'list'
```

### Tempo

`time()` restituisce un contatore monotono in millisecondi con la parte decimale — è quello da usare
per misurare quanto dura qualcosa. `unix_time()` restituisce il tempo assoluto in millisecondi.
`convert_date(timestamp)` lo scompone in `[anno, mese, giorno, ora, minuto, secondo, giorno_settimana,
giorno_anno]`, e la stessa funzione fa il contrario a partire da una lista.

### Introspezione

`type(valore)` restituisce il nome del tipo. `var(nome)` legge una variabile per nome e `vars(prefisso)`
elenca quelle che iniziano per un prefisso — utili per il debug, non per la logica normale, dove un
accesso diretto è più chiaro e più veloce.

`system_variable_get(chiave)` e `system_variable_set(chiave, valore)` leggono e scrivono uno spazio
condiviso da tutte le app dello stesso server.

### Misura

`profile_expr(espressione)` esegue l'espressione ripetutamente per un intervallo fisso e restituisce
quante volte ci è riuscito: è il modo rapido di confrontare due implementazioni senza montare un
benchmark.

Risponde però a "quanto ci mette questo pezzo", non a "chi si mangia il tick". Per la seconda
domanda c'è il comando `/blossom profile`, che misura funzione per funzione:

| Comando | Cosa misura |
| --- | --- |
| `/blossom profile run <codice>` | un'espressione al volo, in un interprete pulito |
| `/blossom profile in <app> <codice>` | del codice eseguito dentro un'app caricata |
| `/blossom profile start <app>` | accende la misura sull'app: da lì conta anche quello che fa da sola, cioè i suoi handler di evento |
| `/blossom profile stop <app>` | chiude la misura e stampa il rapporto |

Il rapporto ha due tabelle. Prima i moduli, ordinati per tempo proprio; poi le quindici funzioni più
costose, con **tempo proprio e tempo totale**. La coppia serve: il totale è il tempo passato dentro
la funzione, comprese quelle che chiama; il proprio è quello che resta togliendole. Una funzione con
il totale enorme e il proprio minuscolo non è lenta — è solo quella in cima allo stack, e
ottimizzarla non cambia niente.

`start`/`stop` è la coppia che serve davvero su un'app a eventi: `run` misura solo quello che scrivi
tu sulla riga di comando, mentre il problema di solito è un handler che scatta cento volte al
secondo.
