package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Module not found, import cycle, or access to a non-public symbol. */
public class ImportError extends BlossomError
{
    public ImportError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public ImportError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
