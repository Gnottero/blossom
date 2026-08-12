# `core.mc.item`

Inventari, oggetti e ricette.

```
import "core.mc.item" as item;

item.inventory_size(player);                    // quante caselle
item.inventory_get(player, 0);                  // ['minecraft:stone', 64, {}]
item.inventory_set(player, 0, 1, 'diamond');
```

Un oggetto è una tripletta `[nome, quantità, nbt]`. Un inventario si indica con l'entità che lo porta
o con la posizione del blocco che lo contiene (baule, fornace, tramoggia...).

### `inventory_size(inventario)`
Quante caselle ha, `null` se lì non c'è nessun inventario.

### `inventory_get(inventario, casella?)`
Il contenuto di una casella, o l'intero inventario se la casella non viene data.

### `inventory_set(inventario, casella, quantità, oggetto?, nbt?)`
Scrive una casella. Quantità `0` la svuota.

### `inventory_find(inventario, oggetto, da_casella?)`
La prima casella che contiene quell'oggetto, `null` se non c'è.

### `inventory_remove(inventario, oggetto, quantità?)`
Toglie oggetti dall'inventario, ovunque siano.

### `inventory_has_items(inventario, oggetti...)`
Se l'inventario contiene tutto quello che serve.

### `drop_item(inventario, casella, quantità?)`
Butta per terra il contenuto di una casella.

### `item_list(tag?)` · `item_tags(oggetto, tag?)` · `item_category(oggetto)`
Gli oggetti registrati, i tag di un oggetto, e la sua categoria nel menu creativo.

### `stack_limit(oggetto)` · `crafting_remaining_item(oggetto)`
Quanto se ne impila, e cosa resta dopo averlo usato in una ricetta (il secchio vuoto, per dire).

### `item_display_name(oggetto)`
Il nome mostrato in gioco, tradotto.

### `recipe_data(oggetto, tipo?)`
Le ricette che producono quell'oggetto: ingredienti, risultato e tipo.

## Data component

Dalla 1.20.5 un oggetto non è più "id più un blocco di NBT libero": è un id più una mappa di
**componenti tipizzati**, ognuno con il proprio codec. Leggere quei dati passando dal NBT grezzo
funziona ancora per quello che nel NBT c'è, ma non dice quali componenti esistono, non distingue un
valore assente da uno uguale al default, e non valida niente quando si scrive.

```
import "core.mc.item" as item;

item.item_components(oggetto);                      // {'minecraft:max_stack_size' -> 64, ...}
item.item_component(oggetto, 'minecraft:damage');   // il valore, o null
item.set_item_component(oggetto, 'minecraft:custom_name', '{"text":"Spada"}');
item.set_item_component(oggetto, 'minecraft:damage', null);   // lo toglie
```

### `item_components(oggetto, default?)`
Tutti i componenti, come mappa da id a valore. Con `default` a vero legge quelli che l'**oggetto ha
di suo** invece di quelli che questa pila porta: è il confronto fra i due che dice se un valore è
stato messo apposta o se è quello che tutti gli oggetti di quel tipo hanno.

### `item_component(oggetto, nome, default?)`
Un componente solo, o `null` se quella pila non ce l'ha.

### `set_item_component(oggetto, nome, valore)`
L'oggetto con quel componente cambiato; con `valore` a `null` lo toglie. Restituisce una copia e non
tocca l'originale — un oggetto letto da un inventario è già una copia, e far finta che scriverci
dentro cambi il mondo sarebbe peggio che non poterlo fare.

Un valore che non ha senso per quel componente viene **rifiutato subito**, dal codec:

```
TypeMismatchError
  The given value is not valid for component 'minecraft:damage': Not a number
```

È il motivo per cui questi passano dai componenti tipizzati e non dal NBT: l'alternativa è un
oggetto rotto che si scopre molto dopo, quando qualcuno prova a usarlo.

### `component_list()`
Gli id di tutti i componenti che questo gioco conosce.
