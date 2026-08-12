# API di Blossom

La libreria standard è organizzata a moduli: niente è disponibile per magia, tutto si importa con un
alias esplicito. Sapere da dove viene una funzione è parte di saperla leggere.

```
import "core.math" as math;
import "core.list" as list;
```

| Modulo | Cosa contiene |
| --- | --- |
| [`core.math`](core.math.md) | aritmetica, trigonometria, casualità, rumore procedurale |
| [`core.string`](core.string.md) | stringhe, espressioni regolari, codifiche |
| [`core.list`](core.list.md) | liste: costruzione, trasformazione, ordinamento |
| [`core.map`](core.map.md) | mappe: chiavi, valori, coppie |
| [`core.json`](core.json.md) | conversione da e verso json |
| [`core.task`](core.task.md) | concorrenza, attese, sezioni critiche |
| [`core.io`](core.io.md) | il disco fuori dalla cartella dell'app, dentro radici dichiarate |
| [`core.system`](core.system.md) | tempo, introspezione, misura |

## Il layer Minecraft

| Modulo | Cosa contiene |
| --- | --- |
| [`core.mc.block`](core.mc.block.md) | leggere e scrivere blocchi |
| [`core.mc.world`](core.mc.world.md) | tempo, chunk, biomi, strutture, dimensioni |
| [`core.mc.scan`](core.mc.scan.md) | percorrere volumi di blocchi |
| [`core.mc.entity`](core.mc.entity.md) | trovare, creare e modificare entità e giocatori |
| [`core.mc.item`](core.mc.item.md) | inventari, oggetti, ricette |
| [`core.mc.structure`](core.mc.structure.md) | salvare, piazzare e confrontare template di struttura |
| [`core.mc.screen`](core.mc.screen.md) | schermate aperte a un giocatore |
| [`core.mc.scoreboard`](core.mc.scoreboard.md) | obiettivi, squadre, barre del boss |
| [`core.mc.nbt`](core.mc.nbt.md) | i dati grezzi del gioco |
| [`core.mc.text`](core.mc.text.md) | messaggi e testo formattato |
| [`core.mc.effect`](core.mc.effect.md) | suoni, particelle, post effect |
| [`core.mc.shape`](core.mc.shape.md) | far vedere quello che uno script calcola |
| [`core.mc.event`](core.mc.event.md) | reagire a quello che succede |
| [`core.mc.file`](core.mc.file.md) | i dati dell'app su disco |
| [`core.mc.system`](core.mc.system.md) | comandi, statistiche, diagnostica |

## Il prelude

Senza importare nulla sono disponibili solo le cose che sono grammatica travestita da funzione, più
`print`:

`print`, `if`, `try`, `throw`, `return`, `break`, `continue`, `loop`, `while`, `for`, `c_for`,
`exit`, `call`, `outer`, `not` (il nome funzionale di `!`).

Ci sono anche `l` e `m`, che il tokenizer costruisce per `[ ... ]` e `{ ... }`: non si scrivono a
mano.

Usare una funzione di libreria senza averla importata non è un errore generico: il messaggio dice in
quale modulo vive e quale riga di import aggiungere.
