# vena-viva

Quando qualcuno rompe un minerale, l'app guarda quanto ne resta lì attorno, glielo dice sopra la
hotbar, disegna il riquadro della vena, lo annota su disco, e quando la vena finisce avvisa le altre
app con un evento.

È l'esempio grande: quattro moduli, un task, una sezione critica, un evento fra app e un `try`. Il
fratello piccolo è [`contapassi`](../contapassi), che sta in un file solo.

## Provarla

```
cp -r examples/vena-viva <mondo>/blossom/vena-viva
```

```
/blossom script load vena-viva
```

Scendi in miniera e rompi un minerale. Poi:

```
/blossom script in vena-viva riepilogo()
/blossom script in vena-viva ultime(5)
/blossom script in vena-viva azzera()
```

Il registro sta in `<mondo>/blossom/data/vena-viva/registro.json`.

Per sentire l'evento da un'altra app:

```
event.handle_event('vena_esaurita', fn_tua);        // fn_tua(giocatore, tipo, pos, nome)
```

## Come è divisa

```
src/
├── main.bs        gli handler, il task, l'interfaccia per la console
├── geologia.bs    la logica: nessun accesso al mondo, nessun evento, nessuna stampa
├── cantiere.bs    tutto quello che tocca il mondo: blocchi, particelle, suoni, HUD
└── registro.bs    lo stato e il disco, con la sezione critica
```

Il taglio non è estetico: `geologia` si prova senza far succedere niente in gioco,

```
/blossom script in vena-viva geologia.conta([[[0, 0, 0], 'diamond_ore']], 'diamond_ore')
```

e `main` resta abbastanza sottile da leggersi tutto insieme.

## Cosa mostra

| Nel codice | Il concetto |
| --- | --- |
| `import "geologia" as geologia;` | alias obbligatorio, un modulo per file ([guida §6](../../docs/guide/06-moduli.md)) |
| `fn estremo(...)` in `geologia` | privata: senza `pub` non esce dal modulo, e usarla da fuori è un errore a caricamento |
| `task.task('ispeziona', ...)` nell'handler | il lavoro lungo esce dal tick ([guida §9](../../docs/guide/09-concorrenza.md)) |
| due soli `task_dock` | leggi in blocco, pensa fuori, scrivi in blocco |
| `task.synchronize(LOCK, ...)` in `registro` | una sequenza leggi-modifica-scrivi chiamata da un altro thread |
| `try(..., 'exception', ...)` attorno al task | un task che esplode non deve sparire in silenzio |
| `motivo = _;` dentro il `catch` | `_` cambia significato dentro un `for`: si copia subito |
| `event.signal_event('vena_esaurita', ...)` | due app che si parlano senza conoscersi ([guida §8](../../docs/guide/08-eventi.md)) |
| `return(false)` in cima all'handler | uscita anticipata quando il blocco non ci interessa |
| `pub fn conta(letture: list, tipo: str)` | tipi sull'interfaccia pubblica, non nel ciclo stretto |
| `"watchdog"` nel manifesto | limiti dichiarati dall'app ([guida §7](../../docs/guide/07-errori.md)) |
