# `core.mc.structure`

Salvare, piazzare e confrontare template di struttura.

```
import "core.mc.structure" as structure;

structure.structure_save('mio:torre', [0, 64, 0], [8, 80, 8]);
structure.structure_load('mio:torre', [100, 64, 100], 90);
structure.structure_matches('mio:torre', [100, 64, 100], [108, 80, 108], 90);
```

Sono gli **stessi template del blocco struttura** di vanilla, e vivono nello stesso posto
(`<mondo>/generated/<namespace>/structure/<nome>.nbt`): quello che uno script salva si apre con un
blocco struttura, e viceversa. È il motivo per cui queste funzioni valgono la pena rispetto a
copiare blocchi a mano.

### `structure_save(nome, da, a, con_entità?)`
Salva il volume fra i due angoli (inclusi) come template. `con_entità` è `false` di default.
Restituisce se il salvataggio è riuscito.

### `structure_load(nome, pos, rotazione?, specchio?, con_entità?, integrità?, seme?)`
Piazza il template. Restituisce se è riuscito, `null` se il template non esiste.

| Argomento | Valori | Default |
| --- | --- | --- |
| `rotazione` | `0`, `90`, `180`, `270` (anche `'clockwise_90'` ecc.) | `0` |
| `specchio` | `'none'`, `'left_right'`, `'front_back'` | `'none'` |
| `con_entità` | | `true` |
| `integrità` | da `0` a `1`: sotto `1` toglie blocchi a caso, come il blocco struttura | `1` |
| `seme` | il seme del caso, per rendere ripetibile una rovina | casuale |

**`pos` è sempre l'angolo minimo del risultato**, anche ruotato o specchiato. Rotazione e
specchiatura girerebbero la struttura *attorno* all'ancora, e senza questa correzione la stessa
chiamata con rotazione diversa finirebbe in un punto diverso — cosa che nessuno trova ovvia. È anche
quello che rende `structure_load` e `structure_matches` reciproci.

### `structure_matches(nome, da, a, rotazione?, specchio?)`
Se la regione fra i due angoli corrisponde al template. `null` se il template non esiste — che è
diverso da "non combacia".

Una regione di dimensioni diverse non può combaciare, e viene scartata subito senza leggere un
blocco. Un template può avere più **palette**, cioè varianti dello stesso volume: combacia se ne
combacia almeno una. I blocchi `structure_void` non contano, come in vanilla.

### `structure_size(nome, rotazione?)`
Le dimensioni del template come `[x, y, z]`, tenendo conto della rotazione, o `null` se non esiste.
