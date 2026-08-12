# `core.mc.file`

I dati dell'app su disco.

```
import "core.mc.file" as file;

file.write_file('punteggi', 'json', {'ada' -> 12});
file.read_file('punteggi', 'json');
```

Ogni app scrive sotto `<mondo>/blossom/<app>.data/`: due app non si vedono i dati per sbaglio. Un
file marcato come condiviso finisce invece in `<mondo>/blossom/shared/`, ed è il modo previsto per
passarsi dati fra app.

Queste funzioni hanno bisogno di un'app caricata: da `/blossom script run` non funzionano, e lo
dicono.

### `read_file(nome, tipo)`
Legge un file. I tipi sono `'text'` (lista di righe), `'json'`, `'nbt'`, `'raw'`, `'shared_text'`,
`'shared_json'`, `'shared_nbt'`, `'shared_raw'`.

### `write_file(nome, tipo, dati)`
Scrive, o accoda se il tipo è testuale.

### `delete_file(nome, tipo)` · `list_files(cartella, tipo)`
Cancella un file, ed elenca quelli di una cartella.

### `load_app_data()` · `load_app_data(nome)` · `store_app_data(dati)` · `store_app_data(dati, nome)`
Lo stato dell'app in NBT, salvato e riletto senza pensare ai percorsi. Senza nome è il file di stato
predefinito dell'app.

### `create_datapack(nome, contenuto)`
Crea un datapack dal contenuto dato (una mappa di percorsi → file) e lo attiva.
