# `core.json`

Conversion between Blossom values and json.

```
import "core.json" as json;

json.encode({'name' -> 'blossom', 'version' -> 1});
json.decode('{"a": [1, 2]}');       // {'a' -> [1, 2]}
```

`encode(value)` produces a json string; `decode(string)` reads it back as a Blossom value.
Invalid json gives a typed error with the position of the problem.
