# `core.list`

Lists: building them, walking them, transforming them, sorting them and reading and writing
individual elements.

```
import "core.list" as list;

list.map([1, 2, 3], _ * 2);              // [2, 4, 6]
list.filter([1, 2, 3, 4], _ % 2 == 0);   // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);    // 10
```

A list is written `[1, 2, 3]` and read with the `:` operator (`l:0`), which is syntax and needs no
import. Everything else goes through this module.

Two properties of lists themselves, described in full in
[2. Values](../guide/02-values.md):

- **indices wrap around** on reading, so `[10, 20, 30]:4` is `20` and never an error, while removing
  an index that does not exist is an `IndexOutOfBoundsError`;
- **a list is a reference**, so passing one to a function and modifying it there modifies the
  original. `system.copy(l)` makes an independent copy.

---

## Building

### `list.range(end)` · `list.range(start, end)` · `list.range(start, end, step)`
Produces the numbers from `start` (default `0`, inclusive) to `end` (exclusive), stepping by `step`
(default `1`). A negative step counts down. If all three arguments are integers the result is a
sequence of integers, otherwise of decimals.

The result is a **lazy** list: nothing is allocated, and the elements are produced as they are
consumed, so `range(1000000)` costs about as much as writing it. Two consequences worth knowing:

- `list.size(list.range(5))` is a `TypeMismatchError`. A lazy list does not know its own length —
  it may not even have one. To get it, materialise the list first:
  `list.size(list.slice(list.range(5), 0, 5))`.
- Walking it twice is not free the second time; `map`, `filter` and friends consume it.

```
list.range(5);              // 0, 1, 2, 3, 4
list.range(2, 5);           // 2, 3, 4
list.range(0, 10, 3);       // 0, 3, 6, 9
list.range(3, 0, -1);       // 3, 2, 1
```

---

## Transforming

All of these take an **expression** as their last argument, in which `_` is the current element,
`_i` its index and `_a` the accumulator of `reduce`. They also accept a **function**, and then those
implicit names are replaced by the function's parameters. The choice is made by the grammar, costs
nothing at run time, and is described in
[5. Control flow](../guide/05-control-flow.md).

Passing `null` in place of the list is not an error: `map` and `filter` give back an empty list,
`find` gives `null`, `all` gives `true`.

### `list.map(list, expression)`
Applies the expression to every element and returns the list of results. `break()` inside the
expression truncates the result there, and `break(v)` truncates it after adding `v`;
`continue(v)` adds `v` and moves on.

### `list.filter(list, condition)`
Returns the elements for which the condition is true.

### `list.reduce(list, expression, initial)`
Folds the list into a single value. `_a` is the accumulator, starting from `initial`, and `_` the
current element. The value of the expression becomes the next accumulator.

### `list.find(list, condition)`
Returns the **first element** that satisfies the condition, or `null` when none does. It stops at
the first match rather than walking the whole list. `continue()` inside it is an error: there is
nothing to continue to.

### `list.all(list, condition)`
Whether every element satisfies the condition. On an empty list it is `true`.

```
list.map([1, 2, 3], _ * 2);                     // [2, 4, 6]
list.map([10, 20], _i);                         // [0, 1]
list.map([1, 2, 3], fn(x) -> x * 2);            // [2, 4, 6]

list.filter([1, 2, 3, 4], _ % 2 == 0);          // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);           // 10
list.find([1, 2, 3], _ > 1);                    // 2
list.all([2, 4], _ % 2 == 0);                   // true
```

---

## Sorting

### `list.sort(list)` · `list.sort(list, key)`
With one argument, sorts by the natural order of the values (numbers numerically, strings
lexicographically with uppercase before lowercase). With two, sorts by what the key computes for each
element; the key is an expression using `_`, or a function of one parameter.

Sorting returns a **new list**; the original is left as it was. The first argument has to be a real
list, not a lazy one.

```
list.sort([3, 1, 2]);                           // [1, 2, 3]
list.sort(['bbb', 'a', 'cc'], list.size(_));    // ['a', 'cc', 'bbb']
list.sort(['bbb', 'a', 'cc'], fn(x) -> list.size(x));
list.sort(map.pairs(scores), -_:1);             // by value, descending
```

