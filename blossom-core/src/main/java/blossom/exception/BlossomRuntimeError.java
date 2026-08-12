package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * Tutto il resto: la categoria generica, con un sottocodice che dice di cosa si tratta davvero.
 *
 * <p>Il sottocodice esiste perché "RuntimeError" da solo non è distinguibile programmaticamente, che
 * è proprio il difetto del sistema a un solo errore da cui partiamo. Un errore che finisce qui e che
 * si ripete spesso è il segnale che gli serve una categoria propria.
 */
public class BlossomRuntimeError extends BlossomError
{
    private final String subcode;

    public BlossomRuntimeError(SourcePosition position, String subcode, String message)
    {
        this(position, subcode, message, null);
    }

    public BlossomRuntimeError(SourcePosition position, String subcode, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
        this.subcode = subcode;
    }

    public String subcode()
    {
        return subcode;
    }

    @Override
    public String kind()
    {
        return "RuntimeError[" + subcode + "]";
    }
}
