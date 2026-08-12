package blossom.exception;

import org.jspecify.annotations.Nullable;

import blossom.interpreter.Expression;
import blossom.interpreter.Token;

/** Errore di sintassi, rilevato al caricamento del modulo. */
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
     * L'errore di sintassi di un token preciso, con il frammento di codice intorno.
     *
     * <p>Il frammento e' quello che il vecchio {@code ExpressionException} sapeva gia' produrre - e'
     * la parte piu' utile del messaggio - ma qui arriva dentro un errore tipizzato e con posizione,
     * come chiede il §4 del piano.
     */
    public static ParseError at(Expression expression, Token token, String message)
    {
        SourcePosition position = new SourcePosition(
                expression.getModuleName() == null ? "<code>" : expression.getModuleName(),
                token.lineno + 1, token.linepos + 1);
        String snippet = String.join("\n", expression.getExpressionSnippet(token));
        return new ParseError(position, snippet.isEmpty() ? message : message + "\n\n  " + snippet);
    }
}
