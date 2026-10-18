# 3. Operators

## The precedence table

From the top (binds tightest) downwards:

| Level | Operators | |
| --- | --- | --- |
| access | `:` `~` | `l:0`, `'text' ~ 'regex'` |
| unary | `-` `!` `...` | `-x`, `!flag`, `...list` |
| power | `^` | right-associative: `2^2^3` is `2^8` |
| multiplication | `*` `/` `%` | |
| addition | `+` `-` | |
| comparison | `<` `<=` `>` `>=` | |
| equality | `==` `!=` | |
| conjunction | `&&` | lazily evaluated |
| disjunction | `\|\|` | lazily evaluated |
| pipe | `\|>` | `list \|> list.map(f)` |
| assignment | `=` `+=` `<>` | |
| definition | `->` | |
| declaration | `fn` `let` `let mut` `pub` | they take the whole definition that follows |
| sequence | `;` | |

Unary minus binds **tighter** than the power operator: `-2 ^ 2` is `4`, not `-4`.

## Arithmetic

```
1 + 2;          3 - 1;          2 * 3;          7 / 2;          7 % 3;          2 ^ 10;
```

`+` does different things depending on its operands, and all of them are predictable:

```
'n=' + 3;                   // 'n=3'      concatenates, converting
[1, 2] + [10, 20];          // [11, 22]   adds element by element
[1, 2] + 1;                 // [2, 3]     adds the scalar to each element
[1, 2] + [1, 2, 3];         // TypeMismatchError: lists of different lengths
'ab' * 3;                   // 'ababab'
```

Adding two lists does **not** concatenate them. For that there is
`list.put(l, null, other, 'extend')`.

## Comparison and equality

`<` `<=` `>` `>=` order numbers and strings (uppercase before lowercase: sorting `['b','A','a']`
gives `[A, a, b]`). `==` and `!=` compare contents, deeply on lists and maps.

The comparison is forgiving across types — `'1' == 1` is true, and so is `true == 1` — and what it
calls equal is one and the same key in a map: `{1 -> 'a'}` is found by `'1'` as well. Being
forgiving, it is not transitive (`'1' == true` is false), so where the type matters, compare
`system.type(value)` too.

Do not chain comparisons: `1 < 2 < 3` evaluates left to right as `(1 < 2) < 3`, that is `true < 3`,
and the result does not mean what it looks like. Write `1 < 2 && 2 < 3`.

## Logic

`&&` and `||` are lazy and **return a value**, not a boolean:

```
0 && 5;             // 0    - it stopped at the first falsy operand
3 && 5;             // 5    - the last one evaluated
0 || 5;             // 5    - the first truthy one
false && (1/0);     // false, and the division by zero is never executed
```

Hence the idiom for a fallback value:

```
name = configuration:'name' || 'unnamed';
```

`!` negates according to the truthiness rules of [chapter 2](02-values.md): `!0`, `!''`, `!null` and
`![]` are all `true`.

## Access: `:`

```
l:0;                    // list element
m:'key';                // map value
m:'a':'b';              // chained
l:0 = 9;                // in write position too
m:'new' = 1;            // creates the key
```

`:` is syntax, not a function: it needs no import. Inside a function signature the same character
means something else: the type of a parameter (`fn f(a: num)`), see [chapter 4](04-functions.md).

A negative index needs parentheses: `l:(-1)`, because `l:-1` does not parse. Alternatively,
`list.get(l, -1)`.

## Interpolated strings: `"..."`

A single quote makes a literal string, and nothing inside it is interpreted. A **double quote**
substitutes the value of whatever sits inside `${...}`:

```
name = 'ada';
points = 12;

"hello ${name}, you have ${points} points";     // 'hello ada, you have 12 points'
"twice that is ${points * 2}";                  // 'twice that is 24'
'hello ${name}';                                // 'hello ${name}' - single quotes do not interpolate
```

Any expression fits inside the braces, calls included — a map literal too, since the braces are
counted. To write a literal `${`, escape it:

```
"esc \${name}";                                 // 'esc ${name}' - no substitution
```

The splitting happens **while the code is being read**: at run time there is no string left to
re-examine, only a concatenation that has already been written out, and a string with no
placeholders stays a constant.

## Search: `~`

One operator, three meanings, according to the type on the left:

```
'hello world' ~ 'o w';              // 'o w'      - regex: the matched portion, null if absent
'v1.2' ~ '([0-9])\.([0-9])';        // ['1', '2'] - with groups: the list of groups
['a', 'b', 'c'] ~ 'c';              // 2          - the index, null if absent
{'a' -> 1} ~ 'a';                   // 'a'        - the key if present, null otherwise
```

On a list it returns **the index**, which for the first element is `0`, and `0` is false in a
condition. If what you mean is "does it contain this?", write `(l ~ x) != null`. Not
`list.has(l, x)`: on a list, `has` asks whether that **index** exists (on a map it asks about the
key, as you would expect).

## Pipe: `|>`

`a |> f(b)` means `f(a, b)`: the value on the left becomes the **first argument** of the call on the
right. It exists so that a chain of transformations reads in the order in which it happens, instead
of from the inside out.

```
import "core.list" as list;

[1, 2, 3, 4]
    |> list.filter(fn(x) -> x % 2 == 0)
    |> list.map(fn(x) -> x * 2);            // [4, 8]
```

The rewrite happens **while the code is being read**: the pipe disappears and what remains is the
call with one extra argument in front. At run time it costs nothing, and chaining three
transformations costs exactly what writing them nested would.

It binds tighter than assignment and looser than `||`, so `x = a |> f()` puts the result in `x`, and
`(a || b) |> f()` is what it looks like. If the right-hand side is a value rather than a call
(`x |> f`, with `f` bound to a function) it is called all the same, and a lambda written on the spot
is read as one thing even without parentheses:

```
5 |> fn(x) -> x * 2;                    // 10
```

## Assignment

```
a = 1;              // assigns, and returns the assigned value
a += 2;             // adds
l += 3;             // on a list: appends
s += 'tail';        // on a string: concatenates
a <> b;             // swaps two variables
```

`<>` swaps **variables**, not positions inside a container: `l:0 <> l:1` is an error.

An assignment returns the value, so `a = (b = 3)` puts `3` in both: rarely handy, but not an error.

## Sequence: `;`

`;` separates expressions and evaluates to the **last** one:

```
(1; 2; 3);          // 3
```

A trailing `;` is allowed. The body of a function is typically a parenthesised sequence:

```
fn area(w, h) -> (
    base = w * h;
    base / 2
);
```

The result is the last line: no mandatory `return` (though `return(v)` exists, to leave early).

## Spreading: `...`

As a call argument, it unrolls a list:

```
f(a, b) -> a + b;
call('f', ...[1, 2]);       // 3
[0, ...[1, 2]];             // [0, 1, 2]
```

As the last parameter of a signature, it collects the rest (see [chapter 4](04-functions.md)):

```
fn how_many(first, ...rest) -> [first, rest];
how_many(1, 2, 3);          // [1, [2, 3]]
how_many(1);                // [1, []]
```

## Definition: `->`

`->` has two meanings, which context separates without ambiguity:

```
fn sum(a, b) -> a + b;          // function definition
m = {'a' -> 1, 'b' -> 2};       // key/value pair, inside a map literal
```
