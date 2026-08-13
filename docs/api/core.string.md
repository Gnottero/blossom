# `core.string`

Strings: building, searching, replacing with regular expressions, encodings.

```
import "core.string" as string;

string.upper('hello');                   // 'HELLO'
string.replace('a-b-c', '-', '+');       // 'a+b+c'
string.split('-', 'a-b-c');              // ['a', 'b', 'c']
```

### Building and formatting

`str(format, ...)` builds a string: `%s` inserts a value, `%d` an integer, `%.2f` a decimal with two
digits. With a single argument it returns the textual representation of the value.

### Upper and lower case

`lower(s)`, `upper(s)`, `title(s)`.

### Searching and replacing

`replace(s, regex, replacement)` replaces every occurrence, `replace_first(s, regex, replacement)`
only the first one. The pattern is a regular expression.

`split(separator, s)` splits a string; `join(separator, list)` is the inverse operation and lives in
[`core.list`](core.list.md).

### Encodings

`encode_b64(s)` / `decode_b64(s)` for base64, `hash_code(v)` for a stable hash of any value.
