# `core.mc.text`

Parlare a chi gioca.

```
import "core.mc.text" as text;

text.print(player, 'ciao');
text.print(player, text.format('rb attenzione', 'w  qualcosa è andato storto'));
text.display_title(player, 'title', text.format('gb Livello 2'));
```

### `print(messaggio)` · `print(destinatario, messaggio)`
Scrive un messaggio: a chi ha lanciato lo script, o a un giocatore preciso. Restituisce il messaggio,
così si può infilare in mezzo a un'espressione senza spezzarla.

### `format(pezzi...)`
Testo formattato. Ogni pezzo è `'stile testo'`, dove lo stile è fatto di lettere:

| | | | |
| --- | --- | --- | --- |
| `w` bianco | `y` giallo | `m` magenta | `r` rosso |
| `c` ciano | `l` verde chiaro | `t` blu | `f` grigio scuro |
| `g` grigio | `d` oro | `p` viola | `n` rosso scuro |
| `q` ciano scuro | `e` verde scuro | `v` blu scuro | `k` nero |
| `i` corsivo | `b` grassetto | `u` sottolineato | `s` barrato |
| `o` offuscato | `#rrggbb` colore esatto | | |

Un pezzo che comincia con un simbolo attacca un'azione al pezzo precedente: `^` testo al passaggio
del mouse, `!` esegue un comando, `?` lo scrive in chat, `@` apre un indirizzo, `&` copia negli
appunti.

### `display_title(destinatari, tipo, testo?, dissolvenza?, durata?, uscita?)`
Scrive in grande sullo schermo. I tipi sono `'title'`, `'subtitle'`, `'actionbar'`, `'player_list_header'`,
`'player_list_footer'`, e `'clear'`/`'reset'` per togliere.

### `hud(giocatori, testo, durata?)` · `hud(giocatore)`
Una riga di testo sopra la hotbar.

```
text.hud(giocatore, 'minerali trovati: ' + trovati);           // resta finché non la cambi
text.hud(giocatore, 'zona instabile', 60);                     // resta 3 secondi, poi va via da sola
text.hud(entity.player('all'), text.format('y attenzione'));
text.hud(giocatore, null);                                     // via adesso
text.hud(giocatore);                                           // cosa sta vedendo, o null
```

`durata` è in tick. **Senza, la riga resta**: non c'è nessuna durata di default nascosta. Sono due
usi diversi e tutti e due normali — un contatore accompagna il giocatore, un avviso deve andarsene da
solo — e una durata implicita avrebbe rotto il primo. La scadenza viene controllata ad ogni tick,
quindi "resta 5 tick" resta 5 tick.

Una durata di zero o negativa è un errore, non "sparisce subito": chi la scrive quasi sempre ha
calcolato male un intervallo, e farla sparire in silenzio nasconderebbe il conto sbagliato. Per
togliere la riga c'è `hud(giocatore, null)`, che lo dice.

Accetta lo stesso testo di `format`, quindi colori e stili funzionano. Restituisce quanti giocatori
sono stati toccati.

**Come funziona, e cosa comporta.** Il gioco non ha nessun pacchetto che dica "tieni questo testo
lì": il titolo e la action bar hanno una durata e poi svaniscono. Questa funzione rimanda la action
bar prima che sfumi, così la riga resta — e siccome è tutto vanilla, **funziona su qualunque client**,
senza installare niente. Il testo parte subito quando lo cambi, e viene rimandato mezzo secondo alla
volta finché deve restare: un pacchetto ogni dieci tick per giocatore, non uno per tick.

Tre conseguenze, dichiarate:

- **la riga è una sola.** È lo spazio della action bar, e quello è.
- **condivide lo spazio con tutto il resto.** Il nome dell'oggetto che compare cambiando slot della
  hotbar, un `display_title(..., 'actionbar', ...)`, un altro mod: la riga della HUD li sovrascrive
  entro mezzo secondo. È quello che serve a una HUD, ma è bene saperlo.
- **la posizione non si sceglie.** Sopra la hotbar, punto.

Per qualcosa che quei limiti non li ha servirebbe del codice sul client, cioè una funzione che
smette di esistere per chi gioca vanilla. Vedi `DECISIONI.md` D-073.
