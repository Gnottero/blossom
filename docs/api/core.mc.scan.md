# `core.mc.scan`

Percorrere volumi di blocchi senza scrivere tre cicli annidati.

```
import "core.mc.scan" as scan;
import "core.mc.block" as block;

// tutti i blocchi in un cubo 5x5x5 attorno all'origine
scan.scan([0, 64, 0], [2, 2, 2], block.set(_x, _y, _z, 'stone'));
```

Dentro il corpo, `_x`, `_y`, `_z` sono le coordinate del punto corrente e `_` è il blocco.

### `scan(centro, raggi, espressione)` · `scan(centro, raggi_giù, raggi_su, espressione)`
Percorre un parallelepipedo attorno al centro. I raggi sono **tre numeri** o una tripla, come le
posizioni: `scan(pos, [2, 1, 2], ...)` e `scan(pos, 2, 1, 2, ...)` sono la stessa cosa. Con due
gruppi di raggi il volume può essere asimmetrico.

Restituisce quante volte l'espressione è risultata vera: contare i blocchi che soddisfano una
condizione è una riga sola.

### `volume(da, a, espressione)`
Come `scan`, ma il volume si dà con due angoli invece che con centro e raggio.

### `rect(centro, raggio?, salto?)` · `diamond(centro, raggio?, altezza?)`
Le posizioni di un parallelepipedo o di un ottaedro, come lista pigra: si consumano man mano, senza
costruire in memoria tutto il volume.

### `neighbours(pos)`
Le sei posizioni adiacenti.
