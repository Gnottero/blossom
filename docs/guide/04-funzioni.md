# 4. Funzioni

## Dichiarare

```
fn raddoppia(x) -> x * 2;

fn area(base, altezza) -> (
    metà = base * altezza / 2;
    metà
);
```

`fn` registra la funzione nella tabella dei simboli del modulo, **mentre il modulo si carica**: a
runtime non resta niente da cercare. È anche quello che permette agli errori di dire "in
`torre.costruisci`" invece di "in una funzione".

Senza `fn`, `nome(args) -> corpo` definisce lo stesso una funzione, ma come effetto collaterale di
un'espressione: usalo per una funzione usa-e-getta dentro un corpo, non per l'interfaccia di un
modulo.

`pub` davanti la rende visibile agli altri moduli:

```
pub fn distanza(a, b) -> ...;       // la vedono tutti
fn interna(x) -> ...;               // solo questo modulo
```

## Chi vede cosa

Dentro una funzione sono visibili: i suoi parametri, le variabili che assegna, e i **binding di
modulo** dichiarati con `let` o `let mut`.

```
let soglia = 10;            // immutabile
let mut contatore = 0;      // riassegnabile

pub fn conta() -> (
    contatore += 1;                     // ok: è 'let mut'
    if(contatore > soglia, 'troppi', contatore)
);
```

Una variabile assegnata al livello del modulo **senza** `let` non è un binding di modulo: resta nel
corpo, e le funzioni non la vedono.

```
contatore = 0;                          // no: è una variabile del corpo
fn bump() -> contatore += 1;            // scrive su un'altra cosa
bump();
contatore;                              // 0
```

Assegnare a un `let` immutabile è un errore, e lo dice:

```
TypeMismatchError
  'soglia' is an immutable binding: declare it 'let mut soglia' if it has to change.
```

Per leggere una variabile del frame **chiamante** c'è `outer`, che si dichiara nella firma:

```
base = 7;
fn leggi(outer(base)) -> base;
leggi();                        // 7
```

## Come arrivano gli argomenti

I valori scalari (numeri, stringhe, booleani) arrivano **per copia**; liste e mappe **per
riferimento**.

```
fn incrementa(v) -> (v += 1; null);
n = 1;
incrementa(n);
n;                              // 1  - invariato

fn accoda(l) -> list.put(l, null, 9);
lista = [1];
accoda(lista);
lista;                          // [1, 9]  - modificata davvero
```

Se una funzione non deve toccare la lista che riceve, copiala: `system.copy(l)`.

## Numero di argomenti

Chiamare con il numero sbagliato è un `ArityError`, sul confine della chiamata:

```
ArityError
  Function f(a, b) wants at least 2 arguments.
```

L'ultimo parametro può raccogliere il resto:

```
fn registra(livello, ...messaggi) -> [livello, messaggi];

registra('info', 'a', 'b');     // ['info', ['a', 'b']]
registra('info');               // ['info', []]
```

## Tipi sui parametri

Facoltativi, uno alla volta:

```
fn distanza(a: list, b: list) -> ...;
fn ripeti(quante: num, cosa) -> ...;        // 'cosa' resta libero
fn applica(f: func, x: any) -> call(f, x);  // 'any' documenta senza vincolare
```

I tipi sono `num`, `str`, `bool`, `list`, `map`, `func`, `null`, `any`, più quelli del gioco:
`entity`, `block`, `text`, `nbt`, `screen`. Si scrive `func` e non `fn` perché `fn` è già la parola
chiave della dichiarazione.

Un tipo scritto male è un errore **quando definisci** la funzione, non alla prima chiamata:

```
ParseError
  Unknown type 'nmu' in the signature: the types are num, str, bool, list, map, func, null, any...
  Did you mean: num?
```

Un argomento sbagliato è un errore **sul confine della funzione**, che è il punto: dice quale
parametro, cosa voleva e cosa è arrivato, invece di esplodere tre righe dentro il corpo su
un'operazione che non c'entra.

```
TypeMismatchError
  in distanza  (src/geo.bs:4:1)

  Parameter 'a' of distanza wants a list, but got a string (due).
```

Il controllo costa un confronto di stringa per parametro annotato: mettere i tipi sull'interfaccia
pubblica di un modulo è quasi sempre un buon affare, metterli su una funzione chiamata in un ciclo
stretto no.

## Le funzioni sono valori

```
fn triplica(x) -> x * 3;

fn applica_due_volte(f, x) -> call(f, call(f, x));

applica_due_volte(triplica, 2);         // 18   - per valore
applica_due_volte('triplica', 2);       // 18   - o per nome
```

`call(f, ...)` invoca una funzione che arriva come valore o come nome. Una funzione passata a un
altro modulo continua a risolversi correttamente: si porta dietro il modulo in cui è stata definita,
quindi le sue chiamate interne vedono sempre i simboli giusti, non quelli di chi la invoca.

È anche il motivo per cui gli handler di evento si registrano **passando la funzione**, non il suo
nome in una stringa:

```
event.on('player_connects', benvenuto);
```

## Ricorsione e uscita anticipata

```
fn fib(n) -> if(n < 2, n, fib(n - 1) + fib(n - 2));
fib(10);                        // 55
```

Il valore di una funzione è l'ultima espressione del corpo. `return(v)` esce prima:

```
fn classifica(x) -> (
    if(x < 0, return('negativo'));
    if(x == 0, return('zero'));
    'positivo'
);
```

La profondità di ricorsione ha un tetto (256 di default, vedi [capitolo 9](09-concorrenza.md)):
oltrepassarlo è un `ExecutionBudgetError`, non uno stack overflow della JVM che porta giù il server.
