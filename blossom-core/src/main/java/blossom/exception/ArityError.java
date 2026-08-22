package blossom.exception;

import org.jspecify.annotations.Nullable;

import blossom.interpreter.Expression;
import blossom.interpreter.Token;

/** Wrong number of arguments in a call. */
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

    /** The arity error of a specific call, with the call's position. */
    public static ArityError at(Expression expression, Token token, String message)
    {
        return new ArityError(positionOf(expression, token), message);
    }
}
