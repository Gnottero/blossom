package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Uso di un valore nullo dove ne serviva uno vero. */
public class NullReferenceError extends BlossomError
{
    public NullReferenceError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public NullReferenceError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
