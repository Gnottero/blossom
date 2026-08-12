# torre-guardiana

Progetto d'esempio: quando qualcuno suona una campana, costruisce lì accanto una torre illuminata
alta quanto serve, e tiene il conto di quelle costruite.

Mostra tutto quello che un progetto Blossom usa davvero:

- **tre moduli** (`main`, `torre.costruzione`, `torre.registro`) con import per alias
- **simboli privati**: `colonna` e il materiale stanno dentro `torre.costruzione` e non escono
- **stato di modulo**: `torre.registro` tiene la lista delle torri in un `let mut` privato, e la
  espone solo attraverso funzioni
- **un evento annullabile**: l'handler di `bell_rings` restituisce `false`, quindi la campana suona
  comunque
- **`try`/`catch`**: se la costruzione fallisce, l'errore viene mostrato ai giocatori e l'evento non
  trascina giù nient'altro

## Provarlo

Copia la cartella `src/` e il file `blossom.json` in `<mondo>/blossom/torre-guardiana/`, poi in
gioco:

```
/blossom script load main
```

Piazza una campana, suonala, e guarda la torre.

```
/blossom script in main quante_torri()
/blossom script in main demolisci_tutte()
```
