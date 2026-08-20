# Changelog

Il formato segue [Keep a Changelog](https://keepachangelog.com/it/1.1.0/).

## Non rilasciato

### Aggiunto

- I **commenti di documentazione**: `///` attaccato alla dichiarazione, con `@param`, `@returns`,
  `@throws`, `@example`, `@see` e `@deprecated`. Per l'interprete restano commenti — non c'e' niente
  di nuovo da imparare e niente che possa rompersi — e a leggerli e' l'editor: l'estensione VS Code
  li mostra passando sopra al nome, nel completamento e nell'aiuto sui parametri, e sa scrivere lo
  scheletro di un blocco con un `@param` per parametro. La convenzione e' descritta in
  `docs/guide/04-funzioni.md`.

- Gli eventi **`app_reloading`** e **`app_reloaded`**, con il nome dell'app come argomento: il primo
  scatta prima che la versione uscente venga smontata — l'ultimo momento in cui i suoi handler
  esistono ancora — il secondo dopo che il codice nuovo ha girato, quindi la versione nuova sente il
  proprio ricaricamento. Insieme sono il modo di far sopravvivere lo stato a un `reload`. Nessuno dei
  due è annullabile, e nessuno dei due scatta a un primo caricamento o a un `unload`. Vedi
  DECISIONI.md D-095.
- **`core.io`**: `read_nbt(radice, percorso)` e `write_nbt(radice, percorso, tag)`, sulle stesse
  radici e con gli stessi confini di tutto il resto del modulo.
- Due radici di `core.io` che ci sono **sempre**, senza dichiararle: `data` e `shared`, entrambe
  scrivibili, sotto la cartella del mondo. Vengono create all'avvio.
- **Documentazione estesa di tutti gli eventi** in `docs/api/core.mc.event.md`: una scheda per evento
  con quando scatta, cosa passa, cosa vuol dire annullarlo e i tranelli; più le sezioni su
  registrazione, annullamento e costo.

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

- **Un'API di file sola.** `core.io` e `core.mc.file` facevano lo stesso lavoro. `core.mc.file` non
  esiste più: `read_nbt`, `write_nbt` e `create_datapack` sono passate in `core.io`, che è l'unico
  posto da cui uno script apre un file. Le tre restano implementate nel layer di gioco - NBT è un
  formato binario di Minecraft, un datapack è un concetto del gioco - ma questo non si vede da uno
  script, perché `StdLib.extendModule` permette di aggiungere simboli a un modulo del core invece di
  dichiararne un secondo. Vedi DECISIONI.md D-094 e D-096.
- `player_command` non è più dichiarato annullabile: non lo era mai stato davvero, perché il mixin
  ignorava il valore di ritorno.

- **Il frame di una chiamata non è più una mappa a hash**, e un valore vale come nodo di sé stesso:
  spariscono le lambda che avvolgevano ogni argomento e ogni risultato. Chiamate a funzione **-24%**,
  loop aritmetico **-32%**, accesso a lista **-25%**. Vedi `BENCHMARK.md` e DECISIONI.md D-091.
- **Il nome di una variabile non vive più sul valore.** Sparisce il campo mutabile che ogni `Value`
  portava per dire a quale variabile era legato, e con lui la copia difensiva che ogni argomento
  subiva entrando in una chiamata: il nome ce l'ha il nodo che legge la variabile, dal parsing.
  Chiamate a quattro argomenti **-20%**, a un argomento **-12%**, loop aritmetico **-6%**. Vedi
  `BENCHMARK.md` e DECISIONI.md D-093.
- `docs/guide/10-prestazioni.md` rimisurato: gli ordini di grandezza tengono conto di D-089..D-093
  (~55 ns per iterazione, ~178 ns per chiamata, ~900.000 operazioni in un tick), e "cosa fa già
  l'interprete" cita la risoluzione dei nomi al parsing e il frame a due array. Nuovo: il costo per
  argomento di una chiamata, ~65 ns oltre il primo.
- `_x`, `_y` e `_z` non sono più preparate in ogni frame: le scrive `scan` prima di valutare il
  corpo, e fuori da uno scan sono variabili non definite come tutte le altre (cioè `null`, non `0`).

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

- **La cartella `<mondo>/blossom/<app>.data/`**: le app scrivono nelle radici `data` e `shared` di
  `core.io`, tenendo separati i propri dati con una sottocartella che porta il loro nome.
- **Il modulo `core.mc.file`** per intero, con `read_file`, `write_file`, `list_files`,
  `delete_file`, `load_app_data` e `store_app_data`. Tabella di migrazione in
  `docs/api/core.io.md`. Tre differenze da tenere presenti: la radice è un argomento, un file assente
  è un `io_exception` e non un `null` (`read_nbt` è l'eccezione e resta `null`), e l'estensione fa
  parte del percorso.
- **Undici funzioni di gioco che non aggiungevano niente** (DECISIONI.md D-090): `material`,
  `item_category` e `enable_hidden_dimensions` rispondevano sempre la stessa cosa; `world_time`,
  `last_tick_times`, `seed`, `view_distance` e `current_dimension` erano il duplicato di una chiave
  di `system_info`; `property` e `block_properties` sono un sottoinsieme di `block_state`;
  `create_marker` e `remove_all_markers` erano un armor stand preconfezionato, che `spawn` fa in
  modo piu' generale.

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

- **L'ordine di esecuzione dei moduli di un progetto si perdeva.** `ProjectLoader` costruiva la
  mappa dei moduli in ordine di dipendenza e poi la consegnava con `Map.copyOf`, che *non* conserva
  l'ordine di inserimento; `inDependencyOrder()` leggeva quella mappa, e `Interpreter.forProject`
  ci girava sopra per far partire i corpi. Il risultato: il corpo di un modulo poteva girare prima
  del corpo di un modulo che importa, e una chiamata `alias.funzione()` nel corpo falliva con
  `has not finished loading yet` a seconda dell'hash dei nomi. Ora la mappa resta un
  `LinkedHashMap` non modificabile, qui e nelle due copie che finiscono in `BlossomHost`. Test di
  regressione: `ModuleSystemTest.runsEveryModuleAfterTheOnesItImports`.
- `modify(giocatore, 'breaking_progress', n)` andava in `NullPointerException` quando il giocatore
  non stava rompendo niente: adesso, senza un blocco sotto il dito, non fa nulla.
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
