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

`replace(s, regex, replacement)` replaces every occurrence; with a count in the tail it replaces only
that many. The pattern is a regular expression.

```
string.replace('a-b-c', '-', '+');       // 'a+b+c'
string.replace('a-b-c', '-', '+', 1);    // 'a+b-c'
```

`split(separator, s)` splits a string; `join(separator, list)` is the inverse operation and lives in
[`core.list`](core.list.md), because its subject is the list.

### Encodings

`encode_base64(s)` / `decode_base64(s)` for base64, `hash_code(v)` for a stable hash of any value.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `encode_b64(s)` | `encode_base64(s)` |
| `decode_b64(s)` | `decode_base64(s)` |
| `replace_first(s, p, w)` | `replace(s, p, w, 1)` — how many is a number, not a name |
| `join(sep, l)` | it lives in [`core.list`](core.list.md) |
