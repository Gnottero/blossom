# 2. Values

## The types

There are seven, and `system.type(v)` names them:

| Type | How it is written | Notes |
| --- | --- | --- |
| `number` | `42`, `-3`, `0.5`, `1e3` | integers and decimals are the same type |
| `string` | `'hello'` | single quotes, always |
| `bool` | `true`, `false` | |
| `list` | `[1, 2, 3]` | |
| `map` | `{'a' -> 1}` | |
| `null` | `null` | the "not there" value |
| `function` | `fn f() -> 1;` | a function is a value |

The game layer adds a few more, handled exactly the same way: `entity`, `block`, `text`, `nbt`,
`screen`.

```
import "core.system" as system;

system.type([1, 2]);        // 'list'
system.type(null);          // 'null'
system.type(true);          // 'bool'
```

## Numbers

One numeric type, backed by a `long` for as long as it can be and by a `double` when it has to be.
The consequences worth knowing:

```
7 / 2;              // 3.5   - division does not truncate
6 / 3;              // 2     - an integral result prints as an integer
-7 % 3;             // 2     - the modulo follows the sign of the divisor
2 ^ 10;             // 1024
```

`^` always goes through floating point, so on very large integers it loses the last digits:

```
2 ^ 62;             // 4611686018430000000, not 4611686018427387904
```

If you need exact 64-bit integers, multiply instead of raising to a power.

Rounding, roots, trigonometry and procedural noise live in
[`core.math`](../api/core.math.md). `math.number('42')` converts a string into a number and
returns `null` when the string is not one, so a value arriving from outside has to be checked:
`if(math.number(text) == null, ...)`.

## Strings

Single quotes. Double quotes are **not** a string literal: they appear only on the
`import "core.list" as list;` line, which is preamble rather than expression.

```
'a\'b';             // an inner quote
'line\nline';       // newline
'a\\b';             // backslash
'ab' * 3;           // 'ababab'
'n=' + 3;           // 'n=3'   - concatenation converts
```

A string **is not indexable**: `'abc':0` is `null`, not `'a'`. To work on characters go through
[`core.string`](../api/core.string.md) (`split`, `replace`, and `slice` from `core.list` over the
list of characters).

`string.str(format, ...)` builds text with `%s`, `%d`, `%.2f`:

```
import "core.string" as string;

string.str('%s has %d apples', 'ada', 3);      // 'ada has 3 apples'
```

## Lists

```
import "core.list" as list;

l = [10, 20, 30];
l:0;                        // 10
l:4;                        // 20 - indices wrap around, 4 % 3 = 1
list.get(l, -1);            // 30 - negative indices count from the end
l:(-1);                     // 30 - the parentheses are required: 'l:-1' does not parse
list.size(l);               // 3
```

Two properties worth keeping in mind:

**Indices wrap around.** Reading past the end of a list is not an error, it is a lap. Deleting is a
different matter: `list.remove(l, 7)` on a three-element list raises an `IndexOutOfBoundsError`.

**A list is a reference.** Assigning it does not copy it:

```
a = [1, 2];
b = a;
list.put(b, 0, 9);
a;                          // [9, 2]  - it is the same list
```

For an independent copy there is `system.copy(a)`.

### Writing into a list

`put` replaces by default, and the modifiers say the rest:

```
l = [1, 2, 3];
list.put(l, 0, 9);              // [9, 2, 3]      replaces
list.put(l, 1, 5, 'insert');    // [9, 5, 2, 3]   slips it in before the index
list.put(l, null, 4);           // [..., 4]       appends
list.put(l, null, [7, 8], 'extend');   // appends the elements, not the list
```

Take care with `put` on a nested list: the first argument is **container and address together**, so
`l:0` means "element 0 of `l`", not "the list inside it":

```
l = [[1, 2]];
list.put(l:0, 9, 'replace');    // [9]        it replaced element 0 of l
list.put(l:0:1, 9);             // [[1, 9]]   this one writes inside
```

### Lazy lists

`list.range(...)` allocates nothing: it produces the elements as it goes, so `range(1000000)` costs
about as much as writing it.

```
list.join(',', list.range(0, 10, 3));       // '0,3,6,9'
list.map(list.range(3), _ * _);             // [0, 1, 4]
```

A lazy list does not know its own length, and may not have one at all: **`list.size(list.range(5))`
is a `TypeMismatchError`**, not a wrong number. To count it, materialise the list first:
`list.size(list.slice(list.range(5), 0, 5))`.

## Maps

```
import "core.map" as map;

m = {'a' -> 1, 'b' -> 2};
m:'a';                  // 1
m:'zz';                 // null - a missing key is null
map.keys(m);            // ['a', 'b']
map.pairs(m);           // [['a', 1], ['b', 2]]
m:'c' = 3;              // writing through the access operator, as with lists
```

Keys can be of any type, not just strings: `{1 -> 'a'}` works. Nested maps are read as a chain:
`m:'a':'b'`.

## Truthiness

In the condition position, these values are false:

| False | True |
| --- | --- |
| `false`, `null`, `0` | any other number |
| `''` (the empty string) | any other string, **including `'0'`** |
| `[]`, `{}` | any non-empty list or map |

```
if('0', 'true', 'false');       // 'true'  - it is a non-empty string
if([], 'true', 'false');        // 'false'
```

## Equality

`==` compares contents, and on lists and maps it is deep:

```
[1, 2] == [1, 2];               // true
{'a' -> 1} == {'a' -> 1};       // true
```

Between a number and a string the comparison is **permissive**: `'1' == 1` is `true`, and so is
`true == 1`. When you need to tell them apart, compare the types as well:

```
import "core.system" as system;

fn identical(a, b) -> system.type(a) == system.type(b) && a == b;
```

## null

`null` arrives from a missing key, from a variable that was never assigned, and from a function that
returns nothing. In arithmetic it counts as zero (`null + 1` is `1`); in a condition it is false.

A variable that was never assigned is `null` without complaint; a **module symbol** that does not
exist is an `UndefinedSymbolError` at load time instead. The difference is deliberate: the first is
a local slip that shows up immediately, the second is a misspelled name that must be stopped before
it runs.
