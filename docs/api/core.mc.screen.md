# `core.mc.screen`

Aprire una schermata a un giocatore e reagire ai suoi click.

```
import "core.mc.screen" as screen;

s = screen.create_screen(player, 'generic_9x3', 'Negozio', gestisci);

fn gestisci(schermata, giocatore, azione, dati) -> (
    if(azione == 'slot_click' && dati:'slot' == 0,
        true    // vero = il click non viene eseguito
    )
);
```

### `create_screen(giocatore, tipo, titolo, callback?)`
Apre una schermata. I tipi sono quelli vanilla: `generic_9x1`..`generic_9x6`, `generic_3x3`,
`anvil`, `beacon`, `blast_furnace`, `brewing_stand`, `crafting`, `enchantment`, `furnace`,
`grindstone`, `hopper`, `lectern`, `loom`, `merchant`, `shulker_box`, `smithing`, `smoker`,
`cartography`, `stonecutter`.

Il valore restituito è la schermata: si usa come inventario con le funzioni di `core.mc.item`.

Il callback riceve `(schermata, giocatore, azione, dati)` e le azioni sono `slot_click`,
`button_click`, `select_recipe`, `close`. Restituire un valore vero annulla l'azione.

### `close_screen(schermata)`
La chiude. Il giocatore torna al proprio inventario.

### `screen_property(schermata, proprietà)` · `screen_property(schermata, proprietà, valore)`
Legge o scrive una proprietà della schermata (il livello di un incantesimo, il progresso di una
fornace, il carburante...).
