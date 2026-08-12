# contapassi

Un'app in un file solo: conta salti, blocchi rotti e morti di ogni giocatore, li mostra sopra la
hotbar e li salva su disco.

È l'esempio da leggere per primo. Il fratello grande è [`vena-viva`](../vena-viva), che è multi-modulo
e usa i task.

## Provarla

```
cp -r examples/contapassi <mondo>/blossom/contapassi
```

```
/blossom script load contapassi
```

Salta, rompi qualcosa: la riga sopra la hotbar si aggiorna. Poi:

```
/blossom script in contapassi classifica('rotti')
/blossom script in contapassi azzera()
```

I dati finiscono in `<mondo>/blossom/contapassi.data/conteggi.json` e vengono riletti al caricamento.

## Cosa mostra

| Nel codice | Il concetto |
| --- | --- |
| `let mut conteggi = {}` | stato di modulo: le funzioni lo vedono, un'assegnazione nuda no ([guida §4](../../docs/guide/04-funzioni.md)) |
| `event.on(...)` nel corpo del modulo | gli handler si registrano al caricamento, e muoiono con l'app ([guida §8](../../docs/guide/08-eventi.md)) |
| `false` alla fine di ogni handler | un valore vero annullerebbe l'azione |
| `text.hud(giocatore, ...)` | una riga sopra la hotbar, che resta finché non la cambi |
| `file.read_file` / `write_file` | persistenza sotto `<mondo>/blossom/contapassi.data/` |
| salvataggio ogni 6000 tick | un server che va giù male non chiama `server_shuts_down` |
| `pub fn classifica(voce: str)` | l'interfaccia per la console, con un tipo sul parametro |
| `list.has(VOCI, voce)` invece di `VOCI ~ voce` | `~` restituisce l'indice, e l'indice `0` è falso |
