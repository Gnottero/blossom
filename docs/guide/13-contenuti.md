# 13. Il contenuto di gioco

Un pack Blossom non porta solo comportamento: puo' portare anche **contenuto**, cioe' quello che
finora si scriveva in un datapack a parte — ricette, loot table, tag, advancement, e tutto il resto.

## La cartella `data/`

Accanto a `src/`, un pack puo' avere una cartella `data/` con **l'albero di un datapack vero**:

```
mio-pack/
├── blossom.json
├── src/
│   └── main.bs
└── data/
    ├── pack.mcmeta
    └── mio/
        ├── recipe/spada.json
        ├── loot_table/vena.json
        ├── tags/block/tenero.json
        └── advancement/inizio.json
```

E' la struttura che chi ha gia' scritto un datapack conosce, senza cambiare niente: gli stessi
percorsi, gli stessi json, gli stessi codec del gioco che li validano. Quello che cambia e' che **non
c'e' un datapack da installare a parte**: chi riceve il pack lo copia in `<mondo>/blossom/` e basta,
come per il codice.

Il contenuto entra prima che il gioco costruisca i propri registri, quindi funziona **tutto**:
ricette, loot table, tag e advancement, che si ricaricano con `/reload`; e anche gli incantesimi, i
tipi di danno, il worldgen e le dimensioni, che il gioco fissa una volta all'apertura del mondo.

## Generare invece di ripetere

Scrivere a mano quaranta ricette quasi identiche e' il motivo per cui conviene avere un linguaggio.
Un'app che espone una funzione `generate()` la puo' far scrivere al codice:

```
module main;

import "core.mc.data" as data;
import "core.list" as list;
import "core.map" as map;

// il materiale, e come si chiama la sua spada
let METALLI = {'iron' -> 'iron', 'gold' -> 'golden', 'copper' -> 'copper'};

pub fn generate() -> (
    list.map(map.keys(METALLI), fn(m) -> data.write("mio:recipe/spada_${m}", {
        'type' -> 'minecraft:crafting_shaped',
        'pattern' -> [' # ', ' # ', ' / '],
        'key' -> {'#' -> "minecraft:${m}_ingot", '/' -> 'minecraft:stick'},
        'result' -> {'id' -> "minecraft:${METALLI:m}_sword", 'count' -> 1}
    }));

    data.write('mio:tags/item/metalli', {
        'values' -> list.map(map.keys(METALLI), fn(m) -> "minecraft:${m}_ingot")
    })
);
```

```
/blossom data generate mio-pack
5 resources written in data/. Run /reload to apply them; a new registry entry or worldgen needs a restart.
```

Quello che ne esce sono **json veri**, che restano sul disco e si guardano con un editor o con
`git diff`. Il comando scrive anche `pack.mcmeta`.

`data.write(id, valore)` prende un id come quello dei comandi — `spazio:cartella/nome` — e decide lui
il percorso. Per i file che json non sono, come una `.mcfunction`, c'e' `data.write_text`.

**Su comando e non all'apertura del mondo**, perche' cosi' il generatore e' codice normale, con il
server acceso e l'API tutta disponibile, e un generatore lento o rotto non ritarda l'apertura di un
mondo.

## Generato e scritto a mano, insieme

`data/.blossom-generated` elenca cosa hanno prodotto i generatori. Rigenerare riscrive e ripulisce
**solo quello**: un json scritto a mano nella stessa cartella non viene mai toccato. Si puo' quindi
incollare un datapack esistente dentro `data/` e generare accanto, o partire a mano e passare al
generatore un pezzo alla volta.

## Quando qualcosa e' sbagliato

I json li validano i codec del gioco, come per un datapack qualunque, e i messaggi sono i loro:

```
Failed to parse mio:spada_gold from pack blossom/mio-pack
Caused by: Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:gold_sword
```

Vale la pena saperlo: un contenuto che non si legge **impedisce al mondo di aprirsi**, esattamente
come farebbe un datapack rotto. E' il prezzo di passare dalla strada vera invece che da una nostra.

## Come si vede

Per il gioco e' un datapack come un altro, e si vede:

```
/datapack list
There are 2 data pack(s) enabled: [vanilla (built-in)], [blossom/mio-pack (built-in)]
```

## Chi vince

Un pack Blossom sta **in fondo** alla lista, sotto i datapack veri di `<mondo>/datapacks/`. E' voluto:
chi amministra un server deve poter correggere una ricetta rotta di un'app di cui non ha il sorgente,
e sovrapporre un datapack e' l'unica via che gli resta.

Non si puo' invece **spegnere**: il codice di un'app da' per esistente il proprio contenuto, e un
`/datapack disable` lo lascerebbe a meta'. Chi non vuole il contenuto toglie il pack.

## `pack.mcmeta`

Serve, ed e' quello di sempre. E' anche cio' che rende `data/` un datapack valido di per se': si puo'
zippare e darlo a chi il mod non ce l'ha.
