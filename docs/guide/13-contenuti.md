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
