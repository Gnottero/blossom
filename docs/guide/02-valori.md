# 2. Valori

## I tipi

Sette, e `system.type(v)` te li dice per nome:

| Tipo | Come si scrive | Note |
| --- | --- | --- |
| `number` | `42`, `-3`, `0.5`, `1e3` | interi e decimali sono lo stesso tipo |
| `string` | `'ciao'` | apici singoli, sempre |
| `bool` | `true`, `false` | |
| `list` | `[1, 2, 3]` | |
| `map` | `{'a' -> 1}` | |
| `null` | `null` | il valore "non c'è" |
| `function` | `fn f() -> 1;` | una funzione è un valore |

Il layer di gioco ne aggiunge altri, che si maneggiano allo stesso modo: `entity`, `block`, `text`,
`nbt`, `screen`.

```
import "core.system" as system;

system.type([1, 2]);        // 'list'
system.type(null);          // 'null'
system.type(true);          // 'bool'
```

## Numeri

Un numero solo, che internamente è un `long` finché può e un `double` quando serve. Le conseguenze
utili da sapere:

```
7 / 2;              // 3.5   - la divisione non tronca
6 / 3;              // 2     - un risultato intero si stampa intero
-7 % 3;             // 2     - il modulo segue il segno del divisore
2 ^ 10;             // 1024
```

`^` passa comunque dai decimali, quindi su interi enormi perde le ultime cifre:

```
2 ^ 62;             // 4611686018430000000, non 4611686018427387904
```

Se ti servono interi esatti a 64 bit, moltiplica invece di elevare.

Per arrotondare, radici, trigonometria e rumore procedurale c'è
[`core.math`](../api/core.math.md). `math.number('42')` converte una stringa in numero e fallisce
con un `TypeMismatchError` se non lo è — non restituisce `null` in silenzio.

## Stringhe

Apici singoli. Le virgolette doppie **non** sono un letterale di stringa: compaiono solo nella riga
`import "core.list" as list;`, che è preambolo e non espressione.

```
'a\'b';             // apice interno
'riga\nriga';       // a capo
'a\\b';             // backslash
'ab' * 3;           // 'ababab'
'n=' + 3;           // 'n=3'   - la concatenazione converte
```

Una stringa **non si indicizza**: `'abc':0` è `null`, non `'a'`. Per lavorare sui caratteri passa da
[`core.string`](../api/core.string.md) (`split`, `replace`, `slice` di `core.list` sulla lista di
caratteri).

`string.str(formato, ...)` costruisce testo con `%s`, `%d`, `%.2f`:

```
import "core.string" as string;

string.str('%s ha %d mele', 'ada', 3);      // 'ada ha 3 mele'
```

## Liste

```
import "core.list" as list;

l = [10, 20, 30];
l:0;                        // 10
l:4;                        // 20 - gli indici avvolgono, 4 % 3 = 1
list.get(l, -1);            // 30 - gli indici negativi contano dal fondo
l:(-1);                     // 30 - le parentesi servono: 'l:-1' non si parsa
list.size(l);             // 3
```

Due proprietà da tenere a mente:

**Gli indici avvolgono.** Leggere fuori dalla lista non è un errore, è un giro. Cancellare invece
sì: `list.remove(l, 7)` su una lista di tre elementi è un `IndexOutOfBoundsError`.

**Una lista è un riferimento.** Assegnarla non la copia:

```
a = [1, 2];
b = a;
list.put(b, 0, 9);
a;                          // [9, 2]  - è la stessa lista
```

Per una copia indipendente c'è `system.copy(a)`.

### Scrivere in una lista

`put` sostituisce di default, e i modificatori dicono il resto:

```
l = [1, 2, 3];
list.put(l, 0, 9);              // [9, 2, 3]      sostituisce
list.put(l, 1, 5, 'insert');    // [9, 5, 2, 3]   infila prima dell'indice
list.put(l, null, 4);           // [..., 4]       accoda
list.put(l, null, [7, 8], 'extend');   // accoda gli elementi, non la lista
```

Attenzione a `put` su una lista annidata: il primo argomento è **contenitore e indirizzo insieme**,
quindi `l:0` significa "l'elemento 0 di `l`", non "la lista dentro":

```
l = [[1, 2]];
list.put(l:0, 9, 'replace');    // [9]        ha sostituito l'elemento 0 di l
list.put(l:0:1, 9);             // [[1, 9]]   questo scrive dentro
```

### Liste pigre

`list.range(...)` non alloca niente: produce gli elementi man mano, quindi `range(1000000)` costa
quanto scriverlo.

```
list.join(',', list.range(0, 10, 3));       // '0,3,6,9'
list.map(list.range(3), _ * _);             // [0, 1, 4]
```

Una lista pigra non conosce la propria lunghezza: **`list.size(list.range(5))` non risponde `5`**
— per averla, materializzala prima (`list.slice(list.range(5), 0, 5)`) o conta a mano. È un
inciampo noto, non un comportamento voluto.

## Mappe

```
import "core.map" as map;

m = {'a' -> 1, 'b' -> 2};
m:'a';                  // 1
m:'zz';                 // null - una chiave che non c'è vale null
map.keys(m);            // ['a', 'b']
map.pairs(m);           // [['a', 1], ['b', 2]]
m:'c' = 3;              // scrivere con l'accesso, come le liste
```

Le chiavi possono essere di qualunque tipo, non solo stringhe: `{1 -> 'a'}` funziona. Le mappe
annidate si leggono in catena: `m:'a':'b'`.

## Verità

Nel posto della condizione, questi valori sono falsi:

| Falso | Vero |
| --- | --- |
| `false`, `null`, `0` | qualunque altro numero |
| `''` (stringa vuota) | qualunque altra stringa, **anche `'0'`** |
| `[]`, `{}` | qualunque lista o mappa non vuota |

```
if('0', 'vero', 'falso');       // 'vero'  - è una stringa non vuota
if([], 'vero', 'falso');        // 'falso'
```

## Uguaglianza

`==` confronta il contenuto, e su liste e mappe è profondo:

```
[1, 2] == [1, 2];               // true
{'a' -> 1} == {'a' -> 1};       // true
```

Fra numero e stringa il confronto è **permissivo**: `'1' == 1` è `true`, e così `true == 1`. Se ti
serve distinguerli, confronta anche i tipi:

```
import "core.system" as system;

fn identici(a, b) -> system.type(a) == system.type(b) && a == b;
```

## null

`null` arriva da: una chiave che non c'è, una variabile mai assegnata, una funzione che non
restituisce niente. In aritmetica vale zero (`null + 1` fa `1`), in una condizione è falso.

Una variabile mai assegnata vale `null` senza protestare; un **simbolo di modulo** che non esiste è
invece un `UndefinedSymbolError` a caricamento. La differenza è voluta: il primo caso è una svista
locale che si vede subito, il secondo è un nome sbagliato che va fermato prima di girare.
