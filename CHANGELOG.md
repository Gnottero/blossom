# Changelog

Il formato segue [Keep a Changelog](https://keepachangelog.com/it/1.1.0/).

## Non rilasciato

### Aggiunto

- **`core.http`**: richieste HTTP verso host dichiarati — `get`, `post`, `request`, `get_json`,
  `post_json`, `hosts`, `encode_url`, `decode_url`, `encode_query`. Nessun host è raggiungibile
  finché non è dichiarato in `config/blossomscript.json`, i redirect (spenti per default) ripassano
  dall'allowlist ad ogni salto, e una richiesta sul thread del server è un errore che dice di
  metterla in un `task`. Uno stato fuori dai 2xx è una risposta, non un errore; i guasti di rete
  arrivano come `http_exception`. Documentazione in `docs/api/core.http.md`.
- Un modulo di libreria può esportare un simbolo con un nome diverso da quello della sua
  implementazione: `http.get` esiste anche se `get` è già di liste e mappe.
- La sezione **`watchdog`** in `config/blossomscript.json`: `timeout_ms` e `max_depth` per le app che
  non dichiarano limiti propri.
- **`docs/guide/12-configurazione.md`**: tutte le chiavi di `blossomscript.json` in un posto solo,
  con tipo, default ed effetto.
- Gli errori che nascono fuori da un comando — handler di evento, chiamate rimandate con `schedule`,
  callback — compaiono **in chat agli operatori** oltre che nel log. Lo stesso errore ripetuto viene
  mostrato una volta ogni cinque secondi, con il conto di quelli soppressi: un handler rotto dentro
  una macchina a pistoni non rende più la chat illeggibile.
- `"autoload": true` nel manifesto: il pack si carica da solo appena il mondo è aperto, prima che
  venga generato `server_starts`.

### Cambiato

- **Un'app è una sola per tutto il server, come un plugin.** Sparita la distinzione fra app globale e
  app per giocatore: con lei escono gli host per giocatore, i global condivisi per app (quel ruolo
  ce l'hanno i binding di modulo) e le chiavi `system_info('app_scope')` e `system_info('app_players')`.
  Vedi DECISIONI.md D-087.
- **I nomi di Scarpet escono dall'API e dai messaggi.** `system_info('scarpet_version')` diventa
  `system_info('blossom_version')`, il marcatore NBT `__scarpet_marker` diventa `__blossom_marker`,
  la chiave `scarpet_custom` di `plop` diventa `blossom_custom`, e un data pack generato si dichiara
  di `'source' -> 'blossom'`. Vedi DECISIONI.md D-088.

- **Le radici di `core.io` stanno dentro la cartella del mondo.** I loro percorsi si scrivono
  relativi al mondo; uno assoluto vale solo se punta comunque dentro, altrimenti la radice viene
  scartata con un avviso nel log. Il confine è ricontrollato dopo aver risolto i link simbolici, così
  nemmeno una radice che è un link in uscita passa. Una configurazione che puntava fuori dal mondo va
  riscritta: quelle radici ora non aprono niente.


### Guida ed esempi

- **`docs/guide/`**: undici capitoli che spiegano il linguaggio invece di elencarne le
  funzioni — primi passi, valori, operatori, funzioni, controllo di flusso, moduli, errori, eventi,
  concorrenza, prestazioni, ricette.
- I frammenti della guida che non richiedono Minecraft sono **eseguiti dai test**
  (`GuideSnippetsTest`), insieme al modulo di logica pura dell'esempio `vena-viva`: una guida che
  mente rompe la build.
- Due nuovi progetti d'esempio: **`contapassi`** (un file: eventi, HUD, stato su disco) e
  **`vena-viva`** (quattro moduli, task fuori dal tick, sezione critica, evento fra app).

### Rimosso

- `core.mc.world.relight` e `core.mc.world.reset_chunk` **non esistono piu'**: erano dichiarate,
  documentate e implementate come `return null` — l'implementazione vera era commentata da prima
  dell'estrazione. Una funzione che non fa quello che dice e' peggio di una che non c'e'.
- Codice morto: due classi (`GlocalFlag`, `LoadException`), ventisette metodi che nessuno chiamava
  (avanzi del layer Carpet in `access/Host`, del sistema di import di Scarpet in `ScriptHost`, e
  alcuni convertitori mai usati), i blocchi di codice commentato rimasti da Scarpet in `WorldTools`,
  `EntityValue`, `WorldAccess` e `Expression`, e 85 import inutilizzati.
- `system_info('world_carpet_rules')`: rispondeva una mappa vuota per definizione, perché Carpet non
  c'è, quindi non informava di niente.
- Gli avvisi di deprecazione ereditati, e la macchina che li emetteva (`issueDeprecation`,
  `Expression.alias`). Una funzione o c'è o non c'è: a questo punto del progetto non c'è niente da
  cui migrare, e le funzioni che portavano l'avviso (`world_time`, `seed`, `view_distance`,
  `material`, `property`, ...) sono documentate e restano.

### Corretto

- Le nuvole si vedevano **intere** dietro un riempimento traslucido, come se la forma non ci fosse:
  il gioco disegna tutte le fasi traslucide prima delle nuvole, e una faccia traslucida non scrive la
  profondità. Ora il riempimento traslucido è disegnato dopo le nuvole, e le copre in proporzione al
  suo alfa — dietro una sfera al 50% la nuvola si vede a metà.
- `core.math.sign`, `core.math.exp` e `core.list.reverse` erano dichiarati dai rispettivi moduli ma
  non implementati: chiamarli rispondeva `Function 'sign' is not defined yet`. Ora esistono.
- `_trace` dentro un `catch` sollevava un `NullPointerException` interno quando nello stack c'era una
  funzione senza modulo (codice da `/blossom script run`).
- Il messaggio di "tipo di eccezione sconosciuto" era in italiano; i messaggi d'errore sono in
  inglese.
- Documentazione allineata al comportamento vero: `put` su una lista **sostituisce** di default
  (`'insert'` infila, `'extend'` concatena), `has` su una lista chiede dell'**indice** e non
  dell'appartenenza, una lista pigra non conosce la propria lunghezza, e `try` non cattura gli errori
  del linguaggio. L'esempio `torre-guardiana` usava `_e` per il valore catturato, che non esiste: è
  `_`.

### API di gioco completa

- L'API di Scarpet e' stata portata per intero: **156 funzioni in 13 moduli `core.mc.*`** (blocchi,
  mondo, scansione volumi, entita', oggetti, schermate, punteggi, NBT, testo, effetti, eventi, file,
  sistema). L'API "curata" della Fase 6 (`block.get`, `world.time`, `player.all`) e' stata ritirata:
  i suoi nomi corti coprivano i simboli di libreria (`list.get`) e copriva un quinto delle funzioni.
- **50 eventi** con catalogo tipizzato: arieta' e annullabilita' dichiarate, controllate al momento
  della registrazione, e documentate in `docs/api/core.mc.event.md`. Piu' 5 eventi attaccabili
  a una singola entita' con `entity_event`.
- Un handler annulla l'azione restituendo un valore **vero**: la stringa magica `'cancel'` non esiste
  piu'.
- Eventi di applicazione: `handle_event` dichiara un evento inventato, `signal_event` lo genera. Due
  app possono parlarsi senza conoscersi.
- `schedule(tick, funzione, ...)` rimanda una chiamata a un tick futuro.
- All'avvio il mod verifica che ogni simbolo dichiarato in un modulo esista davvero: un import che
  passa e una chiamata che fallisce dopo non e' un errore che si vuole poter avere.

### Pack e file

- `/blossom script load <nome>` carica anche i **pack**: una cartella con dentro `blossom.json`, il
  suo `src/` e tutti i moduli che l'entrypoint importa. La cartella del mondo viene riletta ad ogni
  elenco, quindi un pack copiato a server acceso e' caricabile subito.
- Le estensioni dei sorgenti passano da `.sc`/`.scl` a **`.bs`/`.bsl`**.

## [0.1.0] — non ancora rilasciata

Prima versione: estrazione dell'interprete di Scarpet e ridisegno del linguaggio attorno a moduli,
errori tipizzati e una API di gioco curata.

### Aggiunto

- **`blossom-core`**: l'interprete come modulo java puro, senza alcun import `net.minecraft.*`,
  testabile e misurabile senza scaricare il gioco. Facciata `Interpreter` con builder per moduli
  importabili, storage su disco e registrazione di API esterne.
- **Sistema di moduli**: `module`, `import "..." as alias`, `pub`, `fn`, `let`, `let mut`.
  Risoluzione statica dei simboli, rilevamento degli import ciclici, manifesto `blossom.json` con
  dipendenze a percorso locale, layout `src/`.
- **Errori tipizzati** con categoria, file, riga, colonna, stack che attraversa i moduli e
  suggerimento fuzzy: `ParseError`, `ImportError`, `UndefinedSymbolError`, `TypeMismatchError`,
  `ArityError`, `IndexOutOfBoundsError`, `NullReferenceError`, `RuntimeError` con sottocodice.
- **Standard library a pacchetti**: `core.math`, `core.string`, `core.list`, `core.map`,
  `core.json`, `core.task`, `core.system`, più un prelude minimo.
- **`blossom`**: mod Fabric indipendente da Carpet, comando `/blossom script`, e i moduli di
  gioco `core.mc.block`, `core.mc.world`, `core.mc.entity`, `core.mc.player`, `core.mc.event`.
- **Eventi**: `tick`, `player_joins`, `player_leaves`, `block_dispenses`, `bell_rings`. Gli handler
  si registrano passando la funzione come valore e spariscono quando l'app viene ricaricata.
- Documentazione per ogni modulo in `docs/api/`, progetto d'esempio in
  `examples/torre-guardiana`, benchmark di riferimento in `BENCHMARK.md`.

### Cambiato rispetto a Scarpet

Rottura di compatibilità voluta e autorizzata: le app `.sc` scritte per Scarpet non girano.

- le variabili `global_` non sono più il modo di condividere stato fra file: lo sono i moduli
- `import(nome, simboli...)` come funzione non esiste più, l'import è una dichiarazione del preambolo
- le funzioni di libreria non sono più globali: vanno importate, e l'errore dice quale import serve
- il nome nudo di una funzione ora *è* la funzione, così passarla a un altro modulo funziona sempre
- `fn`, `let`, `mut`, `pub`, `module`, `import`, `as` sono nomi riservati
- `delete` su un indice fuori dalla lista è un errore invece di cancellare l'elemento sbagliato
