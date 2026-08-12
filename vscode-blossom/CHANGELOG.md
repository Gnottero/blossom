# Changelog

## 0.1.1

- lo schema di `blossom.json` accetta `autoload`, che il mod legge da sempre: prima un manifesto che
  lo dichiarava veniva segnato con "Property autoload is not allowed."
- `version` non e' piu' obbligatorio nel manifesto, come nel mod (assente vale `0.1.0`)
- i percorsi della documentazione seguono il repo nuovo: `docs/api/` invece di `docs/blossom/api/`

## 0.1.0

Prima versione.

- evidenziazione della sintassi (preambolo, `fn`/`let`/`pub`, tipi sui parametri, variabili
  implicite, stringhe, commenti)
- completamento di moduli, simboli di libreria, eventi, funzioni e binding del file
- hover con firma e descrizione, presi dalla documentazione del repo
- firma di chiamata, struttura del file, vai alla definizione (anche attraverso gli import)
- diagnostiche del preambolo e dell'uso dei simboli, con correzioni rapide
- schema JSON per `blossom.json`
- `tools/generate-api.js`: i dati dell'API si rigenerano dalle sorgenti del mod
