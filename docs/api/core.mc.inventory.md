# `core.mc.inventory`

The inventories: of whoever carries one, and of whatever contains one.

```
import "core.mc.inventory" as inventory;

inventory.size(player);                    // how many slots
inventory.get(player, 0);                  // ['minecraft:stone', 64, {}]
inventory.set(player, 0, 1, 'diamond');
```

An inventory is named with the entity carrying it or with the position of the block containing it
(chest, furnace, hopper...). An item is a triple `[name, count, nbt]`.

### `size(inventory)`
How many slots it has, `null` when there is no inventory there.

### `get(inventory, slot?)`
The content of a slot, or the whole inventory when the slot is not given.

### `set(inventory, slot, count, item?, nbt?)`
Writes a slot. A count of `0` empties it.

### `find(inventory, item, from_slot?)`
The first slot containing that item, `null` when there is none.

### `remove(inventory, item, count?)`
Takes items out of the inventory, wherever they are.

### `has(inventory, items...)`
Whether the inventory holds everything needed.

### `drop(inventory, slot, count?)`
Throws the content of a slot on the ground.

---

## Coming from `core.mc.item`

Inventories were mixed in with items, and they are a different thing: an item is a value you hold,
an inventory is a place in the world you read and write. They now have a module each.

| Before | Now |
| --- | --- |
| `item.inventory_size(i)` | `inventory.size(i)` |
| `item.inventory_get(i, s?)` | `inventory.get(i, s?)` |
| `item.inventory_set(i, s, n, ...)` | `inventory.set(i, s, n, ...)` |
| `item.inventory_find(i, x, from?)` | `inventory.find(i, x, from?)` |
| `item.inventory_remove(i, x, n?)` | `inventory.remove(i, x, n?)` |
| `item.inventory_has_items(i, ...)` | `inventory.has(i, ...)` |
| `item.drop_item(i, s, n?)` | `inventory.drop(i, s, n?)` |
