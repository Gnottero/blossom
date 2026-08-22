# `core.mc.item`

Items: their recipes, their tags and their data components. Inventories are a different thing and
live in [`core.mc.inventory`](core.mc.inventory.md).

```
import "core.mc.item" as item;

item.name(['minecraft:diamond_sword', 1, {}]);   // the name shown in game
item.tags('minecraft:stone');                    // the tags of an item
item.recipes('minecraft:stick');                 // what produces it
```

An item is a triple `[name, count, nbt]`.

### `item.catalog(tag?)` · `item.tags(item, tag?)`
The registered items, and the tags of an item.

### `item.max_stack(item)` · `item.crafting_remainder(item)`
How high it stacks, and what is left after using it in a recipe (the empty bucket, say).

### `item.name(item)`
The name shown in game, translated.

### `item.recipes(item, type?)`
The recipes producing that item: ingredients, result and type.

## Data components

Since 1.20.5 an item is no longer "id plus a block of free-form NBT": it is an id plus a map of
**typed components**, each with its own codec. Reading that data through the raw NBT still works for
what is in the NBT, but it does not say which components exist, does not tell an absent value from
one equal to the default, and validates nothing when writing.

```
item.components(item_value);                       // {'minecraft:max_stack_size' -> 64, ...}
item.component(item_value, 'minecraft:damage');    // the value, or null
item.set_component(item_value, 'minecraft:custom_name', '{"text":"Sword"}');
item.set_component(item_value, 'minecraft:damage', null);   // removes it
```

### `item.components(item, default?)`
Every component, as a map from id to value. With `default` true it reads the ones the **item has of
its own** instead of the ones this stack carries: it is the comparison between the two that says
whether a value was set on purpose or is the one every item of that kind has.

### `item.component(item, name, default?)`
A single component, or `null` when that stack does not have it.

### `item.set_component(item, name, value)`
The item with that component changed; with `value` at `null` it removes it. It returns a copy and
does not touch the original: an item read from an inventory is already a copy, and pretending that
writing into it changes the world would be worse than not being able to.

A value that makes no sense for that component is **refused right away**, by the codec:

```
TypeMismatchError
  The given value is not valid for component 'minecraft:damage': Not a number
```

That is why these go through typed components and not through NBT: the alternative is a broken item
discovered much later, when somebody tries to use it.

### `item.component_catalog()`
The ids of every component this game knows.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `stack_limit(i)` | `max_stack(i)` |
| `item_list(tag?)` | `catalog(tag?)` |
| `item_tags(i, t?)` | `tags(i, t?)` |
| `item_display_name(i)` | `name(i)` |
| `recipe_data(i, t?)` | `recipes(i, t?)` |
| `crafting_remaining_item(i)` | `crafting_remainder(i)` |
| `item_components(i, d?)` | `components(i, d?)` |
| `item_component(i, n, d?)` | `component(i, n, d?)` |
| `set_item_component(i, n, v)` | `set_component(i, n, v)` |
| `component_list()` | `component_catalog()` |
| the `inventory_*` family and `drop_item` | [`core.mc.inventory`](core.mc.inventory.md) |
