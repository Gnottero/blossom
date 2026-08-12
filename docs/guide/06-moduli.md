# 6. Moduli e progetti

Un file è un modulo. Ha un nome dichiarato, uno spazio dei nomi suo, e una lista esplicita di quello
che lascia uscire. Non esiste uno spazio globale implicito, e non esistono convenzioni sui prefissi
dei nomi: quello che è pubblico lo è perché c'è scritto.

## Le tre parole chiave

```
module math.vector;                 // chi sono: deve combaciare col percorso del file

import "core.math" as math;         // cosa uso, e con che nome lo chiamo qui
import "geometria/angoli" as ang;

pub fn add(a, b) -> [a:0 + b:0, a:1 + b:1];     // esce dal modulo
fn normalizza(v) -> ...;                        // resta qui

pub let ORIGINE = [0, 0];           // anche i binding si esportano
let mut cache = {};
```

**`module`** va in testa e il nome deve corrispondere alla posizione dentro `src/`:
`src/math/vector.bs` → `module math.vector;`. È un controllo che il caricatore fa prima di eseguire
qualsiasi cosa, e serve perché il nome di un modulo sia una cosa sola, non due che possono divergere.

**`import`** ha **sempre** un alias. Non c'è la forma senza `as`: due librerie non possono litigare
sui nomi, perché il nome lo scegli tu, in questo file, e vale solo qui. Il percorso di un modulo
locale usa `/` (`"torre/costruzione"`), quello di un modulo di libreria usa `.` (`"core.mc.block"`).

**`pub`** decide cosa esce. Toccare un simbolo privato di un altro modulo è un errore **a
caricamento**, non una sorpresa a runtime:

```
UndefinedSymbolError
  Module 'lib' does not export 'secret'.
```

## Il progetto

```
mia-app/
├── blossom.json
├── blossom.lock          ← lo scrive il caricatore, non tu
└── src/
    ├── main.bs           ← module main          (l'entrypoint)
    ├── conti.bs          ← module conti
    └── torre/
        ├── costruzione.bs    ← module torre.costruzione
        └── registro.bs       ← module torre.registro
```

```json
{
  "name": "mia-app",
  "version": "0.1.0",
  "entrypoint": "src/main.bs",
  "api_level": "1.0",
  "autoload": true,
  "dependencies": {
    "utils": { "path": "../utils", "version": "^1.2.0" }
  },
  "watchdog": { "timeout_ms": 5000, "max_depth": 512 }
}
```

| Campo | Obbligatorio | Cosa fa |
| --- | --- | --- |
| `name` | sì | il nome del progetto |
| `entrypoint` | sì | il file da cui parte il caricamento |
| `version` | consigliato | semver del progetto; è quello che gli altri vincolano |
| `api_level` | no (`1.0`) | quale API di Blossom serve |
| `autoload` | no (`false`) | il pack si carica da solo appena il mondo è aperto |
| `dependencies` | no | vedi sotto |
| `watchdog` | no | limiti di esecuzione, o `false` per toglierli |

Una cartella così, copiata sotto `<mondo>/blossom/`, è un **pack**: `/blossom script load mia-app`.

## Partire da soli

`"autoload": true` vuol dire che quel pack non ha bisogno che nessuno dia `/blossom script load`:
entra appena il mondo è aperto, su un server dedicato come in singleplayer. È la dichiarazione di
un'app che deve esserci sempre — un guardiano, un registro, un comando custom — e non di una che stai
provando.

Il caricamento avviene **prima** che venga generato `server_starts`, quindi un'app che parte da sola
può ascoltare anche quell'evento. Se non carica (manifesto rotto, modulo mancante, errore nel corpo)
l'errore finisce nel log e in chat agli operatori, e il resto del mondo parte lo stesso.

Le app in un file solo (`<nome>.bs`, senza cartella e senza manifesto) non hanno dove dichiararlo:
per farne partire una da sola, dalle un `blossom.json` e falla diventare un pack.

## Ordine di caricamento e cicli

I moduli vengono **tutti registrati prima** che il primo corpo giri. Due moduli che si chiamano a
vicenda sono un caso normale, non un errore:

```
// a.bs
module a;
import "b" as b;
pub fn ping(n) -> if(n <= 0, 'fine', b.pong(n - 1));

// b.bs
module b;
import "a" as a;
pub fn pong(n) -> a.ping(n - 1);
```

Quello che non si può fare è **leggere**, mentre un modulo si carica, un binding di un modulo dello
stesso ciclo che non ha ancora eseguito il proprio corpo: lì uno dei due deve venire per forza
prima, e l'errore lo dice invece di dare un `null` misterioso.

```
ImportError

  Module 'b', imported as 'b', has not finished loading yet: 'from_b' does not exist yet. This
  happens with circular imports, where one of the two modules has to run first. Move the use inside
  a function, which is evaluated when you call it and not while the module loads.
```

