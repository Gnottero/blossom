package blossom.exception;

import org.jspecify.annotations.Nullable;

/** A value of a different type than the operation requires. */
public class TypeMismatchError extends BlossomError
{
    public TypeMismatchError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public TypeMismatchError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
