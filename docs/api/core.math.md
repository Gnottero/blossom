# `core.math`

Matematica: aritmetica avanzata, trigonometria, casualità e rumore procedurale.

```
import "core.math" as math;

math.sqrt(16);          // 4
math.max(3, 7, 2);      // 7
math.rand(10);          // un numero fra 0 e 10
```

### Aritmetica e arrotondamenti

`abs(x)`, `sign(x)`, `round(x)`, `floor(x)`, `ceil(x)`, `sqrt(x)`, `exp(x)`, `fact(n)`, `relu(x)`,
`number(v)` — conversione a numero, errore tipizzato (`TypeMismatchError`) se il valore non lo è.

`max(...)` e `min(...)` accettano più argomenti o una lista sola.

### Logaritmi

`ln(x)`, `ln1p(x)`, `log(x)`, `log10(x)`, `log1p(x)`.

### Trigonometria

`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2(y, x)`, `sinh`, `cosh`, `tanh`, `asinh`,
`acosh`, `atanh`, `sec`, `sech`, `csc`, `csch`, `cot`, `coth`. Gli angoli sono in radianti;
`deg(rad)` e `rad(deg)` convertono.

### Casualità

`rand(limite)` restituisce un numero fra 0 e il limite; con una lista, un suo elemento a caso.
`reset_seed(seme)` riavvia il generatore associato a quel seme, così una sequenza casuale si può
riprodurre — utile nei test.

### Rumore procedurale

`perlin(x, y?, z?, seme?)` e `simplex(x, y, z?, seme?)` campionano rumore coerente; `mandelbrot(x,
y, limite)` restituisce il numero di iterazioni prima della divergenza.

### Operazioni sui bit

`bitwise_and(a, b)`, `bitwise_or(a, b)`, `bitwise_xor(a, b)`, `long_to_double_bits(n)`.
