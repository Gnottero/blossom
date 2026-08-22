# `core.map`

Maps: reading keys, values and pairs, and writing entries.

```
import "core.map" as map;

m = {'a' -> 1, 'b' -> 2};
map.keys(m);            // ['a', 'b']
map.has(m, 'a');        // true
```

A map is written `{key -> value, ...}` and read with the `:` operator (`m:'a'`), which is syntax and
needs no import. Keys can be of any type, not only strings: `{1 -> 'a'}` is a valid map. A missing
key reads as `null`, which is why `map.has` exists: it is the only way to tell "absent" from
"present and null".

A map, like a list, is a **reference**: assigning it does not copy it. `system.copy(m)` makes an
independent copy.

---

## Reading

### `map.keys(m)`
The list of keys, in insertion order. On anything that is not a map, `null`.

### `map.values(m)`
The list of values, in the same order as the keys.

### `map.pairs(m)`
The list of `[key, value]` pairs, the form to iterate over when you need both at once, and the one
to sort when you want a map ordered by value.

### `map.get(m, key)` · `map.get(m, k1, k2, ...)`
The value for that key, or `null`. With several keys it descends through nested maps, so
`map.get(m, 'a', 'b')` is `m:'a':'b'` and gives `null` rather than an error if the intermediate step
is missing.

### `map.has(m, key)`
Whether the key is present, regardless of its value.

### `map.size(m)`
How many entries.

```
m = {'a' -> 1, 'b' -> 2};

map.keys(m);                // ['a', 'b']
map.values(m);              // [1, 2]
map.pairs(m);               // [['a', 1], ['b', 2]]
map.get(m, 'a');            // 1
map.has(m, 'zz');           // false
map.size(m);                // 2

list.sort(map.pairs(m), -_:1);      // the entries, by value, descending
```

---

## Writing

### `map.put(m, key, value)`
Sets the entry, creating the key if it was not there. Returns whether something changed. Writing
through the access operator does the same thing and is shorter: `m:'c' = 3`.

### `map.remove(m, key)`
Removes the entry and returns whether there was one. Removing a key that is not there is not an
error, unlike removing a list index that does not exist.

```
m = {'a' -> 1};
map.put(m, 'b', 2);         // m is {'a' -> 1, 'b' -> 2}
m:'c' = 3;                  // the same thing, written as an access
map.remove(m, 'a');         // m is {'b' -> 2, 'c' -> 3}
map.remove(m, 'zz');        // false, and nothing happens
```

---

## Maps shared between tasks

A single operation on a map shared between several tasks is atomic: an assignment, a `put`, a
`remove`, a read, an iteration. A **sequence** of operations is not: `m:'n' = m:'n' + 1` reads and
writes in two steps, and another task can slip in between them. For sequences there is
`task.synchronize` in [`core.task`](core.task.md).

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `length(m)` | `size(m)` |
| `delete(m, k)` | `remove(m, k)` |
