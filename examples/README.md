# Esempi

Tre progetti completi, in ordine di difficoltà. Si installano copiandoli sotto `<mondo>/blossom/` e
si caricano con `/blossom script load <nome>`.

| Progetto | File | Cosa mostra |
| --- | --- | --- |
| [`contapassi`](contapassi) | 1 | eventi di giocatore, HUD sopra la hotbar, stato salvato su disco, funzioni pubbliche per la console |
| [`torre-guardiana`](torre-guardiana) | 3 | più moduli, simboli privati, un evento annullabile, `try`/`catch` |
| [`vena-viva`](vena-viva) | 4 | task fuori dal tick, `task_dock`, sezione critica, evento fra app, logica separata dal mondo |

La guida che spiega i concetti sta in [`docs/guide/`](../docs/guide/README.md); il
riferimento delle funzioni in [`docs/api/`](../docs/api/Overview.md).

Il modulo di logica pura di `vena-viva` (`src/geologia.bs`) viene eseguito dalla suite di test del
core, in `GuideSnippetsTest`: è codice verificato, non pseudocodice.
