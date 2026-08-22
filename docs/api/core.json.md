# `core.json`

Conversion between Blossom values and json text.

```
import "core.json" as json;

json.encode({'name' -> 'blossom', 'version' -> 1});     // '{"name":"blossom","version":1}'
json.decode('{"a": [1, 2]}');                           // {'a' -> [1, 2]}
```

---

### `json.encode(value)`
Produces the json representation of a value, as a string. The mapping is the obvious one:

| Blossom | json |
| --- | --- |
| `number` | number |
| `string` | string |
| `bool` | `true` / `false` |
| `null` | `null` |
| `list` | array |
| `map` | object, with every key converted to a string |

`NaN` and infinity have no way of being written in json, so encoding one raises a `json_error`
instead of producing a text no reader would accept back.

Values of the game types (`entity`, `block`, `nbt`, …) encode as their textual representation rather
than as a structure; to serialise game data, go through [`core.mc.nbt`](core.mc.nbt.md) instead.

### `json.decode(text)`
Reads json text back into a value. Objects become maps with string keys, arrays become lists,
numbers become numbers.

Malformed json throws a **`json_error`**, which is catchable and sits under `io_exception` in the
filter hierarchy:

```
fn read_configuration(text) -> try(
    json.decode(text),
    'json_error', (
        print('unreadable configuration, using the defaults');
        {}
    )
);
```

Note that `try(json.decode(text))` without the filter catches nothing: the short form of `try`
catches user exceptions only. See [7. Errors](../guide/07-errori.md).

One consequence of constant folding: when the argument is a literal, as in `json.decode('{')`, the
call can be evaluated while the module is being compiled, and the error then arrives at load time
rather than inside your `try`.

---

## Reading and writing json files

For a file there is no need to encode by hand: [`core.io`](core.io.md) has `io.read_json` and
`io.write_json`, which do the conversion and the disk access in one call, inside a declared root.
