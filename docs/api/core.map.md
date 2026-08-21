# `core.map`

Maps: keys, values and pairs.

```
import "core.map" as map;

m = {'a' -> 1, 'b' -> 2};
map.keys(m);            // ['a', 'b']
map.has(m, 'a');        // true
```

Maps are written `{key -> value, ...}` and read with `:` (`m:'a'`), which is syntax.

### Reading

`keys(m)`, `values(m)`, `pairs(m)` — the last one returns a list of `[key, value]` pairs.
`get(m, key)`, `has(m, key)`, `size(m)`.

### Modification

`put(m, key, value)`, `remove(m, key)`.

Single operations on a map shared between several tasks are atomic; a *sequence* of operations is
not — for that there is `synchronize` in [`core.task`](core.task.md).

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `length(m)` | `size(m)` |
| `delete(m, k)` | `remove(m, k)` |
