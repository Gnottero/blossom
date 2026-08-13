# `core.json`

Conversion between Blossom values and json.

```
import "core.json" as json;

json.encode_json({'name' -> 'blossom', 'version' -> 1});
json.decode_json('{"a": [1, 2]}');       // {'a' -> [1, 2]}
```

`encode_json(value)` produces a json string; `decode_json(string)` reads it back as a Blossom value.
Invalid json gives a typed error with the position of the problem.
