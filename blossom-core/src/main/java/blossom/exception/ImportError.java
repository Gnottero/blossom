package blossom.exception;

import org.jspecify.annotations.Nullable;

/** Modulo non trovato, ciclo di import, oppure accesso a un simbolo non pubblico. */
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
