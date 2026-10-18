# guardian-tower

Example project: when someone rings a bell, it builds a lit tower next to it, as tall as needed, and
keeps count of the ones it has built.

It shows everything a Blossom project really uses:

- **three modules** (`main`, `tower.construction`, `tower.registry`) with aliased imports
- **private symbols**: `column` and the materials live inside `tower.construction` and do not leave it
- **module state**: `tower.registry` keeps the list of towers in a private `let mut`, and exposes it
  only through functions
- **a cancellable event**: the `bell_rings` handler returns `false`, so the bell still rings
- **`try`/`catch`**: if the construction fails, the error is shown to the players and the event does
  not drag anything else down with it

## Trying it

```
cp -r examples/guardian-tower <world>/blossom/guardian-tower
```

```
/blossom script load guardian-tower
```

Place a bell, ring it, and watch the tower go up.

```
/blossom script in guardian-tower tower_count()
/blossom script in guardian-tower demolish_all()
```
