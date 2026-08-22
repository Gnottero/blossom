# `core.mc.data`

Writing the game content a pack carries: recipes, loot tables, tags, advancements, everything that
used to need a datapack of its own.

```
import "core.mc.data" as data;

data.write('my_pack:recipe/sword', {
    'type' -> 'minecraft:crafting_shaped',
    'pattern' -> [' # ', ' # ', ' / '],
    'key' -> {'#' -> 'minecraft:iron_ingot', '/' -> 'minecraft:stick'},
    'result' -> {'id' -> 'minecraft:iron_sword', 'count' -> 1}
});
```

Writing the json by hand inside `data/` works and is fine. This module is for **generating** it,
which is the reason to have a language instead of forty nearly identical files.

It only works while generating: `/blossom data generate <app>` calls the app's `generate()` with this
module live. Outside of that there is no right moment to write, and the error says so.

### `data.write(id, value)`
A json resource. The id is the one commands use, `namespace:folder/name`, and the path follows from
it: whoever writes does not have to know the shape of `data/`. Returns the relative path written.

### `data.write_text(id, lines)`
A resource that is not json, a `.mcfunction`, say. The id carries the extension, because nothing
else says what kind of file it is. `lines` is a list of lines or a single string.

## What the command does around it

The generation also writes `pack.mcmeta`, and keeps `data/.blossom-generated`: the list of what the
generators produced. Regenerating rewrites and cleans up **only those**, so a json written by hand in
the same folder is never touched.

See [13. Game content](../guide/13-contenuti.md).
