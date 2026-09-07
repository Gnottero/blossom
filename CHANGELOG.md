# Changelog

Il formato segue [Keep a Changelog](https://keepachangelog.com/it/1.1.0/).

## 1.0.0 — non rilasciato

`api_level` sale a **2.0**: e' una rottura MAJOR, e un'app che dichiara `api_level: "1.0"` non
carica piu'. E' voluto: e' meglio di un'app che carica e sbaglia. Un manifesto che **non** dichiara
il campo vale invece il MINOR piu' basso del MAJOR corrente - oggi `2.0` - cioe' "non uso niente di
recente", che e' quello che il campo facoltativo ha sempre voluto dire.

### Eventi

- **`player_clicks_item_on_item(player, carried, other, slot, action)`**: un oggetto tenuto sul
  cursore cliccato su un altro oggetto in un contenitore aperto — il gesto a cui risponde il bundle,
  qui per ogni oggetto. `carried` e `other` sono le due pile come stanno prima del click, `slot` e'
  il numero dello slot nel contenitore aperto e `action` e' `'left'` o `'right'`. Parte solo se
  entrambe le pile ci sono, e annullarlo vuol dire che il click e' stato gestito: niente scambio,
  niente unione, le due pile restano come stavano.

- **Tutti gli eventi di gioco sono annullabili tranne tredici**: la vita del server e delle app
  (`server_starts`, `server_shuts_down`, `app_reloading`, `app_reloaded`), i tre `tick`, i due capi
  di una sessione (`player_connects`, `player_disconnects`), `player_dies` e i tre eventi dei chunk.
  Per tutti gli altri l'aggancio e' stato spostato sulla chiamata che compie davvero l'azione,
  quindi annullare ferma il gioco e non solo l'evento. La colonna "Cancellable" del catalogo e
  `event.describe(nome)` restano la fonte.

- **Gli eventi che partono a cose fatte fermano quello che resta**, e la voce del catalogo dice
  esattamente cosa: `player_places_block` rimette al suo posto quello che c'era e non consuma
  l'oggetto; `explosion_outcome` parte fra le due meta' dello scoppio, quindi salva i blocchi ma non
  le entita', gia' colpite; `player_interacts_with_block` ferma l'advancement e il movimento del
  braccio, non richiude il baule; `player_respawns` lascia il giocatore sulla schermata di morte.
  `player_escapes_sleep` e' annullabile per coerenza, ma in quel ramo il gioco non fa niente: e'
  detto senza girarci intorno anche nella documentazione.

  Dove la cancellazione andrebbe **inventata** invece che fermata, l'evento resta non annullabile e
  rimanda a quello chiesto prima: `player_dies` a `player_takes_damage` — a vita gia' a zero,
  annullare vorrebbe dire anche decidere quanta vita restituire — e i due capi di una sessione alla
  porta da cui si entra.

- **Rottura, tre eventi cambiano momento** per poter essere annullati:
  - `player_picks_up_item` parte **prima** che l'oggetto entri nell'inventario, quindi `item` e' la
    pila offerta e non piu' la parte che ci stava;
  - `player_changes_dimension` parte **prima** dello spostamento: `to_pos` e' la destinazione decisa
    dal gioco e non e' piu' `null` uscendo dall'End;
  - `player_trades` parte prima che i costi vengano spesi.

- **Attenzione al `false` finale.** Un handler che finisce con un `print` restituisce un valore vero,
  e su un evento diventato annullabile questo ora ferma l'azione: su `statistic` la statistica non
  viene contata, su `player_picks_up_item` l'oggetto resta per terra.

### Il contenuto di gioco dentro il pack

Un pack puo' avere una cartella **`data/`** con l'albero di un datapack vero. Viene data al gioco da
dentro il pack: chi installa copia una cosa sola, e non c'e' niente da mettere in
`<mondo>/datapacks/`. Ci passano tutte le categorie — ricette, loot table, tag, advancement, e anche
i registri dinamici e il worldgen, che il gioco fissa all'apertura del mondo — perche' l'aggancio e'
la sorgente dei pack, non un mixin per categoria.

- **`core.mc.data`**: `write(id, valore)` e `write_text(id, righe)` per **generare** il contenuto
  invece di ripeterlo.
- **`/blossom data generate <app>`** chiama la `generate()` dell'app e scrive json veri, piu' il
  `pack.mcmeta`. Su comando e non all'apertura del mondo, cosi' il generatore e' codice normale con
  il server acceso.
- **`/blossom data list <app>`** dice cosa c'e' nella `data/` e cosa di quello e' generato.
- **`/blossom data export <app>`** ne fa uno zip in `<mondo>/blossom/.export/`: un datapack che gira
  anche senza Blossom.
- `data/.blossom-generated` elenca cosa hanno prodotto i generatori, e la rigenerazione riscrive e
  ripulisce **solo quello**: un json scritto a mano nella stessa cartella non viene mai toccato.

Un pack Blossom sta in fondo alla lista dei datapack e non si puo' spegnere: in fondo perche' un
datapack vero deve poter correggere una ricetta rotta di un'app di cui non si ha il sorgente, non
spegnibile perche' il codice dell'app da' per esistente il proprio contenuto.

### Sintassi

- **Funzioni senza nome**: `fn(a, b) -> a + b`. Catturano il frame in cui nascono, quindi vedono
  quello che le circonda senza dichiararlo con `outer`. `fn nome(...)` dichiara, `fn(...)` e' un
  valore: a distinguerli e' la parentesi subito dopo `fn`.
- **Chiamata diretta di un valore-funzione**: `f(x)` con `f` legata a una funzione. `call(f, x)`
  resta, e serve per chiamare per nome calcolato a runtime.
- **Stringhe interpolate**: `"ciao ${nome}"`. L'apice singolo resta letterale, il doppio interpola.
- **`match`**: `match(x, 0 -> 'zero', d: num -> ..., else -> ...)`. Il soggetto si valuta una volta
  sola, e i corpi dei bracci non presi non si valutano affatto.
- **L'operatore `|>`**: `lista |> list.filter(f) |> list.map(g)`. Si risolve mentre si legge il
  codice e non costa niente a runtime.
- **Valori di riserva e argomenti con nome**: `fn f(a, b = 1)` e `f(1, b = 2)`. Il valore di riserva
  si rivaluta ad ogni chiamata che lo usa. Gli argomenti con nome vanno in fondo, e l'ordine e' un
  controllo di caricamento.
- **Cicli e `catch` accettano una lambda** oltre all'espressione con `_`:
  `list.map(l, fn(x) -> x * 2)`, `try(x, 'exception', fn(e) -> ...)`. In `reduce` una lambda a due
  argomenti legge `(accumulatore, elemento)`. La forma corta con `_` resta, ed e' la piu' veloce.
- Il nome funzionale dell'operatore `~` non e' piu' `match` ma **`contains`**: `~` e' un test di
  appartenenza, e `match` serve al costrutto omonimo.

### API rinominata (rottura)

Ogni pagina di `docs/api/` ha in fondo la propria tabella `vecchio -> nuovo`. Le regole:

| Regola | Esempio |
| --- | --- |
| via lo stutter col nome del modulo | `score.scoreboard_add` -> `score.add` |
| un verbo solo per operazione | `delete`, `delete_file`, `*_remove` -> `remove` |
| una parola sola per "quanti" | `length` -> `size` |
| nessuna abbreviazione opaca | `loaded_ep` -> `loaded_for_entities`, `plop` -> `place_feature` |
| inglese americano, scelto una volta | `neighbours` -> `neighbors`, `map_colour` -> `map_color` |
| il simbolo sta nel modulo del suo primo argomento | `split` solo in `core.string`, `join` solo in `core.list` |

Tre moduli si dividono, perche' tenevano insieme cose diverse:

- `core.mc.scoreboard` -> **`core.mc.score`**, **`core.mc.team`**, **`core.mc.bossbar`**;
- gli inventari escono da `core.mc.item` in **`core.mc.inventory`**;
- le funzioni `structure*` di `core.mc.world` raggiungono i template in **`core.mc.structure`**.

### Tolto

- **`system.system_variable_get`** e **`system.system_variable_set`**: erano uno spazio globale
  condiviso da tutte le app dello stesso server, cioe' l'implicito che il linguaggio non vuole.
  Per parlare a un'altra app ci sono gli eventi fra app, per conservare dati c'e' `core.io`.
- **`core.mc.system.task_join`**: era lo stesso simbolo di `core.task`, esportato due volte.
- **`task_thread`**: registrato ma non esportato da nessun modulo, quindi irraggiungibile.

### Corretto

- **Una chiamata scritta dopo un valore ora e' una chiamata.** Le parentesi dopo `)` erano un
  raggruppamento: `f()()` rispondeva la funzione interna invece del suo risultato, in silenzio, e
  `f(1)(2)` dava un `ParseError` che parlava dello stato del validatore ("Too many numbers or
  variables"). Ne seguiva che una funzione che restituisce una funzione non era chiamabile.
  `(fn(x) -> x * 2)(21)`, `add(1)(2)` e `f()()` valgono ora quello che sembrano (vedi D-097).
- **`x |> fn(a) -> ...` si legge come una cosa sola.** Il tubo lega piu' stretto di `->`, quindi il
  soggetto finiva fra i parametri della firma e l'errore diceva "Only variables can be used in
  function signature, not 5". Le parentesi non servono piu'.
- **`_`, `_i` e `_a` non valgono zero fuori da un ciclo.** `list.map(l, fn() -> _ * 2)` rispondeva
  una lista di zeri: dentro una lambda gli impliciti non arrivano. Ora lo dice, e dice quale forma
  usare (vedi D-098).
- **`json.encode` scrive solo json.** `NaN` e infinito finivano nel testo cosi' com'erano, senza
  apici: un risultato che nessun lettore serio rilegge. Ora e' un `json_error`, filtrabile come
  quello di `decode`.
- **Un segnaposto numerico di `string.str` vuole un numero.** `string.str('%d', 'x')` rispondeva
  `'0'`. Ora dice cosa gli e' arrivato; un testo scritto come un numero passa ancora. Gli argomenti
  che avanzano sono un errore invece di sparire.
- **Un nome del linguaggio non diventa una variabile.** `fn print(a) -> a` era gia' un errore, ma
  `print = 1` passava, e lo stesso nome voleva dire due cose. Vale anche per i parametri. `l`, `m` e
  `concat`, che il tokenizer costruisce da solo per `[...]`, `{...}` e le stringhe interpolate,
  restano nomi liberi.
- **Gli spostamenti di bit contano i bit.** Java maschera l'ampiezza sui 6 bit bassi, e si vedeva
  attraverso: `bitwise_shift_left(1, 64)` valeva `1` e `(1, 200)` valeva `256`. Oltre i 64 bit ora
  non resta niente, e un'ampiezza negativa e' un errore. Le rotazioni restano definite modulo la
  larghezza, che e' quello che una rotazione e'.
- **Quello che `==` chiama uguale e' la stessa chiave.** `'1' == 1` era vero ma `{1 -> 'a'}` non si
  trovava con `'1'`, perche' il codice hash era quello del tipo: il contratto di `HashMap` rotto.
  Ora l'hash segue l'uguaglianza (vedi D-099).
- **I nomi delle funzioni dell'utente non sono piu' quelli delle built-in.** `fn sum(...)`,
  `fn get(...)`, `fn map(...)`, `fn size(...)` fallivano alla definizione: il controllo guardava
  l'elenco piatto delle implementazioni, 160 nomi, di cui 56 senza un modulo da suggerire ("'sum'
  is not available without an import", e non c'era niente da importare). Un nome senza qualificatore
  e' sempre dell'utente; la libreria si raggiunge per alias, quindi non c'era niente da mascherare.
  Riservato resta il solo prelude (`if`, `loop`, `print`, `try`, ...), e usarne uno come nome di
  funzione lo dice invece di lamentarsi di `->`.
- **Un manifesto senza `api_level` non caricava.** Il default era scritto `1.0` a mano mentre il
  runtime forniva `2.0`: adesso e' il MINOR piu' basso del MAJOR corrente, come dice il campo.
- **Il costante piegato a parse time non salta piu' il `try`.** Una chiamata pura con argomenti tutti
  costanti veniva eseguita mentre il codice si leggeva: `try(json.decode('{bad'), 'json_error', ...)`
  non catturava niente, `if(false, json.decode('{bad'), 'ok')` esplodeva sul ramo morto, e una
  funzione che conteneva quella riga faceva fallire il **caricamento del modulo** senza essere mai
  chiamata. Se il calcolo anticipato fallisce, adesso si rinuncia a piegarlo e la chiamata fallisce
  dove e' scritta.
- **L'eccezione di un task arriva a chi lo aspetta.** `task.wait` restituiva `null` e l'errore finiva
  solo nel log, quindi un `try` intorno non vedeva niente - il contrario di quello che
  `core.task.md` prometteva. Il log resta, per il task che nessuno aspetta.
- **Il tokenizer non esce piu' dalla stringa.** Uno spazio in fondo a un segnaposto (`"${1 }"`) o una
  graffa dentro (`"${ {'k' -> 1} }"`) davano una `StringIndexOutOfBoundsException` cruda: il
  controllo del limite veniva dopo la lettura invece che prima. E `\${` adesso e' un `${` letterale,
  che prima non si poteva scrivere.
- **Firme controllate quando si leggono**: `...rest` deve essere l'ultimo parametro (prima
  `fn f(a, ...rest, b)` passava e legava gli argomenti ai nomi sbagliati), un parametro non puo'
  essere dichiarato due volte, e la lambda passata a `map`/`reduce` deve avere i parametri che le
  vengono passati - `list.reduce(l, fn(a) -> a, 0)` restituiva l'accumulatore iniziale in silenzio.
- **`size` misura quello che ha una dimensione**: su un numero contava le cifre e su una lista pigra
  i caratteri della sua rappresentazione (`list.size(list.range(5))` rispondeva `14`). Ora sono due
  `TypeMismatchError`, e quello della lista pigra dice come materializzarla.
- **Niente errori interni al posto di errori del linguaggio**: `l:0 <> l:1` diceva "please report
  this issue to Blossom", e `json.decode('1e999999')` faceva uscire una `NumberFormatException` della
  JVM invece di un `json_error`.
- **I messaggi di `core.task` usano il nome che si scrive** (`task.wait`), non quello
  dell'implementazione (`task_join`).
- `modify` ed `entity_types` erano **registrati due volte**, con il corpo copiato identico: la
  seconda registrazione sovrascriveva la prima.
- `core.task` documentava `task_await` come "aspetta senza restituire il valore", che non e' quello
  che fa: prende il prossimo valore prodotto con `yield` da una coroutine. Ora si chiama `next`, e
  `wait` e' quella che aspetta la fine.
- `opacity` e `blocks_daylight` erano commentati con un "investigate": i metodi ci sono ancora, con
  un nome nuovo e senza argomenti. Implementati ed esportati.

### Strumenti

- **`tools/check-old-names.py`**: legge la tabella delle rinomine dai cataloghi stessi e verifica
  che nessun nome vecchio sopravviva in `docs/`, `examples/` e nei dati dell'editor.
- Un test verifica che **nessun simbolo esca da due moduli**, con le eccezioni dichiarate una per una.

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
