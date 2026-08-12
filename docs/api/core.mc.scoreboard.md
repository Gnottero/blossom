# `core.mc.scoreboard`

Obiettivi, squadre e barre del boss.

```
import "core.mc.scoreboard" as score;

score.scoreboard_add('punti');
score.scoreboard('punti', player, 10);
score.scoreboard_display('sidebar', 'punti');
```

### `scoreboard()` · `scoreboard(obiettivo)` · `scoreboard(obiettivo, chi)` · `scoreboard(obiettivo, chi, valore)`
Gli obiettivi esistenti, i punteggi di un obiettivo, il punteggio di uno, o l'assegnazione di un
punteggio.

### `scoreboard_add(obiettivo, criterio?)` · `scoreboard_remove(obiettivo)` · `scoreboard_remove(obiettivo, chi)`
Crea un obiettivo, lo cancella, o toglie il punteggio di uno solo.

### `scoreboard_property(obiettivo, proprietà, valore?)`
Legge o scrive le proprietà di un obiettivo: `display_name`, `criterion`, `render_type`.

### `scoreboard_display(posto, obiettivo?)`
Mostra un obiettivo in un posto (`'sidebar'`, `'list'`, `'belowName'`, o un colore di squadra).

### `team_list()` · `team_list(squadra)`
Le squadre, o i membri di una squadra.

### `team_add(squadra)` · `team_add(squadra, chi)` · `team_remove(squadra)` · `team_leave(chi)`
Crea una squadra, ci aggiunge qualcuno, la scioglie, o fa uscire qualcuno da quella in cui sta.

### `team_property(squadra, proprietà, valore?)`
Legge o scrive le proprietà di una squadra: `collisionRule`, `color`, `displayName`, `friendlyFire`,
`nametagVisibility`, `deathMessageVisibility`, `prefix`, `suffix`, `seeFriendlyInvisibles`.

### `bossbar()` · `bossbar(nome)` · `bossbar(nome, proprietà, valore?)`
Le barre esistenti, la creazione di una barra, e le sue proprietà: `name`, `color`, `style`, `value`,
`max`, `visible`, `players`, `add_player`, `remove`.