Regola pratica: nel corpo di un modulo fai solo dichiarazioni e registrazioni di handler; il lavoro
vero mettilo dentro funzioni.

## Dipendere da un altro progetto

Tre forme, che si escludono a vicenda:

```json
"dependencies": {
  "utils":  { "path": "../utils", "version": "^1.2.0" },
  "torre":  { "pack": "torre", "version": "^1.0.0" },
  "vector": { "version": "^2.0.0" }
}
```

- **`path`** — una cartella, di solito accanto al progetto: è il modo di lavorare su due progetti
  insieme.
- **`pack`** — un altro pack installato nello stesso mondo, sotto `<mondo>/blossom/`: è il modo di
  dipendere da qualcosa che un amministratore ha messo lì, senza inventarsi percorsi che sulla sua
  macchina non esistono.
- **solo `version`** — dal registry, se il server ne ha uno configurato. Spento per default.

Il `version` è un vincolo [semver](https://semver.org) e viene verificato davvero: `^1.2.0` accetta
da 1.2.0 a 2.0.0 esclusa, `~1.2.0` solo le patch, `>=1.2, <2` combina, `*` accetta tutto. Sotto la
1.0.0 ogni incremento può rompere, quindi `^0.2.3` si ferma alla 0.3.0. Un pre-release (`1.0.0-rc1`)
non viene mai scelto da un vincolo che non lo nomina.

Le dipendenze sono **transitive**, e ognuna ha il proprio spazio dei nomi: dentro `utils`,
`import "math/vec"` significa quello che dice il manifesto di `utils`, non il tuo. Due progetti
possono chiamare `math` due librerie diverse senza sapere niente l'uno dell'altro.

Se la versione trovata non soddisfa il vincolo, l'app non parte:

```
CompatibilityError
  in /mondo/blossom/app/blossom.json:1:1

  Dependency 'utils' (path ../utils) is at version 2.0.0, which does not satisfy the constraint
  ^1.2.0 declared in the manifest.
```

## Il lock

Un vincolo come `^1.0.0` è una domanda, e la risposta cambia nel tempo. La scelta fatta viene scritta
in `blossom.lock` accanto al manifesto, così la stessa app caricata su due server prende la stessa
versione:

```json
{
  "format": 1,
  "dependencies": {
    "vector": { "source": "registry", "version": "1.4.0", "sha256": "9f86d0..." },
    "torre":  { "source": "pack", "version": "1.0.0" }
  }
}
```

Per cambiare idea: `/blossom pack update <app>`. Il lock si aggiorna da solo in un caso solo —
quando la versione bloccata non soddisfa più il vincolo, cioè quando sei stato tu a riscrivere il
manifesto.

Le dipendenze **a percorso non vengono bloccate**: è la cartella accanto, che stai modificando
adesso. L'impronta `sha256` c'è solo per quelle prese dal registry, che sono le uniche il cui
contenuto arriva da fuori.

## Il registry

Spento per default; si accende in `config/blossomscript.json`:

```json
{ "registry": { "directory": "/srv/blossom-packs" } }
{ "registry": { "url": "https://esempio.org/blossom/" } }
```

**A cartella**: `<radice>/<nome>/<versione>/`, e dentro un progetto normale. Nessun server da
mantenere. **Su http**: un `index.json` statico e degli zip, con `sha256` **obbligatorio** — un
archivio scaricato ed eseguito senza controllo è codice arbitrario su un server. Ogni versione si
scarica una volta e resta in `<mondo>/blossom/.cache/`.

## `api_level`

`api_level` è `MAJOR.MINOR` e descrive **l'API di Blossom**, non l'app: la versione dell'app è
`version`, ed è semver. Sono due numerazioni diverse apposta — una dice "che cosa sono io", l'altra
"di che cosa ho bisogno".

- **MAJOR** cambia quando qualcosa che funzionava smette di funzionare.
- **MINOR** cambia quando si aggiunge.

Un'app gira se il MAJOR combacia e il suo MINOR non supera quello del mod. Non dichiararlo significa
`1.0`, cioè "non uso niente di recente".

## Come si divide un'app

Una divisione che regge, dall'esperienza dei due esempi in `examples/`:

| Modulo | Cosa ci sta |
| --- | --- |
| `main` | gli `event.on`, il `print` di avvio, le funzioni pubbliche che l'admin chiama con `script in` |
| un modulo per dominio (`torre.costruzione`, `vena.geologia`) | la logica, senza sapere niente di eventi |
| un modulo di stato (`registro`) | i `let mut`, il salvataggio su disco, e le funzioni per leggerlo |

Il pezzo che tocca il mondo e il pezzo che decide **cosa** fare stanno separati: il secondo si prova
con `/blossom script in <app> ...` senza dover far succedere l'evento vero.
