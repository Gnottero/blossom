# 4. Functions

## Declaring

```
fn double(x) -> x * 2;

fn area(base, height) -> (
    half = base * height / 2;
    half
);
```

`fn` registers the function in the module's symbol table **while the module is loading**, so nothing
is left to look up at run time. It is also what lets errors say "in `tower.build`" instead of "in
some function".

Without `fn`, `name(args) -> body` still defines a function, but as the side effect of an
expression: use it for a throwaway function inside a body, not for a module's interface.

`pub` in front makes it visible to other modules:

```
pub fn distance(a, b) -> ...;       // everyone can see it
fn internal(x) -> ...;              // this module only
```

The name is yours. `fn map(...)`, `fn size(...)`, `fn get(...)` are ordinary names: the library is
reached through an alias (`list.map`), so nothing of yours can shadow it. The only reserved names
are the ones you write without importing anything — `break`, `c_for`, `call`, `continue`, `exit`,
`fn`, `for`, `if`, `loop`, `match`, `not`, `outer`, `print`, `return`, `throw`, `try`, `while` —
which are grammar rather than library.

## Anonymous functions

`fn(...)` without a name is a function written on the spot:

```
double = fn(x) -> x * 2;
double(3);                              // 6

list.map([1, 2, 3], fn(x) -> x * 2);    // [2, 4, 6]
event.on('player_dies', fn(who, cause) -> (log(who); false));
```

The two forms cannot be confused: `fn name(...)` declares, `fn(...)` is a value. What decides is the
parenthesis immediately after `fn`.

A lambda **captures the frame it is born in**, so it sees the variables around it without having to
declare them with `outer`:

```
base = 10;
add = fn(x) -> x + base;
add(3);                                 // 13
```

Only the author of a lambda pays for that capture: a named function captures nothing, as before.

## Who sees what

Inside a function these are visible: its parameters, the variables it assigns, and the **module
bindings** declared with `let` or `let mut`.

```
let threshold = 10;         // immutable
let mut counter = 0;        // reassignable

pub fn count() -> (
    counter += 1;                       // fine: it is a 'let mut'
    if(counter > threshold, 'too many', counter)
);
```

A variable assigned at module level **without** `let` is not a module binding: it stays in the body,
and functions do not see it.

```
counter = 0;                            // no: this is a body variable
fn bump() -> counter += 1;              // writes to something else entirely
bump();
counter;                                // 0
```

Assigning to an immutable `let` is an error, and it says so:

```
TypeMismatchError
  'threshold' is an immutable binding: declare it 'let mut threshold' if it has to change.
```

To read a variable of the **calling** frame there is `outer`, declared in the signature:

```
base = 7;
fn read(outer(base)) -> base;
read();                         // 7
```

## How arguments arrive

Scalar values (numbers, strings, booleans) arrive **by copy**; lists and maps **by reference**.

```
fn increment(v) -> (v += 1; null);
n = 1;
increment(n);
n;                              // 1  - unchanged

fn append(l) -> list.put(l, null, 9);
values = [1];
append(values);
values;                         // [1, 9]  - genuinely modified
```

If a function must not touch the list it receives, copy it: `system.copy(l)`.

## Number of arguments

Calling with the wrong count is an `ArityError`, raised at the call boundary:

```
ArityError
  Function f(a, b) wants at least 2 arguments.
```

The last parameter can collect the rest:

```
fn record(level, ...messages) -> [level, messages];

record('info', 'a', 'b');       // ['info', ['a', 'b']]
record('info');                 // ['info', []]
```

It has to be the **last** one — it takes everything that is left, so nothing can follow it — and no
two parameters may share a name. Both are `ParseError`s at the line where the signature is written.

## Default values and named arguments

A parameter can declare a value to use when the caller does not pass one:

```
fn place(pos, block, update = true) -> ...;

place(p, 'stone');                      // update is true
place(p, 'stone', false);               // by position
place(p, 'stone', update = false);      // by name
```

The default is re-evaluated **on every call that uses it**: `fn f(l = [])` gives a fresh list each
time, not the same one forever.

Named arguments go **at the end**, after the positional ones, and among themselves they may appear
in any order:

```
fn f(a, b, c) -> [a, b, c];
f(1, c = 3, b = 2);         // [1, 2, 3]
f(a = 1, 2);                // an error, reported at load time
```

