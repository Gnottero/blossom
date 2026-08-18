# Blossom

Un linguaggio di scripting per Minecraft, e il mod Fabric che lo esegue. È costruito attorno a tre
cose: **moduli veri**, **errori leggibili** e una **API di gioco curata** invece di una superficie
enorme e piatta.

Fa una cosa sola, eseguire codice che manipola il mondo: niente rules, niente ottimizzazioni del
server, niente strumenti di amministrazione.

L'interprete discende dal codice di Scarpet (fabric-carpet, MIT), da cui è stato estratto e
ridisegnato; l'attribuzione completa è in [`NOTICE`](NOTICE).

È **server-side**: si installa sul server e chi gioca vanilla non perde nessuna funzione. Lo stesso
jar messo anche su un client aggiunge una cosa sola — il disegno delle forme di
[`core.mc.shape`](docs/api/core.mc.shape.md), che senza client si vedono come particelle.

## Com'è fatto

```
blossom-core/     l'interprete: java puro, zero import net.minecraft.*, testabile senza il gioco
blossom/          il mod Fabric: comandi, eventi, e i moduli core.mc.*
docs/     la documentazione della libreria
examples/         progetti d'esempio completi
```

Il core non sa che esiste Minecraft. Il mod gli passa la propria API attraverso un unico aggancio
(`Interpreter.Builder.withApi`), e questo è il motivo per cui i test del linguaggio girano in un
secondo senza scaricare il gioco.

## Installazione

Serve Fabric Loader 0.19.3+ e Java 25. Costruisci il mod con:

```
./gradlew :blossom:build
```

Il jar finisce in `blossom/build/libs/`. Copialo nella cartella `mods/` del server o del client.

