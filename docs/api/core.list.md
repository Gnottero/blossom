# `core.list`

Lists: building, transforming, sorting and access.

```
import "core.list" as list;

list.map([1, 2, 3], _ * 2);              // [2, 4, 6]
list.filter([1, 2, 3, 4], _ % 2 == 0);   // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);    // 10
```

Lists are written `[1, 2, 3]` and read with the `:` operator (`l:0`), which is syntax and needs no
import. Everything else goes through this module.

### Building

`range(end)`, `range(start, end)`, `range(start, end, step)` — lazy: the elements are produced as
they go, so `range(1000000)` does not allocate a million values. A lazy list does not know its own
length: `size(range(5))` does **not** answer `5`; to get it, materialise the list first with
`slice(range(5), 0, 5)`.

### Transforming

`map(list, expression)`, `filter(list, condition)`, `reduce(list, expression, initial)`. Inside the
expression `_` is the current element, `_i` its index and `_a` the accumulator of `reduce`.

`find(list, condition)` returns the first element that satisfies the condition, `all(list,
condition)` says whether they all satisfy it.

### Sorting

`sort(list)` sorts by the natural order of the values; `sort_key(list, key)` by the result of the key
expression.

### Access and modification

`size(l)`, `get(l, index)`, `slice(l, from, to?)`, `reverse(l)`.

`has(l, index)` asks whether **that index** exists, not whether the list contains a value: for
membership there is `(l ~ value) != null`, which returns the index or `null`.

`put(l, index, value)` **replaces**; `put(l, index, value, 'insert')` slips it in before the index;
`put(l, null, value)` appends; `put(l, null, other_list, 'extend')` appends the elements of the other
list instead of the list itself. `remove(l, index)` removes — and unlike reading, which wraps
indices, removing an index that does not exist is an `IndexOutOfBoundsError`.

When the first argument is already an access (`put(l:0, ...)`), that is **container and address
together**: `put(l:0, 9, 'replace')` replaces element `0` of `l`, and to write inside a nested list
you go down one level, `put(l:0:1, 9)`.

`join(separator, list)` joins the elements into a string.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `length(l)` | `size(l)` |
| `delete(l, i)` | `remove(l, i)` |
| `first(l, cond)` | `find(l, cond)` |
| `split(sep, s)` | it lives in [`core.string`](core.string.md) |
