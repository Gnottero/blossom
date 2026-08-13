# `core.mc.item`

Inventories, items and recipes.

```
import "core.mc.item" as item;

item.inventory_size(player);                    // how many slots
item.inventory_get(player, 0);                  // ['minecraft:stone', 64, {}]
item.inventory_set(player, 0, 1, 'diamond');
```

An item is a triple `[name, count, nbt]`. An inventory is named with the entity carrying it or with
the position of the block containing it (chest, furnace, hopper...).

### `inventory_size(inventory)`
How many slots it has, `null` when there is no inventory there.

### `inventory_get(inventory, slot?)`
The content of a slot, or the whole inventory when the slot is not given.

### `inventory_set(inventory, slot, count, item?, nbt?)`
Writes a slot. A count of `0` empties it.

### `inventory_find(inventory, item, from_slot?)`
The first slot containing that item, `null` when there is none.

### `inventory_remove(inventory, item, count?)`
Takes items out of the inventory, wherever they are.

### `inventory_has_items(inventory, items...)`
Whether the inventory holds everything needed.

### `drop_item(inventory, slot, count?)`
Throws the content of a slot on the ground.

### `item_list(tag?)` · `item_tags(item, tag?)` · `item_category(item)`
The registered items, the tags of an item, and its category in the creative menu.

### `stack_limit(item)` · `crafting_remaining_item(item)`
How high it stacks, and what is left after using it in a recipe (the empty bucket, say).

### `item_display_name(item)`
The name shown in game, translated.

### `recipe_data(item, type?)`
The recipes producing that item: ingredients, result and type.

## Data components

Since 1.20.5 an item is no longer "id plus a block of free-form NBT": it is an id plus a map of
**typed components**, each with its own codec. Reading that data through the raw NBT still works for
what is in the NBT, but it does not say which components exist, does not tell an absent value from
one equal to the default, and validates nothing when writing.

```
import "core.mc.item" as item;

item.item_components(item_value);                       // {'minecraft:max_stack_size' -> 64, ...}
item.item_component(item_value, 'minecraft:damage');    // the value, or null
item.set_item_component(item_value, 'minecraft:custom_name', '{"text":"Sword"}');
item.set_item_component(item_value, 'minecraft:damage', null);   // removes it
```

### `item_components(item, default?)`
Every component, as a map from id to value. With `default` true it reads the ones the **item has of
its own** instead of the ones this stack carries: it is the comparison between the two that says
whether a value was set on purpose or is the one every item of that kind has.

### `item_component(item, name, default?)`
A single component, or `null` when that stack does not have it.

### `set_item_component(item, name, value)`
The item with that component changed; with `value` at `null` it removes it. It returns a copy and
does not touch the original — an item read from an inventory is already a copy, and pretending that
writing into it changes the world would be worse than not being able to.

A value that makes no sense for that component is **refused right away**, by the codec:

```
TypeMismatchError
  The given value is not valid for component 'minecraft:damage': Not a number
```

That is why these go through typed components and not through NBT: the alternative is a broken item
discovered much later, when somebody tries to use it.

### `component_list()`
The ids of every component this game knows.
