# `core.math`

Mathematics: rounding, roots, logarithms, trigonometry, randomness, procedural noise and bit
manipulation.

```
import "core.math" as math;

math.sqrt(16);          // 4
math.max(3, 7, 2);      // 7
math.rand(10);          // a number between 0 (inclusive) and 10 (exclusive)
```

Blossom has one numeric type, backed by a 64-bit integer while it can be and by a double when it
cannot. Every function on this page accepts either and returns whichever fits; the ones documented
as returning an integer always do.

---

## Rounding and sign

### `math.round(x)`
Rounds to the nearest integer, halves away from zero. Returns an integer.

### `math.floor(x)`
The largest integer not greater than `x`. Returns an integer.

### `math.ceil(x)`
The smallest integer not less than `x`. Returns an integer.

### `math.abs(x)`
The absolute value.

### `math.sign(x)`
`-1`, `0` or `1` according to the sign of `x`.

### `math.relu(x)`
`x` when `x > 0`, otherwise `0`. The rectifier of neural-network fame, useful anywhere a value has
to be clamped at zero.

```
math.round(2.5);        // 3
math.floor(-2.1);       // -3
math.ceil(-2.1);        // -2
math.sign(-4);          // -1
math.relu(-4);          // 0
```

---

## Powers, roots and factorials

### `math.sqrt(x)`
The square root. Negative arguments give `NaN`, not an error.

### `math.exp(x)`
`e` raised to `x`.

### `math.fact(n)`
The factorial of `n`. Exact up to `n = 20`, where the result still fits in a 64-bit integer; from 21
to 170 it is computed as a double and loses precision; above 170 it returns the largest
representable double rather than overflowing to infinity.

Raising to a power is the `^` operator, not a function. It always goes through floating point, so on
very large integers it loses the last digits (`2 ^ 62` is not exact). Multiply when you need exact
64-bit integers.

```
math.sqrt(2);           // 1.4142135623730951
math.fact(20);          // 2432902008176640000  - exact
math.fact(21);          // 5.109094217170944e19 - a double from here on
```

---

## Logarithms

### `math.ln(x)`
The natural logarithm, base `e`.

### `math.ln1p(x)`
`ln(1 + x)`, accurate for `x` close to zero where `ln(1 + x)` would lose precision.

### `math.log(x)`
The logarithm in **base 2**, not the natural logarithm. `math.log(1024)` is `10`.

### `math.log1p(x)`
`log2(1 + x)`: the base-2 counterpart of `ln1p`.

### `math.log10(x)`
The logarithm in base 10.

```
math.ln(math.exp(1));   // 1
math.log(1024);         // 10
math.log10(1000);       // 3
```

---

## Trigonometry

**Angles are in degrees, not radians.** This is deliberate: Minecraft's own yaw and pitch are in
degrees, and the conversion that a script would otherwise write on every call is already applied
inside these functions.

### `math.sin(a)` · `math.cos(a)` · `math.tan(a)`
The circular functions of an angle in degrees.

### `math.asin(x)` · `math.acos(x)` · `math.atan(x)`
The inverses; the result is an angle in degrees.

### `math.atan2(y, x)`
The angle in degrees of the point `(x, y)` measured from the positive x axis, in the full range
−180 to 180. Note the argument order: `y` first, as in every language that has this function.

### `math.sec(a)` · `math.csc(a)` · `math.cot(a)` · `math.acot(x)`
Secant, cosecant, cotangent and inverse cotangent, also in degrees.

### `math.sinh(x)` · `math.cosh(x)` · `math.tanh(x)`
The hyperbolic functions. These take a **plain number**, not an angle: no conversion is applied.

### `math.asinh(x)` · `math.acosh(x)` · `math.atanh(x)` · `math.sech(x)` · `math.csch(x)` · `math.coth(x)`
The remaining hyperbolic functions and their inverses, also on plain numbers.

### `math.deg(radians)` · `math.rad(degrees)`
Convert between the two units, for when a value arrives in radians from somewhere else.

```
math.sin(90);           // 1      - degrees
math.atan2(1, 1);       // 45
math.deg(math.rad(90)); // 90
```

---

## Comparison

### `math.max(...)` · `math.min(...)`
Take several arguments, or a single list, and return the largest or smallest. They compare with the
same ordering as `<` and `>`, so they work on strings too. Calling either with no arguments is an
`ArityError`.

```
math.max(3, 7, 2);          // 7
math.max([3, 7, 2]);        // 7   - a single list works too
math.min('pear', 'apple');  // 'apple'
```

---

## Conversion

### `math.number(v)`
Converts a value into a number:

| Argument | Result |
| --- | --- |
| a number | the same number |
| a string that parses | the number it denotes |
| a string that does not parse | `null` |
| a list or a map | its size |

