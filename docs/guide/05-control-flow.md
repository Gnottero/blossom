# 5. Control flow

There are no statements: `if`, the loops and the transformations are functions that return a value.
Which means they compose, and that a condition can go wherever a value goes.

## `if`

```
if(condition, then);
if(condition, then, otherwise);
if(c1, a1, c2, a2, ..., otherwise);         // a chain: the first true one wins
```

```
if(health < 5, 'critical', health < 15, 'hurt', 'healthy');
if(false, 'a');             // null: with no final branch, if nothing matched the result is null
```

Branches that are not taken **are not evaluated**: `if(x != 0, 100 / x, 0)` is safe.

## `match`

When the conditions are all comparisons against the **same** value, `match` says it once:

```
match(damage,
    0            -> 'unharmed',
    d: num       -> if(d > 20, 'severe', 'hurt'),
    else         -> 'unknown');
```

A pattern to the left of the arrow can be:

| Pattern | Matches |
| --- | --- |
| a value (`0`, `'hello'`) | when the subject is equal to it |
| a name (`d`) | always, and binds the subject to that name |
| a name with a type (`d: num`) | when the subject has that type, and binds it |
| `else` | the final branch |

The subject is evaluated **once**, and the bodies of the arms that are not taken are not evaluated at
all. When no arm matches, the result is `null`, just as for an `if` with no final branch.

There are no guards (`d if d > 20 -> ...`): they would need an infix `if`, that is new grammar, and
an `if` inside the arm's body says the same thing.

## Loops

| | Signature | Returns |
| --- | --- | --- |
| `loop` | `loop(times, body)` | the last value of the body |
| `for` | `for(list, body)` | **how many times** the body came out true |
| `while` | `while(condition, ceiling, body)` | the last value of the body |
| `c_for` | `c_for(init, condition, step, body)` | the last value of the body |

```
sum = 0;
loop(5, sum += _);                      // _ is 0, 1, 2, 3, 4

for([1, 2, 3], sum += _);               // _ is the element, _i its index

how_many_high = for(players, entity.query(_, 'y') > 100);   // for counts the true ones

i = 0;
while(i < 3, 1000, i += 1);             // 1000 is the iteration ceiling, not the condition

c_for(i = 0, i < 10, i += 2, sum += i);
```

The **ceiling** of `while` is not decorative: it is the maximum number of laps, and it exists
because a loop running inside an event holds the server still. Reaching it is not an error, the loop
simply ends.

`break()` leaves the loop, `continue()` skips to the next lap, and both accept a value that becomes
the value of the loop (or of the iteration):

```
loop(10, if(_ == 3, break(_)); 0);      // 3

sum = 0;
for([1, 2, 3], if(_ == 2, continue()); sum += _);      // skips the 2
```

`break` leaves the **innermost** loop. There is no labelled `break`: to leave two levels, use a
function and `return`.

`exit(value)` is a different thing: it stops the whole execution in progress, not just the loop.

## Transformations

They live in [`core.list`](../api/core.list.md) and take an **expression**, not a function: inside
it, `_` is the current element, `_i` its index and `_a` the accumulator of `reduce`.

```
import "core.list" as list;

list.map([1, 2, 3], _ * 2);                 // [2, 4, 6]
list.map([10, 20], _i);                     // [0, 1]
list.filter([1, 2, 3, 4], _ % 2 == 0);      // [2, 4]
list.reduce([1, 2, 3, 4], _a + _, 0);       // 10
list.find([1, 2, 3], _ > 1);                // 2, or null when nothing matches
list.all([2, 4], _ % 2 == 0);               // true (on an empty list it is true)
list.sort(['bbb', 'a'], list.size(_));      // sorts by the computed key
```

`break()` inside a `map` truncates the result: `list.map([1,2,3], if(_ == 2, break()); _)` gives
`[1]`.

The same transformations also accept a **function** in place of the expression, and then the three
implicit names are replaced by parameters:

```
list.map([1, 2, 3], fn(x) -> x * 2);                    // [2, 4, 6]
list.map(['a', 'b'], fn(x, i) -> i);                    // [0, 1]  - the second one is the index
list.reduce([1, 2, 3], fn(a, x) -> a + x, 0);           // 10      - in reduce the first is the accumulator
```

Which of the two forms it is, is decided by the grammar and not by the value: you can see it in the
source, and it costs nothing at run time. The short form with `_` stays the fastest, because it
creates no call frame; the lambda in exchange gives the element a name, and a name cannot collide
with the one belonging to an enclosing loop.

The two forms do not mix: inside a `fn(...)` the three implicit names have no value, because that
function names its own parameters. Writing `list.map([1, 2, 3], fn() -> _ * 2)` is an error saying
so, and the same goes for reading `_` where no loop has bound it.

An already declared function is called directly:

```
fn score(player) -> ...;
list.map(entity.player('all'), fn(p) -> score(p));
```

## Choosing the right loop

- a list to transform → `map`/`filter`/`reduce`: they say what you are doing, and keep no counter by
  hand
- a volume of blocks → `scan.scan` or `scan.volume` from
  [`core.mc.scan`](../api/core.mc.scan.md), which are three nested loops written properly
- a long job that must not stall the tick → a `task` ([chapter 9](09-concurrency.md))
- a counter with an unusual step → `c_for`
- "until X happens", with a ceiling → `while`
