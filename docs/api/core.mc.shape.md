# `core.mc.shape`

Far vedere quello che uno script sta calcolando.

```
import "core.mc.shape" as shape;

shape.draw_shape('box', 100, {'from' -> [0, 64, 0], 'to' -> [8, 70, 8], 'color' -> 'red'});
shape.draw_shape('sphere', 100, {'center' -> [0, 66, 0], 'radius' -> 4, 'color' -> 'cyan'});
shape.draw_shape('label', 100, {'pos' -> [0, 70, 0], 'text' -> 'qui'});
```

## Il patto con il client

Il mod è **server-side**: si installa sul server e basta, e nessuna funzione smette di funzionare per
chi gioca vanilla. Le forme però sono l'unica cosa che un server non può disegnare da solo — non
esiste nessun pacchetto vanilla che dica "traccia una linea qui". Quindi:

| Chi guarda | Cosa vede |
| --- | --- |
| ha Blossom anche sul client | la forma vera, disegnata con le linee |
| gioca vanilla | la stessa geometria approssimata a **particelle** |

Chi scrive lo script non deve sapere chi ha cosa: `draw_shape` decide da sola, giocatore per
giocatore, e restituisce quanti l'hanno ricevuta come forma vera. Se ti serve saperlo prima c'è
`has_client(giocatore)`, in [`core.mc.entity`](core.mc.entity.md) — è una domanda su un giocatore,
non sul disegno.

Il client si riconosce da solo: appena entri, il server manda un saluto; un client vanilla lo scarta
in silenzio, uno Blossom risponde. Niente da configurare.

**I limiti delle particelle**, dichiarati: il colore diventa la particella che gli somiglia di più
(le particelle vanilla non si colorano a piacere), le curve diventano poligoni, e un'etichetta non si
può disegnare affatto — chi non ha il client riceve il testo in chat una volta sola. Un blocco o un
item diventano una particella che ne mostra la texture nel punto giusto.

### `draw_shape(tipo, durata, attributi)`

`durata` è in tick. `attributi` è una mappa; quelli comuni a tutte le forme:

| Attributo | Cosa fa | Default |
| --- | --- | --- |
| `player` | chi la vede: un giocatore o una lista | tutti quelli nella dimensione |
| `color` | un nome o un numero `0xRRGGBBAA`; alfa zero vuol dire "niente reticolo" | bianco |
| `fill` | il colore del riempimento; alfa zero vuol dire "non riempire", alfa `ff` copre quello che c'è dietro | trasparente |
| `line` | lo spessore della linea | `2.0` |
| `follow` | un'entità (o il suo id): le coordinate diventano relative a lei | nessuna |
| `snap` | quali assi seguono l'entità, con `d` per arrotondare al blocco | `'xyz'` |
| `debug` | la forma si vede solo con le hitbox di debug accese | `false` |

I nomi di colore sono `white`, `black`, `red`, `green`, `blue`, `yellow`, `cyan`, `magenta`,
`orange`, `grey`. Non sono solo una comodità: un colore scritto a mano in esadecimale è la cosa che
si sbaglia più spesso, e quasi sempre finisce con il canale alfa a zero — cioè la forma non si vede,
e sembra che la funzione non abbia funzionato.

`snap` prende una combinazione degli assi: `'xz'` segue l'entità sul piano ma lascia la quota
scritta nello script, `'dxdydz'` la segue arrotondando al blocco. Vale solo con `follow`.

E la geometria, per tipo:

| Tipo | Attributi |
| --- | --- |
| `line` | `from`, `to` |
| `box` | `from`, `to` |
| `sphere` | `center`, `radius`, `level` |
| `cylinder` | `center`, `radius`, `height`, `axis` (`'x'`/`'y'`/`'z'`, default `'y'`), `level` |
| `label` | `pos`, `text` (o `value`), `size`, `align`, `indent`, `height`, `raise`, `tilt`, `lean`, `turn`, `facing`, `doublesided` |
| `block` | `pos`, `block`, `facing`, `tilt`, `lean`, `turn`, `scale`, `blocklight`, `skylight` |
| `item` | `pos`, `item`, `variant`, `facing`, `tilt`, `lean`, `turn`, `scale`, `blocklight`, `skylight` |

`level` è quanti lati hanno i cerchi: `0` (il default) li calcola dal raggio. Vale insieme per il
reticolo e per il riempimento — una sfera con `level` alto ha più meridiani e paralleli, non solo una
buccia più liscia. Un cilindro di altezza `0` è un disco — il modo di disegnare un raggio a terra
senza inventare una sesta forma.

