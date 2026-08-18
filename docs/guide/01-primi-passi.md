# 1. Primi passi

## Cosa serve

Fabric Loader 0.19.3+ e Java 25, sul server. Il jar si costruisce dal repo:

```
./gradlew :blossom:build
```

e finisce in `blossom/build/libs/`. Copialo in `mods/`. Non serve Carpet, non serve niente
altro.

Il mod è **server-side**: chi si collega con un client vanilla non perde nessuna funzione. Lo stesso
jar messo anche sul client aggiunge solo il disegno delle forme di
[`core.mc.shape`](../api/core.mc.shape.md); senza client quelle forme si vedono come particelle.

Per provare senza installare niente:

```
./gradlew :blossom:runServer
```

La prima volta il server si ferma chiedendo l'EULA di Mojang: scrivi `eula=true` in
`blossom/run/eula.txt` e rilancia.

## Il file più corto che fa qualcosa

Gli script di un mondo vivono in `<mondo>/blossom/`. Un file `.bs` è un'app:

```
// <mondo>/blossom/saluta.bs
module saluta;

import "core.mc.text" as text;
import "core.mc.event" as event;

fn benvenuto(giocatore) -> (
    text.print(giocatore, 'benvenuto');
    false
);

event.on('player_connects', benvenuto);
print('saluta pronta');
```

In gioco:

```
/blossom script load saluta
```

Il `print` finale compare subito: il corpo di un modulo viene eseguito quando l'app si carica, ed è
lì che si registrano gli handler. Da quel momento `benvenuto` scatta a ogni collegamento.

Tre cose che quel file dichiara e che non sono facoltative:

1. **`module saluta;`** in testa, e il nome deve combaciare con il percorso del file. Il caricatore
   lo controlla prima di eseguire qualunque cosa.
2. **ogni `import` ha un alias.** `import "core.mc.text" as text;` — senza `as` è un errore. Due
   librerie non possono litigare su un nome perché il nome lo scegli tu, in questo file.
3. **niente è disponibile per magia.** `sqrt(4)` senza import non funziona: l'errore ti dice in
   quale modulo vive `sqrt` e quale riga aggiungere.

## Provare senza salvare un file

```
/blossom script run 2 + 3 * 4
/blossom script run import "core.math" as math; math.sqrt(16)
```

`run` valuta in un interprete pulito, che non vede le app caricate. Per entrare **dentro** un'app,
con le sue funzioni e il suo stato:

```
/blossom script in saluta benvenuto(entity.player())
```

## Tutti i comandi

| Comando | Cosa fa |
| --- | --- |
| `/blossom script run <codice>` | valuta codice al volo, in un interprete pulito |
| `/blossom script in <app> <codice>` | valuta codice dentro un'app caricata |
| `/blossom script load <app>` | carica `<mondo>/blossom/<app>.bs`, o il pack `<mondo>/blossom/<app>/` |
| `/blossom script reload <app>` | ricarica un'app già caricata |
| `/blossom script unload <app>` | scarica un'app e i suoi handler di evento |
| `/blossom script list` | cosa è caricato e cosa c'è su disco |
| `/blossom pack update <app>` | ri-risolve le dipendenze e riscrive `blossom.lock` |
| `/blossom profile run <codice>` | misura dove va il tempo, funzione per funzione |
| `/blossom profile in <app> <codice>` | come sopra, dentro un'app caricata |
| `/blossom profile start <app>` · `stop <app>` | misura un'app viva, handler compresi |

Ricaricare un'app **toglie i suoi handler prima di rieseguire il file**: non restano due copie dello
stesso listener attaccate all'evento. È il motivo per cui registrare gli handler nel corpo del
modulo, come nell'esempio, è la cosa giusta da fare.

## Da un file a un progetto

Appena il file cresce, diventa una cartella con un manifesto:

```
<mondo>/blossom/mia-app/
├── blossom.json
└── src/
    ├── main.bs           ← module main
    └── conti/
        └── registro.bs   ← module conti.registro
```

```json
{
  "name": "mia-app",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "1.0"
}
```

Si carica con lo stesso comando: `/blossom script load mia-app`. Come si dividono i moduli, cosa
esportano e come si dipende da un altro progetto è il [capitolo 6](06-moduli.md).

## Dove finiscono i dati

| Cosa | Dove |
| --- | --- |
| il codice dell'app | `<mondo>/blossom/<app>.bs` o `<mondo>/blossom/<app>/` |
| i dati delle app (radice `data` di [`core.io`](../api/core.io.md)) | `<mondo>/blossom/data/` |
| i dati condivisi fra app (radice `shared`) | `<mondo>/blossom/shared/` |
| le dipendenze scaricate da un registry | `<mondo>/blossom/.cache/` |
| la configurazione del server ([capitolo 12](12-configurazione.md)) | `config/blossomscript.json` |

Tutto quello che uno script apre sta sotto la cartella del mondo: le radici di
[`core.io`](../api/core.io.md) si dichiarano lì dentro e non possono uscirne, e il resto del disco
del server non è raggiungibile da nessuna configurazione.

Un'app non vede i dati di un'altra per sbaglio: per passarsi qualcosa ci sono i file `shared_*` e
gli eventi di applicazione ([capitolo 8](08-eventi.md)).
