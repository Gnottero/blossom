package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Index out of bounds for a list or a string. */
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
