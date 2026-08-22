# `core.mc.nbt`

The raw data of the game.

```
import "core.mc.nbt" as nbt;

tag = nbt.nbt('{Fire: 20, Motion: [0.0d, 0.5d, 0.0d]}');
tag:'Fire';                          // 20
nbt.encode([1, 2, 3]);           // from a Blossom value to NBT
```

An NBT value is indexed like a map or a list, and can be modified in place: it is the way to touch
what the curated API does not expose yet.

### `nbt.nbt(value)`
Builds an NBT value from a string in command format, from a map or from a list.

### `nbt.decode(nbt)`
The opposite: from NBT to ordinary Blossom values (maps, lists, numbers, strings).

### `nbt.encode(value, force?)`
From Blossom values to NBT. With `force` true, what has no NBT shape becomes its string instead of
failing the conversion.

### `nbt.escape(string)`
Puts a string in the form the game would write it inside a tag.

### `nbt.matches(tag, predicate, respect_lists?)`
Whether a tag satisfies a partial predicate, with the same semantics as the vanilla commands.

### `nbt.storage()` · `nbt.storage(key)` · `nbt.storage(key, value)`
The NBT storage of the world, the one the commands call `data storage`: it is the way to exchange
data with functions and datapacks.
