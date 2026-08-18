# 12. Configurazione del server

Tutto quello che decide un amministratore, e che un'app non può decidere da sola, sta in un file
solo: `config/blossomscript.json`, accanto agli altri file di configurazione dei mod.

Il file è **facoltativo**. Senza, il mod parte con i default: nessun registry, nessuna cartella
raggiungibile da `core.io`, nessun host raggiungibile da `core.http`, watchdog standard. Un file
illeggibile o malformato non impedisce l'avvio — viene segnalato nel log e si parte con i default,
perché un mod che rifiuta di caricare per una virgola di troppo rompe un server per una cosa che non
stava usando.

Il file si legge **all'apertura del mondo**. Cambiarlo con il server acceso non ha effetto: serve un
riavvio (in singleplayer, riaprire il mondo).

## Il file completo

```json
{
  "root": "/srv/blossom-scripts",

  "registry": { "directory": "/srv/blossom-packs" },

  "io": {
    "roots": { "condiviso": "condiviso", "log": "blossom/log" },
    "writable": ["condiviso"]
  },

  "http": {
    "hosts": ["api.esempio.org", "*.stato.esempio.org"],
    "schemes": ["https"],
    "timeout_ms": 5000,
    "max_response_bytes": 1048576,
    "follow_redirects": false
  },

  "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
}
```

Ogni sezione è indipendente e ogni sezione mancante spegne la sua parte. `root` e le quattro sezioni
sono tutto quello che esiste: una chiave che non è fra queste viene ignorata.

## `root` — da dove si caricano le app

| Chiave | Tipo | Default | Cosa fa |
| --- | --- | --- | --- |
| `root` | stringa | `<mondo>/blossom` | la cartella in cui il mod cerca app e pack |

Senza questa chiave le app stanno in `<mondo>/blossom/`, e ogni mondo ha le sue. Dichiarandola, tutti
i mondi di quel server guardano dove dici tu.

Un percorso **relativo** si risolve dentro la cartella del mondo (`"scripts"` →
`<mondo>/scripts/`); uno **assoluto** vale com'è, anche fuori dal mondo — una cartella di script
condivisa fra più mondi è il caso per cui questa chiave esiste. È diverso da `io.roots`, che non può
uscire dal mondo: lì il confine protegge dagli script, qui la scelta è di chi amministra il server e
scrive questo file.

La cartella viene creata se non c'è. Cambiarla non sposta niente: le app che stavano nella vecchia
cartella restano lì e semplicemente non vengono più viste.

## `registry` — da dove arrivano le dipendenze

Spento per default. Due forme, alternative fra loro; se ci sono entrambe vince `directory`.

| Chiave | Tipo | Default | Cosa fa |
| --- | --- | --- | --- |
| `registry.directory` | stringa | — | una cartella `<radice>/<nome>/<versione>/`, con dentro un progetto normale |
| `registry.url` | stringa | — | un `index.json` statico e degli zip, serviti via http |

Un `registry` che non dice né `directory` né `url` viene ignorato con un avviso nel log. Per la
forma dell'indice e il perché il `sha256` è obbligatorio, vedi [6. Moduli e progetti](06-moduli.md).

La cache dei download sta in `.cache/` dentro la cartella degli script, quindi segue `root`.

## `io` — le cartelle raggiungibili da `core.io`

Due radici ci sono sempre, anche senza questa sezione:

| Radice | Dove | Scrivibile |
| --- | --- | --- |
| `data` | `<mondo>/blossom/data/` | sì |
| `shared` | `<mondo>/blossom/shared/` | sì |

Sono il posto dove un'app tiene i propri dati. Non c'è nessuna separazione automatica fra un'app e
l'altra: chi vuole i suoi dati per sé usa una sottocartella con il proprio nome
(`io.write_json('data', 'contapassi/conteggi.json', ...)`), che è visibile nel punto della chiamata
invece che decisa dal mod. Le cartelle vengono create al primo avvio.

| Chiave | Tipo | Default | Cosa fa |
| --- | --- | --- | --- |
| `io.roots` | mappa nome → percorso | `data`, `shared` | le cartelle raggiungibili, una per nome |
| `io.writable` | lista di nomi | `data`, `shared` | quali di quelle radici si possono anche scrivere |

Quello che dichiari qui si **aggiunge** alle due di default; una radice dichiarata con lo stesso nome
di una di quelle la sostituisce.

**I percorsi sono relativi alla cartella del mondo.** Un percorso assoluto vale solo se punta
comunque dentro il mondo; se punta fuori, la radice viene scartata con un avviso nel log e le sue
chiamate falliscono come se non fosse mai stata dichiarata. Fuori dalla cartella del mondo c'è il
resto del disco del server, e nessuna configurazione deve poterlo aprire a uno script.

Il confine vale anche per i link simbolici: una radice che è un link fuori dal mondo, o un link
dentro una radice che punta fuori, viene rifiutata al momento della chiamata.

Un nome che compare in `writable` ma non in `roots` è quasi sempre un refuso e viene segnalato. Il
contrario — dichiarare una cartella e dimenticarsi di renderla scrivibile — si accorge da solo alla
prima scrittura.

Le funzioni sono in [`core.io`](../api/core.io.md).

## `http` — gli host raggiungibili da `core.http`

Nessun host per default: `core.http` esiste ma non raggiunge niente.

| Chiave | Tipo | Default | Cosa fa |
| --- | --- | --- | --- |
| `http.hosts` | lista di stringhe | `[]` | i pattern consentiti: nome esatto, `*.dominio`, o `*` per tutto |
| `http.schemes` | lista di stringhe | `["https"]` | gli schemi ammessi |
| `http.timeout_ms` | numero | `5000` | quanto si aspetta una risposta prima di fallire |
| `http.max_response_bytes` | numero | `1048576` | oltre questa dimensione il corpo viene rifiutato invece che letto |
| `http.follow_redirects` | booleano | `false` | se seguire i 3xx, ricontrollando l'host ad ogni salto |

`*` fra gli host e `http` fra gli schemi sono ammessi ma segnalati nel log: il primo rende ogni app
installata un canale verso qualunque server di internet, il secondo manda le richieste in chiaro.

I redirect, quando sono accesi, si fermano a cinque salti e ogni salto ripassa dall'allowlist: un
`302` non può portare uno script fuori dagli host dichiarati.

Le funzioni sono in [`core.http`](../api/core.http.md).

## `watchdog` — i limiti di esecuzione

Vale per le app che non dichiarano limiti propri nel loro `blossom.json` (vedi
[9. Concorrenza](09-concorrenza.md)).

| Chiave | Tipo | Default | Cosa fa |
| --- | --- | --- | --- |
| `watchdog.timeout_ms` | numero | `2000` | quanto può girare un blocco di codice prima di essere fermato |
| `watchdog.max_depth` | numero | `256` | quanto può essere profonda la ricorsione |

Un `timeout_ms` non positivo toglie il limite di tempo: serve solo a chi sta profilando, ed è
segnalato nel log perché su un server è il modo di far sparire un tick senza capire perché. Il
codice lanciato a mano da un comando ha comunque una soglia più larga: chi ha premuto invio sta
aspettando, e non c'è nessun evento di gioco appeso.

## Cosa **non** si configura qui

- **il manifesto di un'app** — `blossom.json`, dentro la cartella dell'app: dipendenze, entrypoint,
  `autoload`, limiti propri. È l'app che si descrive, non il server che la governa.
- **quali app partono da sole** — è `"autoload": true` nel manifesto dell'app.
- **i permessi dei comandi** — sono quelli di Minecraft, per livello di operatore.
