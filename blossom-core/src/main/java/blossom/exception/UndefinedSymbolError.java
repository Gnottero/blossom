package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Reference to a symbol that doesn't exist at the point where it's used. */
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
