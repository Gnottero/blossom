# `core.map`

Mappe: chiavi, valori e coppie.

```
import "core.map" as map;

m = {'a' -> 1, 'b' -> 2};
map.keys(m);            // ['a', 'b']
map.has(m, 'a');        // true
```

Le mappe si scrivono `{chiave -> valore, ...}` e si leggono con `:` (`m:'a'`), che è sintassi.

### Lettura

`keys(m)`, `values(m)`, `pairs(m)` — quest'ultima restituisce una lista di coppie `[chiave, valore]`.
`get(m, chiave)`, `has(m, chiave)`, `length(m)`.

### Modifica

`put(m, chiave, valore)`, `delete(m, chiave)`.

Le operazioni singole su una mappa condivisa fra più task sono atomiche; una *sequenza* di operazioni
non lo è — per quella serve `synchronize` di [`core.task`](core.task.md).
