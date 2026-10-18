# 13. Game content

A Blossom pack does not carry behaviour only: it can carry **content** as well, meaning everything
that until now went into a separate datapack: recipes, loot tables, tags, advancements, and all the
rest.

## The `data/` directory

Next to `src/`, a pack may have a `data/` directory holding **a real datapack tree**:

```
my-pack/
├── blossom.json
├── src/
│   └── main.bs
└── data/
    ├── pack.mcmeta
    └── mine/
        ├── recipe/sword.json
        ├── loot_table/vein.json
        ├── tags/block/soft.json
        └── advancement/start.json
```

It is the structure anyone who has written a datapack already knows, unchanged: the same paths, the
same json, the same game codecs validating them. What changes is that **there is no separate
datapack to install**: whoever receives the pack copies it into `<world>/blossom/` and that is all,
exactly as for the code.

The content is registered before the game builds its own registries, so **everything** works:
recipes, loot tables, tags and advancements, which reload with `/reload`; and also enchantments,
damage types, worldgen and dimensions, which the game fixes once when the world is opened.

## Generating instead of repeating

Writing forty nearly identical recipes by hand is the reason it pays to have a language. An app that
exposes a `generate()` function can have the code write them:

```
module main;

import "core.mc.data" as data;
import "core.list" as list;
import "core.map" as map;

// the material, and what its sword is called
let METALS = {'iron' -> 'iron', 'gold' -> 'golden', 'copper' -> 'copper'};

pub fn generate() -> (
    list.map(map.keys(METALS), fn(m) -> data.write("mine:recipe/sword_${m}", {
        'type' -> 'minecraft:crafting_shaped',
        'pattern' -> [' # ', ' # ', ' / '],
        'key' -> {'#' -> "minecraft:${m}_ingot", '/' -> 'minecraft:stick'},
        'result' -> {'id' -> "minecraft:${METALS:m}_sword", 'count' -> 1}
    }));

    data.write('mine:tags/item/metals', {
        'values' -> list.map(map.keys(METALS), fn(m) -> "minecraft:${m}_ingot")
    })
);
```

```
/blossom data generate my-pack
5 resources written in data/. Run /reload to apply them; a new registry entry or worldgen needs a restart.
```

What comes out are **real json files**, which stay on disk and can be inspected with an editor or
with `git diff`. The command writes `pack.mcmeta` as well.

`data.write(id, value)` takes an id shaped like the ones in commands (`namespace:directory/name`)
and works out the path itself. For files that are not json, such as an `.mcfunction`, there is
`data.write_text`.

**On a command and not when the world opens**, because that way the generator is ordinary code, with
the server running and the whole API available, and a slow or broken generator does not delay the
opening of a world.

## Generated and hand-written, side by side

`data/.blossom-generated` lists what the generators produced. Regenerating rewrites and cleans up
**only that**: a json written by hand in the same directory is never touched. So you can paste an
existing datapack inside `data/` and generate alongside it, or start by hand and move one piece at a
time over to the generator.

## Looking and taking away

`/blossom data list <app>` says what is in a pack's `data/`, and who wrote it:

```
7 files in the data/ of 'demo', 6 of them generated:
  generated  data/demo/recipe/sword_copper.json
  by hand    data/demo/tags/block/hand_written.json
  generated  pack.mcmeta
```

The column is there for one thing: to see at a glance what the next generation will rewrite and what
will stay as it is.

`/blossom data export <app>` turns it into a zip in `<world>/blossom/.export/`:

```
The content of 'demo' is a datapack in blossom/.export/demo.zip: it works on a server without Blossom.
```

It is an ordinary datapack: it can be handed to someone who does not have the mod, or kept to check
that what the generator produced is what you expected. The list of generated files does not go
inside: that is ours, not the datapack's.

## When something is wrong

The json is validated by the game's codecs, as for any datapack, and the messages are theirs:

```
Failed to parse mine:sword_gold from pack blossom/my-pack
Caused by: Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:gold_sword
```

Worth knowing: content that cannot be read **prevents the world from opening**, exactly as a broken
datapack would. That is the price of going down the real road instead of one of our own.

## How it shows up

To the game it is a datapack like any other, and it shows:

```
/datapack list
There are 2 data pack(s) enabled: [vanilla (built-in)], [blossom/my-pack (built-in)]
```

## Who wins

A Blossom pack sits **at the bottom** of the list, below the real datapacks in `<world>/datapacks/`.
That is deliberate: whoever administers a server must be able to fix a broken recipe belonging to an
app whose source they do not have, and overlaying a datapack is the only route left to them.

What cannot be done is **disabling** it: an app's code assumes its own content exists, and a
`/datapack disable` would leave it half there. Whoever does not want the content removes the pack.

## `pack.mcmeta`

It is required, and it is the usual one. It is also what makes `data/` a valid datapack in its own
right: it can be zipped and handed to someone who does not have the mod.