Names are resolved at the **function boundary**, so a name that does not exist tells you which the
real ones are, and passing the same parameter twice is an error rather than a value that silently
wins.

## Parameter types

Optional, one per parameter:

```
fn distance(a: list, b: list) -> ...;
fn repeat(times: num, what) -> ...;         // 'what' stays free
fn apply(f: func, x: any) -> call(f, x);    // 'any' documents without constraining
```

The types are `num`, `str`, `bool`, `list`, `map`, `func`, `null`, `any`, plus those of the game:
`entity`, `block`, `text`, `nbt`, `screen`. It is spelled `func` and not `fn` because `fn` is
already the declaration keyword.

A misspelled type is an error **when you define** the function, not on the first call:

```
ParseError
  Unknown type 'nmu' in the signature: the types are num, str, bool, list, map, func, null, any...
  Did you mean: num?
```

A wrong argument is an error **at the function boundary**, which is the whole point: it says which
parameter, what it wanted and what arrived, instead of blowing up three lines into the body on an
unrelated operation.

```
TypeMismatchError
  in distance  (src/geo.bs:4:1)

  Parameter 'a' of distance wants a list, but got a string (two).
```

The check costs one string comparison per annotated parameter: putting types on a module's public
interface is almost always a good trade, putting them on a function called in a tight loop is not.

## Functions are values

```
fn triple(x) -> x * 3;

fn apply_twice(f, x) -> call(f, call(f, x));

apply_twice(triple, 2);         // 18   - by value
apply_twice('triple', 2);       // 18   - or by name
```

`call(f, ...)` invokes a function that arrives as a value or as a name. A function passed to another
module keeps resolving correctly: it carries the module it was defined in with it, so its internal
calls always see the right symbols, not those of whoever invokes it.

That is also why event handlers are registered **by passing the function**, not its name in a
string:

```
event.on('player_connects', welcome);
```

## Documenting a function

A comment that begins with **three** slashes, attached to the declaration, is that symbol's
documentation:

```
/// The score of a tower.
///
/// Counts the blocks and weights them by material; a tower shorter than three blocks does not count.
///
/// @param height   how many blocks tall it is
/// @param material the block it is made of
/// @returns the score, zero when the tower does not count
/// @throws value_exception when the material is not in the catalogue
/// @example score(12, 'stone');
/// @see tower.register
pub fn score(height: num, material: str) -> ...;
```

To the language they remain comments: `///` is `//` with one more slash, the interpreter skips them
like every other comment, and there is nothing to learn in order to make them work. The reader is
the editor: the VS Code extension shows the text when you hover over the name, in completion, and
in parameter help while you write the call; **Blossom: write the doc comment of the function**
writes the skeleton for you (`Ctrl+Shift+P`, or the lightbulb on the declaration line): one `@param`
per parameter, already with the right names.

There are six tags:

| Tag | What it is for |
| --- | --- |
| `@param name text` | one parameter; the name must be the one in the signature |
| `@returns text` | what comes back |
| `@throws type text` | an error that can escape, with the type that `try` filters on |
| `@example code` | a one-line example, rendered as code |
| `@see reference` | where to look for the rest |
| `@deprecated reason` | not to be used any more; the name is shown as deprecated |

The text before the first tag is the description: the **first line** is the summary (what you see
in completion, where there is room for a single line), and what follows a blank line is the rest,
shown when hovering over the name. An indented line under a tag continues it, so a long description
can wrap without repeating `@param`.

The block documents **the declaration immediately below it**: a blank line or an ordinary comment in
between detaches it, and at that point it documents nothing. This works for `fn`, for `let` and for
`module`, which is the place to write what the file is for.

## Recursion and early exit

```
fn fib(n) -> if(n < 2, n, fib(n - 1) + fib(n - 2));
fib(10);                        // 55
```

The value of a function is the last expression of its body. `return(v)` leaves earlier:

```
fn classify(x) -> (
    if(x < 0, return('negative'));
    if(x == 0, return('zero'));
    'positive'
);
```

Recursion depth has a ceiling (256 by default, see [chapter 9](09-concorrenza.md)): crossing it is
an `ExecutionBudgetError`, not a JVM stack overflow that takes the server down with it.
