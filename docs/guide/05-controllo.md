# 5. Controllo di flusso

Non ci sono istruzioni: `if`, i cicli e le trasformazioni sono funzioni che restituiscono un valore.
Il che vuol dire che si compongono, e che una condizione si può mettere dove sta un valore.

## `if`

```
if(condizione, allora);
if(condizione, allora, altrimenti);
if(c1, a1, c2, a2, ..., altrimenti);        // catena: la prima vera vince
```

```
if(vita < 5, 'critico', vita < 15, 'ferito', 'sano');
if(false, 'a');             // null: senza ramo finale, se nessuna condizione è vera, è null
```

I rami non presi **non vengono valutati**: `if(x != 0, 100 / x, 0)` è sicuro.

## `match`

Quando le condizioni sono tutte confronti sullo **stesso** valore, `match` lo dice una volta sola:

```
match(danno,
    0            -> 'illeso',
    d: num       -> if(d > 20, 'grave', 'ferito'),
    else         -> 'ignoto');
```

Una forma a sinistra della freccia puo' essere:

| Forma | Combacia |
| --- | --- |
| un valore (`0`, `'ciao'`) | se il soggetto e' uguale |
| un nome (`d`) | sempre, e lega il soggetto a quel nome |
| un nome con tipo (`d: num`) | se il soggetto e' di quel tipo, e lo lega |
| `else` | il ramo finale |

Il soggetto si valuta **una volta sola**, e i corpi dei bracci non presi non si valutano affatto.
Senza nessun braccio che combacia il risultato e' `null`, come per un `if` senza ramo finale.

Non ci sono guardie (`d if d > 20 -> ...`): richiederebbero un `if` infisso, cioe' grammatica nuova,
e un `if` dentro il corpo del braccio dice la stessa cosa.

## Cicli

| | Firma | Restituisce |
| --- | --- | --- |
| `loop` | `loop(quante_volte, corpo)` | l'ultimo valore del corpo |
| `for` | `for(lista, corpo)` | **quante volte** il corpo è risultato vero |
| `while` | `while(condizione, tetto, corpo)` | l'ultimo valore del corpo |
| `c_for` | `c_for(init, condizione, passo, corpo)` | l'ultimo valore del corpo |

```
somma = 0;
loop(5, somma += _);                    // _ vale 0, 1, 2, 3, 4

for([1, 2, 3], somma += _);             // _ è l'elemento, _i il suo indice

quanti_alti = for(giocatori, entity.query(_, 'y') > 100);   // for conta i veri

i = 0;
while(i < 3, 1000, i += 1);             // 1000 è il tetto di iterazioni, non la condizione

c_for(i = 0, i < 10, i += 2, somma += i);
```

Il **tetto** di `while` non è un dettaglio decorativo: è il numero massimo di giri, e serve perché
un ciclo che gira dentro un evento tiene fermo il server. Raggiungerlo non è un errore, il ciclo
semplicemente finisce.

`break()` esce dal ciclo, `continue()` salta al giro dopo, e tutti e due accettano un valore che
diventa il valore del ciclo (o dell'iterazione):

```
loop(10, if(_ == 3, break(_)); 0);      // 3

somma = 0;
for([1, 2, 3], if(_ == 2, continue()); somma += _);      // salta il 2
```

`break` esce dal ciclo **più interno**. Non esiste un `break` etichettato: per uscire da due livelli,
usa una funzione e `return`.

`exit(valore)` è un'altra cosa: ferma tutta l'esecuzione in corso, non solo il ciclo.

## Trasformazioni

Sono in [`core.list`](../api/core.list.md) e prendono un'**espressione**, non una funzione: dentro,
`_` è l'elemento corrente, `_i` il suo indice e `_a` l'accumulatore di `reduce`.

```
import "core.list" as list;

list.map([1, 2, 3], _ * 2);                 // [2, 4, 6]
list.map([10, 20], _i);                     // [0, 1]
list.filter([1, 2, 3, 4], _ % 2 == 0);      // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);       // 10
list.first([1, 2, 3], _ > 1);               // 2, oppure null se nessuno
list.all([2, 4], _ % 2 == 0);               // true (su una lista vuota è true)
list.sort_key(['bbb', 'a'], list.length(_));// ordina secondo la chiave calcolata
```

`break()` dentro un `map` tronca il risultato: `list.map([1,2,3], if(_ == 2, break()); _)` dà `[1]`.

Le stesse trasformazioni accettano anche una **funzione** al posto dell'espressione, e allora i tre
nomi impliciti li sostituiscono i parametri:

```
list.map([1, 2, 3], fn(x) -> x * 2);                    // [2, 4, 6]
list.map(['a', 'b'], fn(x, i) -> i);                    // [0, 1]  - il secondo e' l'indice
list.reduce([1, 2, 3], fn(a, x) -> a + x, 0);           // 10      - in reduce il primo e' l'accumulatore
```

Quale delle due forme sia lo decide la grammatica, non il valore: si vede scritto, e non costa
niente a runtime. La forma corta con `_` resta la piu' veloce, perche' non crea nessun frame di
chiamata; la lambda in cambio da' un nome all'elemento, e un nome non puo' collidere con quello del
ciclo che sta fuori.

Una funzione gia' dichiarata si chiama direttamente:

```
fn punteggio(giocatore) -> ...;
list.map(entity.player('all'), fn(g) -> punteggio(g));
```

## Scegliere il ciclo giusto

- una lista da trasformare → `map`/`filter`/`reduce`: dicono cosa fai, e non tengono un contatore a
  mano
- un volume di blocchi → `scan.scan` o `scan.volume` di [`core.mc.scan`](../api/core.mc.scan.md),
  che sono tre cicli annidati scritti bene
- un lavoro lungo che non deve fermare il tick → un `task` ([capitolo 9](09-concorrenza.md))
- un contatore con un passo strano → `c_for`
- "finché non succede X", con un tetto → `while`
