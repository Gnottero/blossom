# `core.string`

Stringhe: costruzione, ricerca, sostituzione con espressioni regolari, codifiche.

```
import "core.string" as string;

string.upper('ciao');                    // 'CIAO'
string.replace('a-b-c', '-', '+');       // 'a+b+c'
string.split('-', 'a-b-c');              // ['a', 'b', 'c']
```

### Costruzione e formato

`str(formato, ...)` costruisce una stringa: `%s` inserisce un valore, `%d` un intero, `%.2f` un
decimale con due cifre. Con un solo argomento restituisce la rappresentazione testuale del valore.

### Maiuscole e minuscole

`lower(s)`, `upper(s)`, `title(s)`.

### Ricerca e sostituzione

`replace(s, regex, sostituto)` sostituisce tutte le occorrenze, `replace_first(s, regex, sostituto)`
solo la prima. Il pattern è un'espressione regolare.

`split(separatore, s)` divide una stringa; `join(separatore, lista)` è l'operazione inversa e vive in
[`core.list`](core.list.md).

### Codifiche

`encode_b64(s)` / `decode_b64(s)` per base64, `hash_code(v)` per un hash stabile di qualunque valore.