Descending order has no flag: negate the key, as above, or `list.reverse` the result.

---

## Reading

### `list.size(l)`
How many elements. On a map it is the number of entries, on a string the number of characters, on
`null` it is `0`. Anything else — a number, a bool, a function — is a `TypeMismatchError`, and so is
a lazy list: see `range` above.

### `list.get(l, index)` · `list.get(l, i1, i2, ...)`
The element at that index. Indices wrap around, and negative ones count from the end, so
`list.get(l, -1)` is the last element. With several indices it descends through nested containers:
`list.get(l, 0, 1)` is `l:0:1`.

The `:` operator does the same and is shorter; `get` exists for a negative index, which as an
operator would need parentheses (`l:(-1)`), and for the nested form.

### `list.has(l, index)`
Whether **that index** exists, not whether the list contains that value. This is the single most
common misreading of this module. For membership, use the `~` operator:

```
list.has([10, 20, 30], 1);      // true  - index 1 exists
list.has([10, 20, 30], 20);     // false - there is no index 20

([10, 20, 30] ~ 20) != null;    // true  - this is "does it contain 20"
```

`~` returns the index, and the index of the first element is `0`, which is false in a condition.
That is why the comparison against `null` is written out.

### `list.slice(l, from)` · `list.slice(l, from, to)`
A new list with the elements from `from` (inclusive) to `to` (exclusive), or to the end when `to` is
omitted. Also the way to materialise a lazy list.

### `list.reverse(l)`
A new list with the elements in the opposite order. It wants a list; anything else is an error.

### `list.join(separator, l)` · `list.join(separator, v1, v2, ...)`
Joins the elements into a string, converting each with the same rules as `+`. With exactly two
arguments the second is the list; with more, the arguments themselves are the elements.

```
list.slice([1, 2, 3, 4], 1, 3);     // [2, 3]
list.reverse([1, 2, 3]);            // [3, 2, 1]
list.join(',', [1, 2, 3]);          // '1,2,3'
list.join('-', 'a', 'b');           // 'a-b'
```

---

## Writing

### `list.put(l, index, value)` · `list.put(l, index, value, mode)`
Writes into the list **in place**, and returns whether something was written. The mode decides what
"writing" means:

| Call | Effect |
| --- | --- |
| `put(l, i, v)` | replaces the element at `i` |
| `put(l, i, v, 'insert')` | slips `v` in before index `i`, growing the list |
| `put(l, null, v)` | appends `v` at the end |
| `put(l, null, v, 'extend')` | appends the **elements** of `v`, not `v` itself |

When the first argument is already an access (`put(l:0, ...)`), that is **container and address
together**: the address is `0` and the container is `l`, so `put(l:0, 9, 'replace')` replaces
element `0` of `l`. To write inside a nested list, go down one more level:

```
l = [[1, 2]];
list.put(l:0, 9, 'replace');    // [9]        replaced element 0 of l
list.put(l:0:1, 9);             // [[1, 9]]   this one writes inside
```

### `list.remove(l, index)`
Removes the element at that index and returns whether something was removed. Unlike reading,
**removing does not wrap**: an index outside the list is an `IndexOutOfBoundsError`.

```
l = [1, 2, 3];
list.put(l, 0, 9);                      // l is [9, 2, 3]
list.put(l, 1, 5, 'insert');            // l is [9, 5, 2, 3]
list.put(l, null, 4);                   // l is [9, 5, 2, 3, 4]
list.put(l, null, [7, 8], 'extend');    // l is [9, 5, 2, 3, 4, 7, 8]
list.remove(l, 0);                      // l is [5, 2, 3, 4, 7, 8]
```

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `length(l)` | `size(l)` |
| `delete(l, i)` | `remove(l, i)` |
| `first(l, cond)` | `find(l, cond)` |
| `sort_key(l, key)` | `sort(l, key)` (and `sort_key(l)` alone used to **shuffle**, which is a different operation with the same name) |
| `split(sep, s)` | it lives in [`core.string`](core.string.md) |
