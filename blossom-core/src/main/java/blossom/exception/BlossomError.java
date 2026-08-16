package blossom.exception;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * La radice degli errori tipizzati di Blossom (PIANO_LINGUAGGIO.md §4).
 *
 * <p>Un errore porta con sé quattro cose: la categoria (che è il tipo java, così è distinguibile
 * anche da un {@code catch} mirato scritto in Blossom), la posizione nel sorgente, uno stack di
 * chiamate che attraversa i moduli, e - quando ha senso - un suggerimento.
 *
 * <p>Estende {@code InternalExpressionException} perché un errore tipizzato resta catturabile
 * ovunque lo fosse un errore generico - a partire dal {@code try} scritto in Blossom - senza che
 * ogni punto di cattura debba essere aggiornato.
 *
 * <p>Lo stack si costruisce risalendo: chi cattura l'errore mentre esce da una funzione aggiunge il
 * proprio frame con {@link #pushFrame}. È il contrario di uno stack trace java preso al momento del
 * lancio, e serve perché il frame più interno da solo non dice mai abbastanza: {@code add} è fallita
 * *chiamata da dove*?
 */
public abstract class BlossomError extends InternalExpressionException
{
    /**
     * La posizione di un token dentro un'espressione, per gli errori che nascono durante la
     * valutazione ma sanno esattamente su quale pezzo di codice sono inciampati.
     */
    public static SourcePosition positionOf(blossom.interpreter.Expression expression, blossom.interpreter.Token token)
    {
        return new SourcePosition(expression.getModuleName() == null ? "<code>" : expression.getModuleName(),
                token.lineno + 1, token.linepos + 1);
    }

    /** Un anello della catena di chiamate: quale funzione, in che punto del sorgente. */
    public record Frame(String function, SourcePosition position)
    {
    }

    private final SourcePosition position;
    private final @Nullable String suggestion;
    private final List<Frame> frames = new ArrayList<>();

    protected BlossomError(SourcePosition position, String message)
    {
        this(position, message, null);
    }

    protected BlossomError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(message);
        this.position = position;
        this.suggestion = suggestion;
    }

    /**
     * Il nome della categoria mostrato all'utente: per default il nome semplice della classe, che è
     * esattamente quello che il piano elenca ({@code ParseError}, {@code ImportError}, ...).
     */
    public String kind()
    {
        return getClass().getSimpleName();
    }

    public SourcePosition position()
    {
        return position;
    }

    public @Nullable String suggestion()
    {
        return suggestion;
    }

    public List<Frame> frames()
    {
        return List.copyOf(frames);
    }

    /** Aggiunge un livello di chiamata mentre l'errore risale. */
    public BlossomError pushFrame(String function, SourcePosition callSite)
    {
        frames.add(new Frame(function, callSite));
        return this;
    }

    /**
     * Il messaggio completo, nel formato del piano:
     *
     * <pre>
     * UndefinedSymbolError
     *   in math.vector.add  (src/math/vector.bs:4:12)
     *   called from main    (src/main.bs:8:1)
     *
     *   Simbolo 'ad' non trovato nel modulo 'math.vector'.
     *   Did you mean: 'add'?
     * </pre>
     */
    public String format()
    {
        StringBuilder out = new StringBuilder(kind());
        List<Frame> allFrames = frames();
        for (int i = 0; i < allFrames.size(); i++)
        {
            Frame frame = allFrames.get(i);
            out.append("\n  ").append(i == 0 ? "in " : "called from ")
               .append(frame.function()).append("  (").append(frame.position()).append(')');
        }
        if (allFrames.isEmpty() && position.isKnown())
        {
            out.append("\n  in ").append(position);
        }
        out.append("\n\n  ").append(getMessage());
        if (suggestion != null)
        {
            out.append("\n  Did you mean: ").append(suggestion).append('?');
        }
        return out.toString();
    }

    @Override
    public String toString()
    {
        return format();
    }

}
