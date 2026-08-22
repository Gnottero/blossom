# `core.mc.inventory`

Inventories: of whoever carries one, and of whatever contains one.

```
import "core.mc.inventory" as inventory;

inventory.size(player);                    // how many slots
inventory.get(player, 0);                  // ['minecraft:stone', 64, {}]
inventory.set(player, 0, 1, 'diamond');
```

An inventory is named by the **entity** carrying it or by the **position** of the block containing it
— chest, furnace, hopper, barrel, shulker box, and so on. Both forms are accepted everywhere in this
module, so the first argument is written the same way whether the target is a player or a block.

An item is a triple `[name, count, nbt]`, and an empty slot reads as
`['minecraft:air', 0, {}]` rather than as `null`.

Every function returns `null` when there is no inventory at that place at all — a position holding
plain stone, an entity that carries nothing.

---

### `inventory.size(inventory)`
How many slots the inventory has. A player's is 41 (36 main, 4 armour, 1 off-hand), a single chest's
is 27.

### `inventory.get(inventory)` · `inventory.get(inventory, slot)`
With a slot, the item triple in it. Without one, the whole inventory as a list of triples, in slot
order.

Slots are counted from 0. Negative indices count from the end, so `-1` is the last slot.

```
inventory.get(player, 0);           // ['minecraft:stone', 64, {}]
inventory.get(chest_pos);           // the whole chest, 27 triples
```

### `inventory.set(inventory, slot, count)` · `inventory.set(inventory, slot, count, item)` · `inventory.set(inventory, slot, count, item, nbt)`
Writes a slot and returns **the triple that was there before**, which makes swapping a slot's
contents a single call.

- with `count` alone, only the size of the existing stack changes;
- with an `item`, the slot is replaced by that item;
- with `nbt`, the new item carries that tag — a string in command form, or a map;
- a `count` of `0` empties the slot, whatever else was passed;
- a `count` of `null` leaves the stack size as it is.

A slot beyond the end of the inventory returns `null` and changes nothing.

```
inventory.set(player, 0, 0);                                    // empty slot 0
inventory.set(player, 0, 1, 'diamond_sword');
inventory.set(player, 0, 1, 'diamond_sword', '{Enchantments:[{id:"sharpness",lvl:5}]}');
```

### `inventory.find(inventory, item)` · `inventory.find(inventory, item, from_slot)`
The number of the first slot holding that item, or `null` when there is none. Passing `null` as the
item finds the first **empty** slot instead — which is how you ask "is there room?".

`from_slot` starts the search further along, so repeated calls walk every occurrence.

```
inventory.find(chest_pos, 'minecraft:coal');        // 3
inventory.find(chest_pos, null);                    // the first free slot, or null when full
```

### `inventory.remove(inventory, item)` · `inventory.remove(inventory, item, count)`
Takes items out wherever they are in the inventory, across stacks. `count` defaults to 1. Returns
`true` when it removed them, and `false` when there were not enough — in which case **nothing** is
removed, so a partial payment cannot happen.

```
if(inventory.remove(player, 'minecraft:emerald', 3), give_reward(player));
```

### `inventory.has(inventory)`
Whether the inventory holds anything at all. It is the emptiness check, not a test for particular
items — for that use `find` or `remove`.

### `inventory.drop(inventory, slot)` · `inventory.drop(inventory, slot, count)`
Throws the contents of a slot on the ground as an item entity, as a player pressing Q would.
Without a count the whole stack is dropped. Returns how many items ended up on the floor.

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
| `item.inventory_has_items(i, ...)` | `inventory.has(i)` |
| `item.drop_item(i, s, n?)` | `inventory.drop(i, s, n?)` |
