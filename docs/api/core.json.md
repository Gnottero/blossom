# `core.json`

Conversione fra valori Blossom e json.

```
import "core.json" as json;

json.encode_json({'nome' -> 'blossom', 'versione' -> 1});
json.decode_json('{"a": [1, 2]}');       // {'a' -> [1, 2]}
```

`encode_json(valore)` produce una stringa json; `decode_json(stringa)` la rilegge come valore
Blossom. Un json non valido dà un errore tipizzato con la posizione del problema.
