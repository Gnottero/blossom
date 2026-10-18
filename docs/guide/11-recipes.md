# 11. Recipes

Pieces of code for problems that keep coming back. Copy them and change them.

## Counting something and showing it to players

```
module main;

import "core.mc.event" as event;
import "core.mc.text" as text;
import "core.map" as map;

let mut scores = {};

fn on_break(player, block) -> (
    name = entity.query(player, 'name');
    scores:name = (scores:name || 0) + 1;
    text.hud(player, 'blocks broken: ' + scores:name);
    false
);

event.on('player_breaks_block', on_break);
```

`text.hud(player, text)` without a duration **stays** until you change it; with a duration in ticks
it disappears on its own. `text.hud(player, null)` removes it.

## State that survives a restart

```
import "core.io" as io;

let mut state = {};
let FILE = 'my-app/state.json';         // 'data' is a root that exists on every server

fn load() -> state = if(io.exists('data', FILE), io.read_json('data', FILE), {});
fn save() -> io.write_json('data', FILE, state);

load();                                 // in the module body: happens at load time
event.on('server_shuts_down', save);
```

Save periodically as well, not only on shutdown: a server that goes down badly calls no handler.

```
let mut tick = 0;
fn on_tick() -> (tick += 1; if(tick % 6000 == 0, save()));    // every 5 minutes
```

## An administrator command

There is no need to register commands: an app's `pub` functions can already be called from the
console.

```
pub fn leaderboard() -> (
    ordered = list.sort(map.pairs(scores), -_:1);
    list.map(ordered, _:0 + ': ' + _:1)
);
```

```
/blossom script in my-app leaderboard()
```

## Doing something in N seconds

```
import "core.mc.event" as event;

event.schedule(100, recheck, pos);      // 100 ticks = 5 seconds
```

For something periodic, a counter on `tick` (see above) is cheaper than rescheduling every time.

## Building without setting off redstone and sand

```
import "core.mc.block" as block;

block.without_updates(
    loop(height, block.set([x, y + _, z], 'stone_bricks'))
);
```

## Scanning a volume and counting

```
import "core.mc.scan" as scan;
import "core.mc.block" as block;

found = scan.scan(centre, [8, 4, 8], block.block(_) == 'diamond_ore');
```

`scan` returns **how many times the expression came out true**: counting is one line, with no
accumulators. Inside the body `_x`, `_y` and `_z` are the coordinates and `_` is the block.

## Long work without stopping the server

```
import "core.task" as task;

fn start(centre) -> task.start('work', centre);

fn work(centre) -> (
    data = task.dock(scan.rect(centre, 32));    // read in one stop
    plan = compute(data);                       // think off the tick
    task.dock(apply(plan))                      // write in one stop
);
```

## A default when a value is missing

```
name = configuration:'name' || 'unnamed';
```

`||` returns the first truthy operand, so it works as an "or else". Careful when `0` or `''` are
legitimate values: there the default would fire by mistake, and you need `map.has`.

## Searching a list

```
import "core.list" as list;

(l ~ x) != null;                    // is it in there?
l ~ x;                              // the index, null when absent
list.find(l, _:'name' == 'ada');    // the first one satisfying a condition
list.has(l, 3);                     // does index 3 exist? (not "does it contain 3")
```

Two traps in one line: `if(l ~ x, ...)` is wrong because if `x` is the first element the index is
`0`, which is false; and `list.has(l, x)` on a list asks whether the **index** exists, not whether
the value is in there. On a map, `map.has(m, key)` is exactly what it looks like.

## Grouping by key

```
fn group(items, key) -> (
    groups = {};
    for(items, (
        k = call(key, _);
        if(!map.has(groups, k), groups:k = []);
        list.put(groups:k, null, _)
    ));
    groups
);
```

## Handling an anticipated failure

```
import "core.json" as json;

fn read(text) -> try(
    json.decode(text),
    'json_error', (
        print('unreadable configuration, using the defaults');
        {}
    )
);
```

With the explicit filter: without it, `try` catches user exceptions only.

## Two apps talking to each other

```
// app "mine"
count = event.emit('vein_exhausted', null, pos, how_many);

// app "register"
event.declare('vein_exhausted', on_vein_exhausted);
```

`emit` returns `null` when nobody is listening: you can skip the work of preparing the data.

## Seeing what a script is computing

```
import "core.mc.effect" as effect;

effect.particle_box('happy_villager', corner_a, corner_b, 1);       // the working area
effect.particle_line('flame', from, to, 1);                         // a path
entity.spawn('armor_stand', pos, {'Invisible' -> 1, 'CustomName' -> '"target"',
        'CustomNameVisible' -> 1, 'NoGravity' -> 1});               // a point, until you remove it
```

With a Blossom client, [`core.mc.shape`](../api/core.mc.shape.md) draws the same things legibly;
without one, they fall back to particles.

## A module of constants only

```
module rules;

pub let MAX_HEIGHT = 32;
pub let MATERIALS = ['stone', 'stone_bricks', 'deepslate'];
pub let COLOURS = {'ok' -> 'l', 'warning' -> 'y', 'error' -> 'r'};
```

```
import "rules" as rules;
if(height > rules.MAX_HEIGHT, throw('too tall'));
```

## Testing a function without making the event happen

Keep the logic out of the handlers:

```
fn on_bell(pos, direction, who) -> (build(pos, 5); false);   // handler: thin
pub fn build(pos, height) -> ...;                            // logic: public
```

```
/blossom script in my-app build([100, 64, 100], 5)
```
