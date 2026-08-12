package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Un valore di tipo diverso da quello che l'operazione richiede. */
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
