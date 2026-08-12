package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Riferimento a un simbolo che non esiste nel punto in cui viene usato. */
public class UndefinedSymbolError extends BlossomError
{
    public UndefinedSymbolError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public UndefinedSymbolError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
