# 7. Errori

Un errore in Blossom ha una **categoria**, una **posizione** e uno **stack che attraversa i moduli**:

```
UndefinedSymbolError
  in math.vector.add  (src/math/vector.bs:4:12)
  called from main    (src/main.bs:8:1)

  Symbol 'ad' not found in module 'math.vector'.
  Did you mean: 'add'?
```

Il suggerimento compare quando un nome è abbastanza vicino a uno che esiste — e solo allora: se hai
scritto `qwertyuiop`, nessuno finge di sapere cosa intendevi.

## Le due famiglie

La distinzione che conta per scrivere codice non è fra le categorie, ma fra **chi le solleva**.

| | Errori del linguaggio | Eccezioni lanciate |
| --- | --- | --- |
| Chi | l'interprete e le funzioni di libreria | `throw(...)`, e le operazioni che possono fallire per colpa del mondo (json, nbt, file, item o blocco che non esiste) |
| Esempi | `ParseError`, `ArityError`, `TypeMismatchError`, `UndefinedSymbolError`, `IndexOutOfBoundsError`, `NullReferenceError`, `ImportError`, `CompatibilityError` | `user_exception`, `value_exception`, `unknown_item`, `io_exception`, `json_error`, `nbt_error`, `b64_error` |
| `try` le cattura? | **no** | sì |
| Cosa vuol dire | c'è un bug nello script | è successa una cosa prevista che può andare male |

`try` **non** cattura un `ArityError` o un `TypeMismatchError`, ed è voluto: quelli non sono
situazioni da gestire, sono codice sbagliato che deve arrivare in faccia a chi lo ha scritto, con
posizione e stack. Nasconderli dietro un `catch` generico è il modo in cui uno script sopravvive per
mesi facendo la cosa sbagliata in silenzio.

Molti errori del linguaggio arrivano prima ancora che lo script giri: nome di modulo che non
corrisponde al file, simbolo privato usato da fuori, tipo inesistente in una firma, evento che non
esiste, handler con l'arietà sbagliata. Un'app che carica è già passata da quei controlli.

## `try`

```
try(espressione);                                   // ingoia le sole eccezioni utente
try(espressione, ripiego);                          // idem, con un valore di ripiego
try(espressione, 'filtro', ripiego);                // solo quel tipo
try(espressione, 'f1', r1, 'f2', r2, ..., ripiego); // più filtri in ordine
```

Il ramo di ripiego puo' essere una **lambda**, e allora l'errore ha un nome:

```
try(lavoro(), 'exception', fn(e) -> print('non ci sono riuscito: ' + e));
try(lavoro(), 'exception', fn(e, tr) -> log(e, tr:'token'));   // il secondo e' _trace
```

E' la forma da preferire: `_` e' anche l'elemento corrente di ogni ciclo, quindi un `for` dentro un
`catch` se lo porta via, e il valore dell'eccezione va salvato a mano prima. Con un nome quel
problema non esiste.

Nella forma senza lambda, `_` resta il valore lanciato:

```
import "core.json" as json;

fn leggi_configurazione(testo) -> try(
    json.decode(testo),
    'json_error', (
        print('configurazione illeggibile: ' + _);
        {}                          // un default, e l'app parte lo stesso
    )
);
```

I filtri seguono una **gerarchia**: `json_error` sta sotto `io_exception`, che sta sotto `exception`.
Filtrare su `exception` cattura tutto quello che è catturabile; filtrare su un tipo che non c'entra
lascia passare l'eccezione al `try` di sopra.

| Filtro | Cattura |
| --- | --- |
| `exception` | tutto il catturabile |
| `user_exception` | quello che lancia `throw(valore)` |
| `value_exception` | valori non validi, e sotto di lui `unknown_item`, `unknown_block`, `unknown_biome`, `unknown_particle`, `unknown_poi`, `unknown_dimension`, `unknown_structure`, `unknown_criterion`, `unknown_screen` |
| `io_exception` | disco e formati, e sotto di lui `nbt_error`, `json_error`, `b64_error` |

Attenzione alla forma corta: `try(codice)` e `try(codice, ripiego)` senza filtro catturano **solo le
eccezioni utente**. Un `json_error` passa attraverso. Se vuoi prendere tutto, scrivilo:
`try(codice, 'exception', ripiego)`.

## `throw`

```
throw();                                    // user_exception, valore null
throw('messaggio');                         // user_exception, con un valore
throw('value_exception', 'non valido');     // di un tipo preciso
```

