# `core.mc.screen`

Opening a screen for a player and reacting to their clicks.

```
import "core.mc.screen" as screen;

s = screen.open(player, 'generic_9x3', 'Shop', handle);

fn handle(scr, player, action, data) -> (
    if(action == 'slot_click' && data:'slot' == 0,
        true    // true = the click is not carried out
    )
);
```

### `open(player, type, title, callback?)`
Opens a screen. The types are the vanilla ones: `generic_9x1`..`generic_9x6`, `generic_3x3`,
`anvil`, `beacon`, `blast_furnace`, `brewing_stand`, `crafting`, `enchantment`, `furnace`,
`grindstone`, `hopper`, `lectern`, `loom`, `merchant`, `shulker_box`, `smithing`, `smoker`,
`cartography`, `stonecutter`.

The returned value is the screen: it is used as an inventory with the functions of `core.mc.item`.

The callback gets `(screen, player, action, data)` and the actions are `slot_click`, `button_click`,
`select_recipe`, `close`. Returning a true value cancels the action.

### `close(screen)`
Closes it. The player goes back to their own inventory.

### `property(screen, property)` · `property(screen, property, value)`
Reads or writes a property of the screen (the level of an enchantment, the progress of a furnace, the
fuel...).