Reticolo e riempimento si accendono uno indipendentemente dall'altro, con l'alfa dei rispettivi
colori. `{'color' -> 0x00000000, 'fill' -> 0x2255ffff}` è una forma piena e liscia, senza spigoli;
`{'fill' -> 0x2255ff80}` è la stessa cosa in trasparenza, con il reticolo bianco di default sopra.
Un riempimento con alfa `ff` è opaco davvero: nasconde il terreno, il cielo e le nuvole che stanno
dietro, e anche l'altra metà della forma stessa. Gli alfa intermedi li nascondono nella loro
proporzione: dietro un riempimento a metà, una nuvola si vede a metà. Chi non ha il client vede
comunque la sagoma a particelle, che prende il colore del riempimento quando il reticolo è spento.

Le attribute dell'etichetta: `size` è la dimensione del carattere (default `10`), `align` vale
`'center'`, `'left'` o `'right'`, `indent`/`height`/`raise` la spostano nelle tre direzioni in unità
di font, `tilt`/`lean`/`turn` la ruotano in gradi, `facing` è `'player'` (default), `'camera'` o una
direzione fissa (`'north'`, `'south'`, `'east'`, `'west'`, `'up'`, `'down'`), `doublesided` la rende
leggibile anche da dietro. Il `fill` di un'etichetta è lo sfondo del testo.

`text` accetta anche un testo formattato di [`core.mc.text`](core.mc.text.md), e in quel caso i suoi
colori e la sua formattazione si vedono nel mondo. Chi non ha il client riceve comunque il testo in
chat, con lo stesso stile.

### Blocchi e item a mezz'aria

`block` e `item` disegnano il modello vero del gioco dove nel mondo non c'è niente: un blocco
fantasma per far vedere dove andrà un pezzo di costruzione, un item che segna un punto.

```
shape.draw_shape('block', 100, {'pos' -> [0, 65, 0], 'block' -> 'chest[facing=north]'});
shape.draw_shape('item', 100, {'pos' -> [0, 67, 0], 'item' -> 'diamond_sword', 'variant' -> 'ground'});
```

`block` prende il blocco come lo si scrive (`'chest[facing=north]'`) o un valore blocco preso dal
mondo — nel secondo caso si porta dietro i suoi dati, quindi un baule disegnato così ha il coperchio
e un cartello il suo testo. `item` prende il nome dell'item o un item con il suo NBT, e i suoi data
component si vedono: un'arma incantata luccica.

Le attribute della posa: `facing` è come per l'etichetta ma il default è `'north'`,
`tilt`/`lean`/`turn` ruotano in gradi, `scale` è un numero solo o uno per asse (`[2, 1, 2]`),
`blocklight` e `skylight` vanno da `0` a `15` e impongono l'illuminazione invece di prendere quella
del posto. `variant` vale solo per gli item ed è la posa in cui il gioco li mostra: `'none'` (il
default), `'gui'`, `'ground'`, `'fixed'`, `'head'`, e le quattro varianti in mano.

Un blocco gira attorno al proprio centro, un item attorno al punto in cui sta. Chi non ha il client
vede una particella `block_marker` (o `item`, per gli item) nel punto: non è il modello, ma dice
almeno quale blocco è e dove sta.

I nomi e i significati sono quelli di Scarpet — chi arriva da lì non deve reimparare niente — ma
stanno in una mappa invece che sparsi fra gli argomenti, e **un nome che quella forma non ha è un
errore**, con il nome giusto suggerito, invece di un default silenzioso.

Sono sette tipi perché sono le cose che servono a far vedere qualcosa che uno script sta calcolando:
un percorso, un volume, un raggio, una colonna, un numero, un blocco, un item. Di Scarpet manca il
`polygon` — facce poligonali arbitrarie - che serve a fare grafica, non a capire cosa sta facendo il
proprio codice.

### `clear_shapes()`
Dimentica le forme ancora in attesa di essere ridisegnate a particelle. Non cancella quelle già
mandate a un client, che scadono da sole.

### `shape_types()`
I tipi che `draw_shape` accetta.

## Installare il client

Lo stesso jar. Messo in `mods/` di un client, aggiunge il disegno delle forme e nient'altro: nessuna
logica di gioco sta da quella parte, e non ci deve stare — un client che decidesse qualcosa sarebbe
un client di cui il server deve fidarsi.
