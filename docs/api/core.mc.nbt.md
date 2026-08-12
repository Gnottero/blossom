# `core.mc.nbt`

I dati grezzi del gioco.

```
import "core.mc.nbt" as nbt;

tag = nbt.nbt('{Fire: 20, Motion: [0.0d, 0.5d, 0.0d]}');
tag:'Fire';                          // 20
nbt.encode_nbt([1, 2, 3]);           // da valore Blossom a NBT
```

Un valore NBT si indicizza come una mappa o una lista, e si può modificare in posto: è il modo di
toccare quello che l'API curata non espone ancora.

### `nbt(valore)`
Costruisce un valore NBT da una stringa in formato comando, da una mappa o da una lista.

### `parse_nbt(nbt)`
Il contrario: da NBT a valori Blossom normali (mappe, liste, numeri, stringhe).

### `encode_nbt(valore, forza?)`
Da valori Blossom a NBT. Con `forza` vero, quello che non ha una forma NBT diventa la sua stringa
invece di far fallire la conversione.

### `escape_nbt(stringa)`
Mette una stringa nella forma con cui il gioco la scriverebbe dentro un tag.

### `tag_matches(tag, predicato, rispetta_liste?)`
Se un tag soddisfa un predicato parziale, con la stessa semantica dei comandi vanilla.

### `nbt_storage()` · `nbt_storage(chiave)` · `nbt_storage(chiave, valore)`
Lo storage NBT del mondo, quello che i comandi chiamano `data storage`: è il modo di scambiare dati
con le funzioni e i datapack.
