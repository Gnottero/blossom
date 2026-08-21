# 3. Operatori

## La tabella delle precedenze

Dall'alto (lega di più) verso il basso:

| Livello | Operatori | |
| --- | --- | --- |
| accesso | `:` `~` | `l:0`, `'testo' ~ 'regex'` |
| unari | `-` `!` `...` | `-x`, `!vero`, `...lista` |
| potenza | `^` | associativo a destra: `2^2^3` è `2^8` |
| moltiplicazione | `*` `/` `%` | |
| addizione | `+` `-` | |
| confronto | `<` `<=` `>` `>=` | |
| uguaglianza | `==` `!=` | |
| congiunzione | `&&` | valutazione pigra |
| disgiunzione | `\|\|` | valutazione pigra |
| tubo | `\|>` | `lista \|> list.map(f)` |
| assegnamento | `=` `+=` `<>` | |
| definizione | `->` | |
| dichiarazione | `fn` `let` `let mut` `pub` | prendono tutta la definizione che segue |
| sequenza | `;` | |

L'unario lega **più** della potenza: `-2 ^ 2` fa `4`, non `-4`.

## Aritmetica

```
1 + 2;          3 - 1;          2 * 3;          7 / 2;          7 % 3;          2 ^ 10;
```

`+` fa cose diverse a seconda degli operandi, e sono tutte prevedibili:

```
'n=' + 3;                   // 'n=3'      concatena, convertendo
[1, 2] + [10, 20];          // [11, 22]   somma elemento per elemento
[1, 2] + 1;                 // [2, 3]     somma lo scalare a ognuno
[1, 2] + [1, 2, 3];         // TypeMismatchError: liste di lunghezza diversa
'ab' * 3;                   // 'ababab'
```

Sommare due liste **non** le concatena. Per quello c'è `list.put(l, null, altra, 'extend')`.

## Confronto e uguaglianza

`<` `<=` `>` `>=` ordinano numeri e stringhe (maiuscole prima delle minuscole: `['b','A','a']`
ordinata dà `[A, a, b]`). `==` e `!=` confrontano il contenuto, in profondità su liste e mappe.

Non incatenare i confronti: `1 < 2 < 3` si valuta da sinistra come `(1 < 2) < 3`, cioè `true < 3`, e
il risultato non vuol dire quello che sembra. Scrivi `1 < 2 && 2 < 3`.

## Logica

`&&` e `||` sono pigri e **restituiscono un valore**, non un booleano:

```
0 && 5;             // 0    - si è fermato al primo falso
3 && 5;             // 5    - l'ultimo valutato
0 || 5;             // 5    - il primo vero
false && (1/0);     // false, e la divisione per zero non viene mai eseguita
```

Da cui l'idioma per un valore di riserva:

```
nome = configurazione:'nome' || 'senza nome';
```

`!` nega secondo le regole di verità del [capitolo 2](02-valori.md): `!0`, `!''`, `!null` e `![]`
sono tutti `true`.

## Accesso: `:`

```
l:0;                    // elemento di lista
m:'chiave';             // valore di mappa
m:'a':'b';              // in catena
l:0 = 9;                // anche in scrittura
m:'nuova' = 1;          // crea la chiave
```

`:` è sintassi, non una funzione: non richiede nessun import. Dentro una firma di funzione lo stesso
carattere significa un'altra cosa — il tipo di un parametro (`fn f(a: num)`), vedi
[capitolo 4](04-funzioni.md).

Per un indice negativo servono le parentesi: `l:(-1)`, perché `l:-1` non si parsa. Oppure
`list.get(l, -1)`.

## Stringhe interpolate: `"..."`

Un apice singolo fa una stringa letterale, e non succede niente al suo interno. Un **doppio apice**
inserisce il valore di quello che sta dentro `${...}`:

```
nome = 'ada';
punti = 12;

"ciao ${nome}, hai ${punti} punti";        // 'ciao ada, hai 12 punti'
"il doppio e' ${punti * 2}";               // 'il doppio e' 24'
'ciao ${nome}';                           // 'ciao ${nome}' - l'apice singolo non interpola
```

