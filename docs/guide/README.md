# La guida a Blossom

L'[API](../api/Overview.md) dice *cosa c'è*. Questa guida dice *come si scrive*: la grammatica, le
regole di visibilità, cosa fa un errore quando arriva, e come si mette insieme un'app che gira su un
server vero.

Ogni frammento di codice qui dentro è stato eseguito. Quelli che non toccano Minecraft sono
verificati da `blossom-core/src/test/java/blossom/GuideSnippetsTest.java`, che gira insieme al resto
dei test: se il linguaggio cambia e la guida non viene aggiornata, la build si accorge.

## I capitoli

| | |
| --- | --- |
| [1. Primi passi](01-primi-passi.md) | installare, caricare un'app, i comandi |
| [2. Valori](02-valori.md) | i tipi, i letterali, verità e uguaglianza |
| [3. Operatori](03-operatori.md) | precedenze, assegnamento, accesso, `~`, `<>`, `...` |
| [4. Funzioni](04-funzioni.md) | `fn`, argomenti, `outer`, tipi, funzioni come valori |
| [5. Controllo di flusso](05-controllo.md) | `if`, cicli, `map`/`filter`/`reduce`, `break` |
| [6. Moduli e progetti](06-moduli.md) | `module`, `import`, `pub`, manifesto, dipendenze |
| [7. Errori](07-errori.md) | categorie, `try`, `throw`, cosa non si può catturare |
| [8. Eventi](08-eventi.md) | reagire al mondo, annullare, eventi fra app |
| [9. Concorrenza](09-concorrenza.md) | `task`, `synchronize`, `task_dock`, il watchdog |
| [10. Prestazioni](10-prestazioni.md) | cosa costa, come si misura, come si sistema |
| [11. Ricette](11-ricette.md) | pezzi di codice pronti, uno per problema |
| [12. Configurazione del server](12-configurazione.md) | `blossomscript.json`: registry, `io`, `http`, watchdog |

## Da dove partire

- non hai mai scritto uno script per Minecraft → [1](01-primi-passi.md), poi
  [11](11-ricette.md), copia e cambia
- hai già scritto script per Minecraft altrove → [6](06-moduli.md) e [7](07-errori.md) sono le due
  parti che probabilmente non ti aspetti, il resto della sintassi ti sarà familiare
- devi mettere in piedi un progetto grosso → [6](06-moduli.md), [9](09-concorrenza.md),
  [10](10-prestazioni.md)

## I progetti d'esempio

| Progetto | Cosa mostra |
| --- | --- |
| [`torre-guardiana`](../../../examples/torre-guardiana) | più moduli, simboli privati, un evento annullabile, `try` |
| [`contapassi`](../../../examples/contapassi) | un file solo: HUD, stato persistente, eventi di giocatore |
| [`vena-viva`](../../../examples/vena-viva) | quattro moduli, `scan`, task in background, evento fra app, comando |
- [13. Il contenuto di gioco](13-contenuti.md) — ricette, loot table, tag e advancement dentro il pack