Per provarlo senza installare niente: `./gradlew :blossom:runServer` avvia un server di sviluppo
(la prima volta va accettato l'EULA di Mojang scrivendo `eula=true` in `blossom/run/eula.txt`).

## Il primo script

Gli script di un mondo stanno in `<mondo>/blossom/`. Un file, un'app:

```
// <mondo>/blossom/saluta.bs
module saluta;

import "core.mc.text" as text;
import "core.mc.entity" as entity;
import "core.mc.event" as event;

fn benvenuto(giocatore) -> (
    text.print(giocatore, 'benvenuto su questo server');
    false
);

event.on('player_connects', benvenuto);
print('saluta pronta');
```

In gioco:

```
/blossom script load saluta
/blossom script in saluta entity.player('all')
/blossom script run 2 + 3 * 4
```

| Comando | Cosa fa |
| --- | --- |
| `/blossom script run <codice>` | valuta codice al volo, in un interprete pulito |
| `/blossom script in <app> <codice>` | valuta codice dentro un'app caricata, che ne vede funzioni e stato |
| `/blossom script load <app>` | carica (o ricarica) `<mondo>/blossom/<app>.bs`, o il pack `<mondo>/blossom/<app>/` |
| `/blossom script reload <app>` | come `load`, sulle app già caricate |
| `/blossom script unload <app>` | scarica un'app e i suoi handler di evento |
| `/blossom script list` | cosa è caricato e cosa c'è su disco |
| `/blossom pack update <app>` | ri-risolve le dipendenze e riscrive `blossom.lock` |
| `/blossom profile run <codice>` | misura dove va il tempo, funzione per funzione |
| `/blossom profile in <app> <codice>` | come sopra, dentro un'app caricata |
| `/blossom profile start <app>` / `stop <app>` | misura un'app viva, handler di evento compresi |

## Un progetto multi-file

Un progetto è una cartella con un manifesto e una cartella `src/`:

```
mio-progetto/
├── blossom.json
└── src/
    ├── main.bs              ← module main
    └── math/
        └── vector.bs        ← module math.vector
```

```json
{
  "name": "mio-progetto",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "1.0",
  "autoload": true,
  "dependencies": {
    "shared_utils": { "path": "../shared-utils", "version": "^1.2.0" }
  }
}
```

`"autoload": true` fa caricare il pack da solo appena il mondo è aperto, senza
`/blossom script load` — prima che venga generato `server_starts`, così può ascoltare anche quello.

Il nome dichiarato in testa a ogni file deve combaciare con la sua posizione dentro `src/`: è un
controllo che il caricatore fa prima di eseguire qualunque cosa.

### Dipendenze

Un progetto può dipendere da un altro in tre modi, che si escludono a vicenda:

```json
"dependencies": {
  "utils":  { "path": "../utils", "version": "^1.2.0" },
  "torre":  { "pack": "torre", "version": "^1.0.0" },
  "vector": { "version": "^2.0.0" }
}
```

- **`path`** — una cartella, di solito accanto al progetto. È il modo di lavorare su due progetti
  insieme.
- **`pack`** — un altro pack già installato nello stesso mondo, sotto `<mondo>/blossom/`. È il modo
  di dipendere da qualcosa che un amministratore ha installato: chi scrive il manifesto non deve
  inventarsi percorsi che sulla sua macchina non esistono.
- **solo `version`** — dal registry, se il server ne ha uno configurato. Spento per default.

Il `version` è un vincolo [semver](https://semver.org) e **viene verificato davvero**: `^1.2.0`
accetta dalla 1.2.0 alla 2.0.0 esclusa, `~1.2.0` solo le patch, `>=1.2, <2` combina, `*` accetta
tutto. Sotto la 1.0.0 ogni incremento può rompere, quindi `^0.2.3` si ferma alla 0.3.0. Un
pre-release (`1.0.0-rc1`) non viene mai preso da un vincolo che non lo nomina.

Se la versione trovata non soddisfa il vincolo, l'app non si carica:

```
CompatibilityError
  in /mondo/blossom/app/blossom.json:1:1

  Dependency 'utils' (path ../utils) is at version 2.0.0, which does not satisfy the constraint
  ^1.2.0 declared in the manifest.
```

Gli import possono essere **ciclici**: due moduli che si chiamano a vicenda sono un caso normale
appena un progetto cresce. I moduli vengono registrati tutti prima che il primo corpo giri, quindi
finché le chiamate stanno dentro funzioni non c'è nessun problema di ordine. Quello che non si può
fare è *leggere*, mentre un modulo si carica, un binding di un modulo dello stesso ciclo che non ha
ancora girato — lì uno dei due deve per forza venire prima, e l'errore lo dice:

```
ImportError

  Module 'b', imported as 'b', has not finished loading yet: 'from_b' does not exist yet. This
  happens with circular imports, where one of the two modules has to run first. Move the use inside
  a function, which is evaluated when you call it and not while the module loads.
```

Le dipendenze sono **transitive** e ognuna ha il proprio spazio dei nomi: dentro `utils`,
`import "math/vec"` significa quello che dice il manifesto di `utils`, non il tuo. Due progetti
possono chiamare `math` due librerie diverse senza sapere niente l'uno dell'altro.

### Il lock

Un vincolo come `^1.0.0` è una domanda, e la risposta cambia nel tempo. Perché la stessa app caricata
su due server prenda la stessa versione, la scelta viene scritta in `blossom.lock` accanto al
manifesto:

```json
{
  "format": 1,
  "dependencies": {
    "vector": { "source": "registry", "version": "1.4.0", "sha256": "9f86d0..." },
    "torre":  { "source": "pack", "version": "1.0.0" }
  }
}
```

Da lì in poi quella versione viene riusata finché non chiedi tu di cambiarla:

```
/blossom pack update <app>
```

Il lock cambia idea da solo in un caso solo: quando la versione bloccata non soddisfa più il vincolo
del manifesto, cioè quando sei stato tu a riscriverlo.

Le dipendenze **a percorso non vengono bloccate**: è la cartella accanto, che stai modificando
adesso, e bloccarla vorrebbe dire far fallire il progetto ad ogni salvataggio. L'impronta `sha256`
c'è solo per quelle prese dal registry, che sono le uniche il cui contenuto arriva da fuori: se
cambia senza che la versione cambi, l'app non si carica.

### Il registry

Spento per default. Si accende in `config/blossomscript.json`:

```json
{ "registry": { "directory": "/srv/blossom-packs" } }
{ "registry": { "url": "https://esempio.org/blossom/" } }
```

**A cartella** — `<radice>/<nome>/<versione>/`, e dentro un progetto normale. Nessun server da
mantenere, il contenuto si ispeziona con `ls`. È la forma giusta per le librerie interne di un
server o di un gruppo.

**Su http** — un `index.json` statico e degli zip:

```json
{
  "vector": {
    "1.4.0": { "url": "vector-1.4.0.zip", "sha256": "9f86d0..." }
  }
}
```

Il `sha256` è obbligatorio: un archivio che arriva dalla rete e viene scompattato senza controllo è
codice arbitrario che gira su un server, e chiunque stia in mezzo alla connessione può sostituirlo.
Una riga senza impronta viene ignorata. Ogni versione si scarica una volta e resta in
`<mondo>/blossom/.cache/`, così un riavvio non rifà venti download e un server senza connessione
carica lo stesso le app che ha già.

### `api_level`

`api_level` è `MAJOR.MINOR` e descrive **l'API di Blossom**, non l'app — la versione dell'app è
`version`, ed è semver. Sono due numerazioni diverse apposta: una dice "che cosa sono io", l'altra
"di che cosa ho bisogno".

- **MAJOR** cambia quando qualcosa che funzionava smette di funzionare: un simbolo tolto o
  rinominato, una firma o un significato che cambia, una regola di grammatica che invalida codice
  già scritto, un evento che smette di scattare.
- **MINOR** cambia quando si aggiunge: un modulo, una funzione, un parametro opzionale, un evento.

Un'app gira se il MAJOR combacia e il suo MINOR non supera quello del mod. Le due direzioni
falliscono per motivi diversi e il messaggio lo dice: MAJOR diverso vuol dire che l'app è scritta
per un'altra API; MINOR più alto vuol dire che usa qualcosa che questa versione non ha ancora e
serve aggiornare il mod. Non dichiararlo significa `1.0`, cioè "non uso niente di recente".

Una cartella così, copiata sotto `<mondo>/blossom/`, è un **pack**: si carica con
`/blossom script load <nome-cartella>` come una qualunque app, anche a server acceso.

In [`examples/`](examples) ci sono tre progetti completi: [`contapassi`](examples/contapassi) (un
file solo: eventi, HUD, stato su disco), [`torre-guardiana`](examples/torre-guardiana) (più moduli,
simboli privati, un evento annullabile, un `try`) e [`vena-viva`](examples/vena-viva) (quattro
moduli, un task, una sezione critica, un evento fra app).

### Limiti di esecuzione

Un'app che cicla all'infinito dentro un evento non rallenta se stessa: ferma il tick, e con lui il
mondo. Per questo ogni esecuzione ha un budget, e superarlo è un errore:

```
ExecutionBudgetError[time]
  in torre.sorveglia  (src/torre.bs:12:5)

  Execution stopped after 2000 ms: the script is running too long and the server is waiting for
  it. If it really needs all that time, move it to a task() or raise 'timeout_ms' in the manifest.
```

I limiti di default sono tre, perché le tre situazioni sono diverse:

| Quando | Tempo | Profondità di chiamata |
| --- | --- | --- |
| dentro un evento di gioco | 2 s | 256 |
| codice lanciato da `/blossom script run` o `load` | 15 s | 256 |
| dentro un `task()` | nessun limite | 256 |

Un `task()` non ha scadenza apposta: gira su un thread suo, non tiene fermo il tick, ed è la cosa da
usare quando un lavoro è davvero lungo.

Un'app può dichiarare i propri limiti nel manifesto, e allora valgono in tutti e tre i casi:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` li toglie del tutto — è una scelta legittima per un'app di cui ti fidi, ma da
quel momento un suo ciclo infinito richiede il riavvio del server.

L'errore del watchdog **non è catturabile** con `try`: se lo fosse, un ciclo infinito dentro un
`try` continuerebbe a girare.

## Configurazione del server

Un file solo, facoltativo: `config/blossomscript.json`. Quattro sezioni, ognuna spenta finché non la
scrivi.

```json
{
  "registry": { "directory": "/srv/blossom-packs" },
  "io": { "roots": { "condiviso": "condiviso" }, "writable": ["condiviso"] },
  "http": { "hosts": ["api.esempio.org"] },
  "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
}
```

- **`registry`** — da dove arrivano le dipendenze che non stanno già su disco.
- **`io`** — le cartelle che [`core.io`](docs/api/core.io.md) può aprire, **in più** a `data` e
  `shared`, che ci sono sempre ed è dove le app tengono i propri dati. I percorsi sono relativi alla
  **cartella del mondo** e non possono uscirne: il resto del disco del server non è raggiungibile da
  nessuna configurazione.
- **`http`** — gli host che [`core.http`](docs/api/core.http.md) può contattare, uno per nome. Senza
  questa sezione gli script non hanno rete.
- **`watchdog`** — i limiti di esecuzione per le app che non ne dichiarano di propri.

Tutte le chiavi, con tipo e default, sono in
[12. Configurazione del server](docs/guide/12-configurazione.md).

## Il linguaggio in due minuti

```
module esempio;

import "core.list" as list;                 // l'alias e' obbligatorio
import "core.math" as math;

let mut contatore = 0;                      // stato del modulo, riassegnabile
let soglia = 10;                            // stato del modulo, immutabile

fn privata(x) -> x * 2;                     // senza 'pub' non esce dal modulo

pub fn conta() -> (
    contatore += 1;
    if(contatore > soglia, 'troppi', contatore)
);

pub fn raddoppia_tutti(l) -> list.map(l, privata(_));
```

### Tipi sui parametri

I parametri possono dichiarare un tipo. È facoltativo, uno alla volta:

```
fn distanza(a: list, b: list) -> ...
fn ripeti(quante: num, cosa) -> ...          // 'cosa' resta senza vincolo
fn applica(f: func, x: any) -> call(f, x);   // 'any' documenta senza vincolare
```

I tipi sono `num`, `str`, `bool`, `list`, `map`, `func`, `null`, `any`, più quelli del gioco:
`entity`, `block`, `text`, `nbt`, `screen`. (Si chiama `func` e non `fn` perché `fn` è già la parola
chiave con cui una funzione si dichiara.)

Un tipo scritto male è un errore **quando definisci la funzione**, non la prima volta che qualcuno la
chiama:

```
ParseError
  Unknown type 'nmu' in the signature: the types are num, str, bool, list, map, func, null, any...
  Did you mean: num?
```

Un argomento del tipo sbagliato è un errore **sul confine della funzione**, che è il punto: dice
quale parametro e cosa gli è arrivato, invece di saltare fuori tre righe dentro il corpo su
un'operazione che non c'entra.

```
TypeMismatchError
  in distanza  (src/geo.bs:4:1)

  Parameter 'a' of distanza wants a list, but got a string (due).
```

Fuori da una firma `:` resta quello che era, cioè l'accesso a un elemento: `lista:0`, `mappa:'chiave'`.

In sintesi:

- **un file è un modulo**, con il suo spazio dei nomi: niente `global_` condivisi per convenzione
- **gli import hanno sempre un alias**, quindi due librerie non possono litigare sui nomi
- **`pub` decide cosa esce**; accedere a un simbolo privato è un errore a caricamento, non a runtime
- **`fn` e `let`** al posto delle convenzioni sui prefissi dei nomi
- **tipi opzionali sui parametri**, che spostano l'errore sul confine della funzione
- **niente spazio globale implicito**: `sqrt(4)` senza import è un errore che ti dice cosa importare
- **gli errori hanno una categoria, una posizione e uno stack** che attraversa i moduli

Il resto è sintassi da linguaggio a espressioni: `for`/`while`/`map`/`filter`/`reduce`, liste,
mappe, operatori.

## Errori

```
UndefinedSymbolError
  in math.vector.add  (src/math/vector.bs:4:12)
  called from main    (src/main.bs:8:1)

  Symbol 'ad' not found in module 'math.vector'.
  Did you mean: 'add'?
```

Le categorie sono due famiglie, e la differenza conta quando si scrive un `try`:

- **errori del linguaggio** — `ParseError`, `ImportError`, `UndefinedSymbolError`,
  `TypeMismatchError`, `ArityError`, `IndexOutOfBoundsError`, `NullReferenceError`, `RuntimeError`:
  sono codice sbagliato, e `try` **non** li cattura apposta. Molti arrivano già a caricamento.
- **eccezioni lanciate** — `throw()` e le operazioni che possono fallire per colpa del mondo
  (`value_exception`, `unknown_item`, `io_exception`, `json_error`, ...): quelle si catturano, per
  tipo, con la gerarchia dei filtri.

Il dettaglio, con la tabella dei filtri, è in [`docs/guide/07-errori.md`](docs/guide/07-errori.md).

## L'estensione per VS Code

In [`vscode-blossom/`](vscode-blossom) c'è il supporto editor: evidenziazione, completamento sui
moduli e sui simboli veri dell'API, hover con le firme, e gli errori che si possono dire senza
eseguire niente (import senza alias, simboli inesistenti, eventi sbagliati, nome di modulo che non
combacia col percorso). I dati non sono scritti a mano: si rigenerano dalle sorgenti del mod con
`node vscode-blossom/tools/generate-api.js`.

## Documentazione

- [La guida](docs/guide/README.md) — undici capitoli: sintassi, moduli, errori, eventi,
  concorrenza, prestazioni, ricette
- [Panoramica dell'API](docs/api/Overview.md) — tutti i moduli
- [`DECISIONI.md`](DECISIONI.md) — le scelte di design e il loro perché
- [`INVENTARIO_FORK.md`](INVENTARIO_FORK.md) — cosa è stato portato dal fork di partenza e dove

## Licenza

MIT, come fabric-carpet da cui il core deriva. Il copyright originale è mantenuto in
[`NOTICE`](NOTICE).
