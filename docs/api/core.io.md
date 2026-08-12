# `core.io`

Il disco fuori dalla cartella dell'app, dentro confini che dichiara un amministratore.

```
import "core.io" as io;

io.write_text('condiviso', 'note/oggi.txt', 'ciao');
io.read_text('condiviso', 'note/oggi.txt');       // 'ciao'
```

Per i dati dell'app c'è già [`core.mc.file`](core.mc.file.md), e per quasi tutto basta: ogni app ha
la sua cartella e nessun'altra la vede. `core.io` serve al caso che quello non copre — parlare con
qualcosa che sul server c'è già: una cartella condivisa fra app, i log di un altro strumento, una
cartella di scambio con un processo esterno.

## I confini

Negli script non esistono percorsi assoluti. Un amministratore dichiara delle **radici** con un nome,
in `config/blossomscript.json`:

```json
{
  "io": {
    "roots": {
      "condiviso": "/srv/blossom-condiviso",
      "log": "/var/log/mio-server"
    },
    "writable": ["condiviso"]
  }
}
```

Ogni funzione comincia con il nome della radice, e il resto del percorso è sempre relativo a quella.
Tre conseguenze, tutte volute:

- **senza dichiarazione non si arriva da nessuna parte.** `core.io` importato su un server che non ha
  configurato niente esiste ma non apre nulla, e lo dice.
- **la radice si vede nel punto della chiamata.** Leggere il codice basta a sapere dove finiscono i
  dati; non c'è un percorso costruito tre funzioni più in là.
- **scrivere è un permesso a parte.** Una radice è di sola lettura salvo che compaia in `writable`.

Un percorso che esce dalla radice è un errore, e non solo quando ci prova con `..`: il percorso viene
risolto davvero prima del confronto, quindi un collegamento simbolico piazzato dentro la radice che
punta fuori viene rifiutato allo stesso modo.

```
RuntimeError[io]
  Path '../segreto.txt' escapes root 'dati'.
```

## Le funzioni

| Funzione | Cosa fa |
| --- | --- |
| `roots()` | i nomi delle radici configurate |
| `writable(radice)` | se ci si può scrivere |
| `exists(radice, percorso)` | se il file o la cartella c'è |
| `is_directory(radice, percorso)` | se è una cartella |
| `list(radice, percorso)` | i nomi dentro una cartella, ordinati |
| `read_text(radice, percorso)` | tutto il contenuto come stringa |
| `read_lines(radice, percorso)` | una lista di righe |
| `read_json(radice, percorso)` | il contenuto interpretato come json |
| `write_text(radice, percorso, testo)` | scrive, creando le cartelle che mancano |
| `append_text(radice, percorso, testo)` | aggiunge in fondo |
| `write_json(radice, percorso, valore)` | scrive il valore come json |
| `make_directory(radice, percorso)` | crea una cartella e le sue intermedie |
| `delete_file(radice, percorso)` | cancella un file o una cartella **vuota** |
| `size(radice, percorso)` | dimensione in byte, o `null` se non esiste |
| `modified(radice, percorso)` | ultima modifica in millisecondi, o `null` |

`list` non è ricorsiva e `delete_file` non cancella alberi. Sono le due scelte in cui una comodità in
più si paga con un incidente: una ricorsione su una radice grande blocca il tick senza che chi l'ha
scritta se ne accorga, e una cancellazione ricorsiva dietro un nome corto è il modo più facile di
perdere dati per un percorso sbagliato di un carattere. Chi vuole scendere o cancellare un albero lo
scrive un livello alla volta — e mentre lo scrive, lo vede.

Si chiama `delete_file` e non `delete` perché `delete` è già quello che toglie un elemento da una
lista o da una mappa.

## Errori

Gli errori del disco sono `io_exception`, quindi si catturano:

```
try(
    io.read_text('dati', 'manca.txt'),
    'io_exception', 'il file non c\'e\''
);
```

Le violazioni dei confini invece **non** si catturano: una radice sconosciuta, un percorso che esce,
una scrittura su una radice di sola lettura sono errori del programma, non condizioni da gestire.