Dentro le graffe ci sta un'espressione qualunque, chiamate comprese. Lo spezzettamento avviene
**mentre si legge il codice**: a runtime non c'e' nessuna stringa da riesaminare, solo una
concatenazione gia' scritta, e una stringa senza segnaposti resta una costante.

## Ricerca: `~`

Un operatore, tre significati, secondo il tipo di sinistra:

```
'hello world' ~ 'o w';              // 'o w'    - regex: la porzione trovata, null se non c'è
'v1.2' ~ '([0-9])\.([0-9])';        // ['1', '2'] - con gruppi: la lista dei gruppi
['a', 'b', 'c'] ~ 'c';              // 2        - l'indice, null se non c'è
{'a' -> 1} ~ 'a';                   // 'a'      - la chiave se c'è, null se no
```

Su una lista restituisce **l'indice**, che per il primo elemento è `0`: in una condizione, `0` è
falso. Se cerchi "c'è dentro?", scrivi `(l ~ x) != null`. Non `list.has(l, x)`: su una lista, `has`
chiede se esiste quell'**indice** (su una mappa invece chiede della chiave, come ci si aspetta).

## Tubo: `|>`

`a |> f(b)` vuol dire `f(a, b)`: il valore a sinistra diventa il **primo argomento** della chiamata a
destra. Serve a leggere una catena di trasformazioni nell'ordine in cui succedono, invece che dal di
dentro verso il di fuori.

```
import "core.list" as list;

[1, 2, 3, 4]
    |> list.filter(fn(x) -> x % 2 == 0)
    |> list.map(fn(x) -> x * 2);            // [4, 8]
```

La riscrittura avviene **mentre si legge il codice**: il tubo sparisce e resta la chiamata con un
argomento in piu' davanti. A runtime non costa niente, e incatenare tre trasformazioni costa quanto
scriverle annidate.

Lega piu' dell'assegnamento e meno di `||`, quindi `x = a |> f()` mette in `x` il risultato, e
`(a || b) |> f()` e' quello che sembra. Se a destra c'e' un valore invece di una chiamata
(`x |> f`, con `f` legata a una funzione) viene chiamato lo stesso.

## Assegnamento

```
a = 1;              // assegna, e restituisce il valore assegnato
a += 2;             // somma
l += 3;             // su una lista: accoda
s += 'coda';        // su una stringa: concatena
a <> b;             // scambia due variabili
```

`<>` scambia **variabili**, non posizioni dentro un contenitore: `l:0 <> l:1` è un errore.

Un assegnamento restituisce il valore, quindi `a = (b = 3)` mette `3` in tutte e due — comodo di
rado, ma non è un errore.

## Sequenza: `;`

`;` separa espressioni e vale l'**ultima**:

```
(1; 2; 3);          // 3
```

Un `;` finale è ammesso. Il corpo di una funzione è tipicamente una sequenza fra parentesi:

```
fn area(w, h) -> (
    base = w * h;
    base / 2
);
```

Il risultato è l'ultima riga: niente `return` obbligatorio (ma `return(v)` esiste, per uscire prima).

## Spacchettare: `...`

Come argomento di chiamata, srotola una lista:

```
f(a, b) -> a + b;
call('f', ...[1, 2]);       // 3
[0, ...[1, 2]];             // [0, 1, 2]
```

Come ultimo parametro di una firma, raccoglie il resto (vedi [capitolo 4](04-funzioni.md)):

```
fn quanti(primo, ...resto) -> [primo, resto];
quanti(1, 2, 3);            // [1, [2, 3]]
quanti(1);                  // [1, []]
```

## Definizione: `->`

`->` ha due significati, che il contesto separa senza ambiguità:

```
fn somma(a, b) -> a + b;        // definizione di funzione
m = {'a' -> 1, 'b' -> 2};       // coppia chiave/valore, dentro un letterale mappa
```
