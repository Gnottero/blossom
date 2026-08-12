# Blossom per VS Code

Supporto editor per [Blossom](../BLOSSOM.md): evidenziazione, completamento sull'API vera, e gli
errori che si possono dire senza eseguire niente — import senza alias, moduli e simboli che non
esistono, eventi sbagliati, nomi di modulo che non combaciano col percorso.

## Cosa fa

| | |
| --- | --- |
| **Evidenziazione** | preambolo, `fn`/`let`/`pub`, tipi sui parametri, `_`/`_i`/`_a`, stringhe, commenti |
| **Completamento** | i moduli dentro `import "…"`, i simboli dopo `alias.`, i nomi degli eventi dentro `event.on('…')`, le funzioni e i binding del file |
| **Hover** | firma e descrizione di ogni funzione di libreria, con il modulo da cui viene |
| **Firma di chiamata** | i parametri della funzione che si sta scrivendo, di libreria o propria |
| **Vai alla definizione** | sulle funzioni del file, e attraverso gli import dentro `src/` |
| **Struttura del file** | modulo, funzioni (`pub` marcate), binding |
| **Diagnostiche** | import senza alias, alias duplicato, modulo `core.*` inesistente, simbolo non esportato, funzione di libreria usata senza importarla, evento sconosciuto, nome di modulo diverso dal percorso |
| **Correzioni rapide** | aggiungi l'import mancante, sostituisci col nome suggerito, rinomina il modulo |
| **`blossom.json`** | schema completo: campi, semver, forme delle dipendenze, `watchdog` |

Due comandi, dalla palette:

- **Blossom: apri la documentazione del modulo** — apre `docs/api/<modulo>.md`, se il repo è
  nel workspace
- **Blossom: copia il comando di caricamento dell'app** — mette `/blossom script load <app>` negli
  appunti, deducendo il nome dall'albero del file

## I dati dell'API non sono scritti a mano

`data/api.json` è generato dalle sorgenti del mod: i moduli e i simboli da `StdLib.java` e
`BlossomApi.java`, gli eventi (nome, arità, parametri, annullabilità) da `Event.java`, le firme e le
descrizioni brevi da `docs/api/`. Quando l'API cambia:

```
node tools/generate-api.js          # dalla cartella dell'estensione, col repo sopra di essa
node tools/generate-api.js /percorso/del/repo
```

Un simbolo aggiunto al mod compare nel completamento senza che nessuno lo ricopi.

## Prestazioni

Nessun language server, nessun processo separato, **zero dipendenze** e nessuno step di build: è
JavaScript che gira nel processo dell'estensione.

- l'attivazione registra i provider e basta; `data/api.json` (~78 KB) si legge alla prima richiesta
- l'indice di un file è una passata sui caratteri più qualche regex su una copia in cui commenti e
  stringhe sono spazi, ed è in cache per versione del documento
- le diagnostiche sono debounced a 300 ms: si analizza quando si smette di scrivere
- sui progetti in `examples/` l'analisi completa di un file sta sotto il millisecondo

Le diagnostiche segnalano solo ciò di cui l'estensione è certa: niente controllo di tipi, niente
arità sulle funzioni dell'utente. Su tutti i file di `examples/` non produce nessun avviso.

## Installazione

Durante lo sviluppo: apri questa cartella in VS Code e premi <kbd>F5</kbd> (Extension Development
Host).

Per installarla davvero:

```
cd vscode-blossom
npx --yes @vscode/vsce package
code --install-extension blossom-0.1.0.vsix
```

## Test

```
node test/run.js
```

Girano senza VS Code e senza `npm install`: `require('vscode')` è servito da uno stub in
`test/vscode-stub.js`.

## Impostazioni

| Impostazione | Default | Cosa fa |
| --- | --- | --- |
| `blossom.diagnostics.enabled` | `true` | spegne le sottolineature lasciando completamento e hover |

## Licenza

MIT, come il resto del progetto.
