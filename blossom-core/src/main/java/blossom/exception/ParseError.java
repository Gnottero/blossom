package blossom.exception;

import org.jspecify.annotations.Nullable;

import blossom.interpreter.Expression;
import blossom.interpreter.Token;

/** Syntax error, detected when the module is loaded. */
public class ParseError extends BlossomError
{
    public ParseError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public ParseError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }

    /**
     * The syntax error for a specific token, with the surrounding code snippet.
     *
     * <p>The snippet is the same thing the old {@code ExpressionException} already knew how to
     * produce (it's the most useful part of the message), but here it arrives inside a typed error
     * with a position, as every error must be.
     */
    public static ParseError at(Expression expression, Token token, String message)
    {
        SourcePosition position = new SourcePosition(
                expression.getModuleName() == null ? "<code>" : expression.getModuleName(),
                token.lineno + 1, token.linepos + 1);
        String snippet = String.join("\n", expression.getExpressionSnippet(token));
        return new ParseError(position, snippet.isEmpty() ? message : message + "\n\n" + snippet);
    }
}