Il valore lanciato può essere qualunque cosa, non solo una stringa: una mappa è il modo di far
arrivare al `catch` più di un dato.

```
fn preleva(conto, quanto) -> (
    if(quanto > conto:'saldo', throw({'errore' -> 'fondi', 'mancano' -> quanto - conto:'saldo'}));
    conto:'saldo' += -quanto
);

try(preleva(conto, 100), 'mancano ' + _:'mancano');
```

Un tipo di eccezione che non esiste è un errore subito, con suggerimento:

```
UndefinedSymbolError
  Unknown exception type: nope_exception.
```

## `_trace`

Dentro un ramo di `catch`, oltre a `_` c'è `_trace`, una mappa con tre chiavi:

| Chiave | Cosa contiene |
| --- | --- |
| `stack` | una lista di `[modulo, funzione, riga, colonna]`, dal punto del lancio in giù |
| `locals` | le variabili locali nel punto in cui l'eccezione è partita |
| `token` | `[testo, riga, colonna]` del punto esatto |

Serve per il log di diagnostica, non per la logica:

```
try(lavoro(), 'exception', (
    io.append_text('data', 'errori.txt', 'fallito a ' + _trace:'token' + ': ' + _ + '\n');
    null
));
```

## Il watchdog

Un'app che cicla all'infinito dentro un evento non rallenta se stessa: **ferma il tick**, e con lui
il mondo. Per questo ogni esecuzione ha un budget:

```
ExecutionBudgetError[time]
  in torre.sorveglia  (src/torre.bs:12:5)

  Execution stopped after 2000 ms: the script is running too long and the server is waiting for
  it. If it really needs all that time, move it to a task() or raise 'timeout_ms' in the manifest.
```

| Quando | Tempo | Profondità di chiamata |
| --- | --- | --- |
| dentro un evento di gioco | 2 s | 256 |
| da `/blossom script run` o al caricamento | 15 s | 256 |
| dentro un `task()` | nessun limite | 256 |

Si cambiano nel manifesto, e allora valgono in tutti e tre i casi:

```json
"watchdog": { "timeout_ms": 5000, "max_depth": 512 }
```

`"watchdog": false` li toglie: legittimo per un'app di cui ti fidi, ma da quel momento un suo ciclo
infinito richiede il riavvio del server.

L'errore del watchdog **non è catturabile** con `try` — se lo fosse, un ciclo infinito dentro un
`try` continuerebbe a girare.

## Errori dentro un handler di evento

Un handler che esplode non porta giù né l'app né le altre app: l'errore viene stampato con il suo
stack e l'evento prosegue.

Stampato **dove**: nel log del server e in chat, agli operatori collegati. Un handler non ha nessuno
a cui rispondere — nessuno ha scritto un comando, è un pistone che si è mosso — e per molto tempo
l'unico posto in cui questi errori esistevano era la console, che chi gioca non guarda. Lo stesso
errore ripetuto compare in chat una volta ogni cinque secondi, con il conto di quelli soppressi nel
frattempo: una macchina a pistoni con un handler rotto sbaglia molte volte al secondo, e non è una
buona ragione per rendere la chat illeggibile. Nel log ci sono comunque tutti.

Vale allo stesso modo per le chiamate rimandate con `schedule` e per i callback delle schermate. Ma "prosegue" vuol dire che l'azione **non** viene annullata, perché il
valore di ritorno non c'è mai stato. Se un evento annullabile deve fallire in sicurezza, mettici tu
il `try` e decidi cosa restituire:

```
fn su_campana(pos, direzione, chi) -> (
    try(
        costruisci(pos),
        'exception', (
            motivo = _;         // '_' cambia significato dentro il for: salvalo prima
            for(entity.player('all'), text.print(_, 'non ci sono riuscito: ' + motivo))
        )
    );
    false           // il suono va avanti comunque
);
```

Quel `motivo = _` non è pedanteria: `_` è lo stesso nome che usano `for`, `map` e `filter` per
l'elemento corrente. Dentro un ciclo, il valore dell'eccezione non c'è più.

Oggi la risposta migliore è non usare `_` per niente e scrivere il `catch` come lambda, che il nome
ce l'ha già:

```
fn su_campana(pos, direzione, chi) -> (
    try(
        costruisci(pos),
        'exception', fn(motivo) -> for(entity.player('all'), text.print(_, 'non ci sono riuscito: ' + motivo))
    );
    false
);
```
