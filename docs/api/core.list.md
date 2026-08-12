# `core.list`

Liste: costruzione, trasformazione, ordinamento e accesso.

```
import "core.list" as list;

list.map([1, 2, 3], _ * 2);              // [2, 4, 6]
list.filter([1, 2, 3, 4], _ % 2 == 0);   // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);    // 10
```

Le liste si scrivono `[1, 2, 3]` e si leggono con l'operatore `:` (`l:0`), che è sintassi e non
richiede import. Tutto il resto passa da questo modulo.

### Costruzione

`range(fine)`, `range(inizio, fine)`, `range(inizio, fine, passo)` — pigro: gli elementi vengono
prodotti mano a mano, quindi `range(1000000)` non alloca un milione di valori. Una lista pigra non
conosce la propria lunghezza: `length(range(5))` **non** risponde `5`; per averla, materializzala
prima con `slice(range(5), 0, 5)`.

### Trasformazione

`map(lista, espressione)`, `filter(lista, condizione)`, `reduce(lista, espressione, iniziale)`.
Dentro l'espressione `_` è l'elemento corrente, `_i` il suo indice e `_a` l'accumulatore di `reduce`.

`first(lista, condizione)` restituisce il primo elemento che soddisfa la condizione, `all(lista,
condizione)` dice se la soddisfano tutti.

### Ordinamento

`sort(lista)` ordina secondo l'ordine naturale dei valori; `sort_key(lista, chiave)` secondo il
risultato dell'espressione chiave.

### Accesso e modifica

`length(l)`, `get(l, indice)`, `slice(l, da, a?)`, `reverse(l)`.

`has(l, indice)` chiede se **quell'indice** esiste, non se la lista contiene un valore: per
l'appartenenza c'è `(l ~ valore) != null`, che restituisce l'indice o `null`.

`put(l, indice, valore)` **sostituisce**; `put(l, indice, valore, 'insert')` infila prima
dell'indice; `put(l, null, valore)` accoda; `put(l, null, altra_lista, 'extend')` accoda gli
elementi dell'altra lista invece della lista stessa. `delete(l, indice)` cancella — e a differenza
della lettura, che avvolge gli indici, cancellare un indice che non esiste è un
`IndexOutOfBoundsError`.

Quando il primo argomento è già un accesso (`put(l:0, ...)`), quello è **contenitore e indirizzo
insieme**: `put(l:0, 9, 'replace')` sostituisce l'elemento `0` di `l`, e per scrivere dentro una
lista annidata si scende di un livello, `put(l:0:1, 9)`.

`join(separatore, lista)` unisce gli elementi in una stringa.