```
math.number('42');          // 42
math.number('42.5e1');      // 425
math.number('abc');         // null   - check it, it does not throw
math.number([1, 2, 3]);     // 3
```

Because a failed conversion is `null` and not an error, a value coming from outside (a command
argument, an http body, a file) has to be checked:

```
n = math.number(text);
if(n == null, throw('value_exception', "not a number: ${text}"));
```

---

## Randomness

### `math.rand(limit)` · `math.rand(limit, seed)`
With a number, returns a value from `0` (inclusive) to `limit` (exclusive), uniformly distributed:
a decimal, so `math.floor(math.rand(6)) + 1` is a die. With a list, returns one of its elements at
random. In a boolean position the drawn value is compared against 1, so `math.rand(limit)` is true with
probability `1 - 1/limit` (never for a limit of 1 or less, and one time in two for `math.rand(2)`).
For an explicit probability, compare instead: `math.rand(1) < 0.3`.

The optional second argument is a seed: the same seed gives the same sequence, on its own generator,
without disturbing the shared one.

### `math.reset_seed(seed)`
Restarts the generator bound to that seed. Returns `true` if a generator for that seed already
existed. Together with the two-argument form of `rand`, this is how a random sequence is made
reproducible, worth having in tests, and in any generator that must produce the same world twice.

```
math.rand(10);                  // 6.813...
math.floor(math.rand(6)) + 1;   // a d6
math.rand(['a', 'b', 'c']);     // one of the three

math.reset_seed(1234);
math.rand(100, 1234);           // always the same value after a reset with that seed
```

---

## Procedural noise

Coherent noise: nearby inputs give nearby outputs, which is what makes it usable for terrain,
textures and anything that has to look natural rather than random.

### `math.perlin(x)` · `math.perlin(x, y)` · `math.perlin(x, y, z)` · `math.perlin(x, y, z, seed)`
Samples Perlin noise in one, two or three dimensions. With four arguments the last is a seed, and
sampling happens on a generator of its own. Values are roughly in `-1..1`.

### `math.simplex(x, y)` · `math.simplex(x, y, z)` · `math.simplex(x, y, z, seed)`
Samples simplex noise. Unlike `perlin` it needs **at least two** dimensions; asking for one is an
`ArityError`.

### `math.mandelbrot(x, y, limit)`
How many iterations the point `(x, y)` survives before the Mandelbrot sequence diverges, up to
`limit`. A point inside the set returns `limit` itself.

```
math.perlin(x / 16, z / 16);            // a smooth height map
math.simplex(x / 32, z / 32, 1234);     // wait: 1234 here is z, not a seed
math.simplex(x / 32, z / 32, 0, 1234);  // this is the seed
```

---

## Bit operations

All of these work on the 64-bit integer representation of their arguments.

### `math.bitwise_and(...)` · `math.bitwise_or(...)` · `math.bitwise_xor(...)`
Fold the operation over every argument, left to right. They accept any number of arguments; with
none they return `null`.

### `math.bitwise_not(n)`
The bitwise complement.

### `math.bitwise_popcount(n)`
How many bits are set.

### `math.bitwise_shift_left(n, amount)`
Shifts left.

### `math.bitwise_shift_right(n, amount)`
Shifts right **logically**: the vacated bits are filled with zeros, so the result of shifting a
negative number is a large positive one.

All three shifts count bits, and a number is 64 of them: shifting by 64 or more leaves nothing
behind (`0`, or `-1` for the arithmetic shift of a negative number), it does not start over.
A negative amount is an error rather than a shift the other way — there is a function for that.
`math.bitwise_roll_left` and `math.bitwise_roll_right` are the exception, because a rotation is
defined modulo the width: rolling by 65 is rolling by 1.

### `math.bitwise_arithmetic_shift_right(n, amount)`
Shifts right **arithmetically**: the sign bit is preserved, so `-8 >> 1` is `-4`.

### `math.bitwise_roll_left(n, amount)` · `math.bitwise_roll_right(n, amount)`
Rotate the bits, the ones falling off one end reappearing at the other.

### `math.double_to_long_bits(x)` · `math.long_to_double_bits(n)`
Reinterpret the bits of a double as an integer and back. They are inverses of one another, and exist
for reading a binary format that stores floating-point values as integers.

```
math.bitwise_and(12, 10);                       // 8
math.bitwise_popcount(255);                     // 8
math.bitwise_shift_right(-8, 1);                // 9223372036854775804
math.bitwise_arithmetic_shift_right(-8, 1);     // -4
math.long_to_double_bits(math.double_to_long_bits(0.5));    // 0.5
```

---

## Constants

`pi` and `euler` are not part of this module: they are language constants, like `true`, `false` and
`null`, and are available without importing anything.

```
2 * pi;             // 6.283185307179586
euler;              // 2.718281828459045
```
