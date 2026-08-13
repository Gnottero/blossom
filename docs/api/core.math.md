# `core.math`

Mathematics: advanced arithmetic, trigonometry, randomness and procedural noise.

```
import "core.math" as math;

math.sqrt(16);          // 4
math.max(3, 7, 2);      // 7
math.rand(10);          // a number between 0 and 10
```

### Arithmetic and rounding

`abs(x)`, `sign(x)`, `round(x)`, `floor(x)`, `ceil(x)`, `sqrt(x)`, `exp(x)`, `fact(n)`, `relu(x)`,
`number(v)` — conversion to a number, with a typed error (`TypeMismatchError`) when the value is not
one.

`max(...)` and `min(...)` take several arguments or a single list.

### Logarithms

`ln(x)`, `ln1p(x)`, `log(x)`, `log10(x)`, `log1p(x)`.

### Trigonometry

`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2(y, x)`, `sinh`, `cosh`, `tanh`, `asinh`,
`acosh`, `atanh`, `sec`, `sech`, `csc`, `csch`, `cot`, `coth`. Angles are in radians; `deg(rad)` and
`rad(deg)` convert between the two.

### Randomness

`rand(limit)` returns a number between 0 and the limit; with a list, one of its elements at random.
`reset_seed(seed)` restarts the generator bound to that seed, so a random sequence can be reproduced
— handy in tests.

### Procedural noise

`perlin(x, y?, z?, seed?)` and `simplex(x, y, z?, seed?)` sample coherent noise; `mandelbrot(x, y,
limit)` returns the number of iterations before divergence.

### Bit operations

`bitwise_and(a, b)`, `bitwise_or(a, b)`, `bitwise_xor(a, b)`, `long_to_double_bits(n)`.
