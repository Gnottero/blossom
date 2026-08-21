# 10. Prestazioni

Il vincolo vero non è "quanto è veloce il linguaggio", è **quanto dura un tick**: 50 ms, e dentro ci
sta anche tutto il resto del gioco. Uno script che ne prende 5 è già un quinto del budget del server.

## Gli ordini di grandezza

Misure su AMD Ryzen 5 5600H, OpenJDK 25 (i numeri completi e il metodo sono in
[`BENCHMARK.md`](../../../BENCHMARK.md)):

| Operazione | Costo |
| --- | --- |
| un'iterazione di loop aritmetico | ~55 ns |
| una chiamata a funzione utente (1 argomento) | ~178 ns (circa 3 iterazioni) |
| la stessa chiamata con 4 argomenti | ~332 ns |
| una lettura indicizzata da lista | ~80 ns |
| costruire una mappa da 2 chiavi | ~234 ns |

Una chiamata costa ancora **per argomento**: circa 65 ns ciascuno oltre il primo. Se una funzione
chiamata in un ciclo stretto prende sei parametri, la lista dei parametri è metà del suo costo.

Tradotto: in un tick da 50 ms ci stanno **circa 900.000 operazioni aritmetiche**. Il linguaggio non è
il collo di bottiglia di quasi nessuno script. Quello che costa davvero è un altro elenco:

| Cosa | Perché costa |
| --- | --- |
| leggere o scrivere un blocco | passa dal mondo, e il mondo è una struttura vera |
| `entity_area` / `entity_selector` su raggi grandi | scorre entità |
| uno `scan` su un volume grosso | è il prodotto delle tre dimensioni: raggio 32 sono 274.625 blocchi |
| un `task_dock` per ogni singola lettura | ogni sosta fa la coda con il tick |
| un handler su `tick` che fa tutto ogni tick | venti volte al secondo, per sempre |

Un ordine di grandezza di differenza fra il costo del linguaggio e il costo del mondo vuol dire una
cosa sola: **ottimizza gli accessi al mondo, non l'aritmetica**.

## Misurare invece di indovinare

Per confrontare due implementazioni, `system.measure(espressione)` la esegue ripetutamente per
un intervallo fisso e dice quante volte ci è riuscita: numero più alto, implementazione più veloce.

Per la domanda vera — *chi si mangia il tick* — c'è `/blossom profile`:

| Comando | Cosa misura |
| --- | --- |
| `/blossom profile run <codice>` | un'espressione al volo |
| `/blossom profile in <app> <codice>` | del codice dentro un'app caricata |
| `/blossom profile start <app>` | accende la misura sull'app: da lì conta anche i suoi handler |
| `/blossom profile stop <app>` | chiude e stampa il rapporto |

Il rapporto ha due tabelle: i moduli ordinati per tempo proprio, e le quindici funzioni più costose
con **tempo proprio e tempo totale**. La coppia serve: il totale è il tempo passato dentro la
funzione, comprese quelle che chiama; il proprio è quello che resta togliendole. Una funzione con
totale enorme e proprio minuscolo non è lenta — è solo in cima allo stack, e ottimizzarla non cambia
niente.

Su un'app a eventi la coppia da usare è `start`/`stop`: `run` misura solo quello che scrivi tu sulla
riga di comando, mentre il problema di solito è un handler che scatta cento volte al secondo.

## Le sistemazioni che pagano

**Restringi il volume prima di percorrerlo.** Un raggio dimezzato è un ottavo del lavoro.

```
// no: 274.625 blocchi
scan.scan(centro, [32, 32, 32], if(block.block(_) == 'diamond_ore', trovati += 1));

// sì: cerca dove ha senso cercare
scan.volume([x-16, 0, z-16], [x+16, 16, z+16], ...);
```

**Leggi una volta, decidi dopo.** Ogni `block.block(p)` è un accesso al mondo: se ti serve lo stesso
punto tre volte, mettilo in una variabile.

**Filtra prima di chiamare.** `entity.on_load(['zombie'], f)` filtra per tipo dentro il
mod; l'evento `entity_loads` con un `if` dentro l'handler ti fa pagare una chiamata per ogni entità
che compare nel mondo.

**Diradare invece di ridurre.** Un controllo che deve essere reattivo, non istantaneo, non ha bisogno
di ogni tick:

```
let mut t = 0;
fn su_tick() -> (t += 1; if(t % 20 == 0, controlla()));
```

**Un `task_dock` per blocco di lavoro**, non uno per lettura ([capitolo 9](09-concorrenza.md)).

**Le liste pigre restano pigre.** `list.range(1000000)` non alloca un milione di valori; ci passi
sopra con `map`/`filter` e non paghi la memoria. Materializzarla con `list.slice(...)` la paga tutta.

**I tipi sui parametri costano un confronto per parametro annotato.** Sull'interfaccia pubblica di un
modulo sono un affare; su una funzione chiamata in un ciclo stretto, no.

## Cosa fa già l'interprete

Prima di essere valutato, l'albero passa da un passo di compilazione: costanti piegate, operatori
associativi appiattiti, sottoalberi puri risolti una volta sola. Vale circa il **32%** su un loop
aritmetico. Un effetto collaterale visibile: `json.decode('{')` con una costante può fallire
già in fase di parsing invece che dentro il tuo `try`.

Gli import sono risolti **una volta al caricamento**: `math.sqrt` non cerca niente per nome a
runtime, e un modulo in più non rallenta le chiamate.

Anche i nomi delle variabili si decidono al parsing: il nodo compilato sa già se sta leggendo una
locale, una globale, un simbolo importato o una implicita (`_`, `_i`, `_a`, che sono campi del
contesto e non voci di una mappa). A runtime non resta nessuna domanda sul nome.

Il frame di una chiamata **non è una mappa a hash**: fino a sedici variabili locali sono due array
paralleli con scansione lineare, e una funzione senza parametri non alloca niente. Un valore non
viene avvolto in nessun oggetto pigro per essere passato o restituito, e gli argomenti non vengono
copiati entrando nel frame. Le due cose insieme valgono **-24%** e **-12%** sulle chiamate a
funzione, e portano il costo per parametro da ~110 a ~65 ns.

Il watchdog costa circa **5 ns per iterazione** e **19 ns per chiamata**. Spegnerlo per andare più
veloce non è un buon affare: si spegne quando dà fastidio, non per guadagnare quei nanosecondi.
