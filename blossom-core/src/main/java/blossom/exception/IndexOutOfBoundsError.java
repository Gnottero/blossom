package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Indice fuori dai limiti di una lista o di una stringa. */
public class IndexOutOfBoundsError extends BlossomError
{
    public IndexOutOfBoundsError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public IndexOutOfBoundsError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
