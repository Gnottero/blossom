package blossom.exception;

import org.jspecify.annotations.Nullable;

import blossom.interpreter.Expression;
import blossom.interpreter.Token;

/** Numero di argomenti sbagliato in una chiamata. */
public class ArityError extends BlossomError
{
    public ArityError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public ArityError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }

    /** L'errore di arietà di una chiamata precisa, con la posizione della chiamata. */
    public static ArityError at(Expression expression, Token token, String message)
    {
        return new ArityError(positionOf(expression, token), message);
    }
}
