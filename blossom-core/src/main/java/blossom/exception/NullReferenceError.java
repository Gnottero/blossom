package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Use of a null value where a real one was required. */
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
