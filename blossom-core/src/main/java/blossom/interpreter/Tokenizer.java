package blossom.interpreter;

import blossom.exception.ExpressionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expression tokenizer that allows to iterate over a {@link String}
 * expression token by token. Blank characters will be skipped.
 */
public class Tokenizer
{
    /**
     * What character to use for decimal separators.
     */
    private static final char decimalSeparator = '.';
    /**
     * What character to use for minus sign (negative values).
     */
    private static final char minusSign = '-';
    /**
     * Actual position in expression string.
     */
    private int pos = 0;
    private int lineno = 0;
    private int linepos = 0;
    private final boolean comments;
    private final boolean newLinesMarkers;
    /**
     * The original input expression.
     */
    private final String input;
    /**
     * The previous token or <code>null</code> if none.
     */
    private Token previousToken;

    private final Expression expression;
    private final Context context;

    Tokenizer(Context c, Expression expr, String input, boolean allowComments, boolean allowNewLineMakers)
    {
        this.input = input;
        this.expression = expr;
        this.context = c;
        this.comments = allowComments;
        this.newLinesMarkers = allowNewLineMakers;
    }

    public static Tokenizer simple(String input)
    {
        return new Tokenizer(null, null, input, false, false);
    }

    public static List<Token> postProcess(List<Token> originalTokens)
    {
        List<Token> cleanedTokens = new ArrayList<>();
        Token last = null;
        for (int i = originalTokens.size() - 1; i >= 0; i--)
        {
            Token current = originalTokens.get(i);
            if (current.type == Token.TokenType.MARKER && current.surface.startsWith("//"))
            {
                continue;
            }
            // skipping comments
            if (!isSemicolon(current)
                    || (last != null && last.type != Token.TokenType.CLOSE_PAREN && last.type != Token.TokenType.COMMA && !isSemicolon(last)))
            {
                if (isSemicolon(current))
                {
                    current.surface = ";";
                    current.type = Token.TokenType.OPERATOR;
                }
                if (current.type == Token.TokenType.MARKER)
                {
                    // dealing with tokens in reversed order
                    if ("{".equals(current.surface))
                    {
                        cleanedTokens.add(current.morphedInto(Token.TokenType.OPEN_PAREN, "("));
                        current.morph(Token.TokenType.FUNCTION, "m");
                    }
                    else if ("[".equals(current.surface))
                    {
                        cleanedTokens.add(current.morphedInto(Token.TokenType.OPEN_PAREN, "("));
                        current.morph(Token.TokenType.FUNCTION, "l");
                    }
                    else if ("}".equals(current.surface) || "]".equals(current.surface))
                    {
                        current.morph(Token.TokenType.CLOSE_PAREN, ")");
                    }
                }
                cleanedTokens.add(current);
            }
            if (!(current.type == Token.TokenType.MARKER && current.surface.equals("$")))
            {
                last = current;
            }
        }
        Collections.reverse(cleanedTokens);
        return cleanedTokens;
    }

    public List<Token> parseTokens()
    {
        List<Token> tokens = new ArrayList<>();
        while (hasNext())
        {
            Token token = next();
            if (token != null && token.type == Token.TokenType.INTERPOLATION)
            {
                expandInterpolation(token, tokens);
            }
            else
            {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Spezza una stringa interpolata nei pezzi che la compongono.
     *
     * <p>{@code "n=${a + 1}!"} diventa {@code concat('n=', (a + 1), '!')}. Succede qui, mentre si
     * legge il sorgente: a runtime non c'e' nessuna stringa da riesaminare e nessun segnaposto da
     * cercare, solo una concatenazione gia' scritta. Una stringa senza segnaposti resta una
     * costante e non costa niente piu' di quanto costava prima.
     */
    private static void expandInterpolation(Token token, List<Token> out)
    {
        String raw = token.surface;
        // ogni elemento e' un argomento di concat, gia' in token
        List<List<Token>> arguments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < raw.length())
        {
            if (raw.charAt(i) == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '$')
            {
                literal.append('$'); // '\\${' written by hand: a literal '${', not a placeholder
                i += 2;
                continue;
            }
            if (raw.charAt(i) == '$' && i + 1 < raw.length() && raw.charAt(i + 1) == '{')
            {
                if (!literal.isEmpty())
                {
                    arguments.add(List.of(stringPiece(token, literal.toString())));
                    literal.setLength(0);
                }
                int close = endOfPlaceholder(raw, i + 2);
                arguments.add(placeholderTokens(token, raw.substring(i + 2, close)));
                i = close + 1;
                continue;
            }
            literal.append(raw.charAt(i));
            i++;
        }
        if (!literal.isEmpty() || arguments.isEmpty())
        {
            arguments.add(List.of(stringPiece(token, literal.toString())));
        }

        if (arguments.size() == 1 && arguments.get(0).size() == 1
                && arguments.get(0).get(0).type == Token.TokenType.STRINGPARAM)
        {
            out.add(arguments.get(0).get(0));
            return;
        }

        out.add(token.morphedInto(Token.TokenType.FUNCTION, "concat"));
        out.add(token.morphedInto(Token.TokenType.OPEN_PAREN, "("));
        for (int a = 0; a < arguments.size(); a++)
        {
            if (a > 0)
            {
                out.add(token.morphedInto(Token.TokenType.COMMA, ","));
            }
            out.addAll(arguments.get(a));
        }
        out.add(token.morphedInto(Token.TokenType.CLOSE_PAREN, ")"));
    }

    /** L'indice della graffa che chiude un {@code ${...}} aperto a {@code from}. */
    private static int endOfPlaceholder(String raw, int from)
    {
        int depth = 1;
        boolean inQuote = false;
        int j = from;
        while (j < raw.length() && depth > 0)
        {
            char c = raw.charAt(j);
            if (c == '\'')
            {
                inQuote = !inQuote;
            }
            else if (!inQuote && c == '{')
            {
                depth++;
            }
            else if (!inQuote && c == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return j;
                }
            }
            j++;
        }
        return raw.length();
    }

    /** I token di una sotto-espressione, fra parentesi perche' non si mescoli con i pezzi accanto. */
    private static List<Token> placeholderTokens(Token token, String source)
    {
        List<Token> tokens = new ArrayList<>();
        tokens.add(token.morphedInto(Token.TokenType.OPEN_PAREN, "("));
        for (Token sub : Tokenizer.simple(source).parseTokens())
        {
            // la posizione resta quella della stringa: e' il punto che chi legge un errore deve
            // andare a guardare, e la sotto-espressione non ha una riga sua
            sub.lineno = token.lineno;
            sub.linepos = token.linepos;
            sub.pos = token.pos;
            tokens.add(sub);
        }
        tokens.add(token.morphedInto(Token.TokenType.CLOSE_PAREN, ")"));
        return tokens;
    }

    private static Token stringPiece(Token token, String text)
    {
        Token piece = token.morphedInto(Token.TokenType.STRINGPARAM, text);
        piece.disguiseAs('\'' + text + '\'', null);
        return piece;
    }

    public boolean hasNext()
    {
        // whitespace at the end is not a token: saying "yes" here and then handing back null is what
        // made a placeholder body like "${1 }" blow up
        int ahead = pos;
        while (ahead < input.length() && Character.isWhitespace(input.charAt(ahead)))
        {
            ahead++;
        }
        return ahead < input.length();
    }

    /**
     * Peek at the next character, without advancing the iterator.
     *
     * @return The next character or character 0, if at end of string.
     */
    private char peekNextChar()
    {
        return (pos < (input.length() - 1)) ? input.charAt(pos + 1) : 0;
    }

    private boolean isHexDigit(char ch)
    {
        return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    /**
     * Un punto dentro un identificatore lo qualifica: {@code vec.add} e' un simbolo solo, non
     * {@code vec} seguito da un operatore.
     *
     * <p>Vale solo se subito dopo comincia un altro identificatore, cosi' il punto decimale dei
     * numeri e ogni altro uso del punto restano quello che erano.
     */
    private boolean isQualifierDot(char ch, int position)
    {
        if (ch != '.' || position + 1 >= input.length())
        {
            return false;
        }
        char next = input.charAt(position + 1);
        return Character.isLetter(next) || next == '_';
    }

    /**
     * Trasforma le parole chiave delle dichiarazioni in operatori prefissi.
     *
     * <p>{@code fn}, {@code let}, {@code let mut} e {@code pub} si scrivono come identificatori ma
     * sono grammatica: senza questo passaggio il parser vedrebbe due simboli affiancati e non
     * saprebbe cosa farne. Sono quindi nomi riservati, e usarli come variabile e' un errore di
     * sintassi - che e' esattamente quello che ci si aspetta da una parola chiave.
     */
    private void morphKeyword(Token token)
    {
        if (token.type != Token.TokenType.VARIABLE)
        {
            return;
        }
        String surface = token.surface;
        if (surface.equals("let") && consumeFollowingWord("mut"))
        {
            surface = "let_mut";
        }
        else if (!blossom.language.Modules.KEYWORDS.contains(surface))
        {
            return;
        }
        if (surface.equals("mut"))
        {
            return; // 'mut' da solo non e' un operatore: esiste solo attaccato a 'let'
        }
        token.disguiseAs(surface, null);
        token.surface = surface + "u";
        token.type = Token.TokenType.UNARY_OPERATOR;
    }

    /**
     * Consuma {@code word} se e' la prossima parola nell'input, spazi a parte.
     *
     * @return se e' stata consumata
     */
    private boolean consumeFollowingWord(String word)
    {
        int lookahead = pos;
        int lookaheadLine = lineno;
        int lookaheadColumn = linepos;
        while (lookahead < input.length() && Character.isWhitespace(input.charAt(lookahead)))
        {
            if (input.charAt(lookahead) == '\n')
            {
                lookaheadLine++;
                lookaheadColumn = 0;
            }
            else
            {
                lookaheadColumn++;
            }
            lookahead++;
        }
        if (!input.startsWith(word, lookahead))
        {
            return false;
        }
        int after = lookahead + word.length();
        if (after < input.length() && (Character.isLetterOrDigit(input.charAt(after)) || input.charAt(after) == '_'))
        {
            return false;
        }
        pos = after;
        lineno = lookaheadLine;
        linepos = lookaheadColumn + word.length();
        return true;
    }

    private static boolean isSemicolon(Token tok)
    {
        return (tok.type == Token.TokenType.OPERATOR && tok.surface.equals(";"))
                || (tok.type == Token.TokenType.UNARY_OPERATOR && tok.surface.equals(";u"));
    }

    public Token next()
    {
        Token token = new Token();

        if (pos >= input.length())
        {
            return previousToken = null;
        }
        char ch = input.charAt(pos);
        while (Character.isWhitespace(ch))
        {
            linepos++;
            if (ch == '\n')
            {
                lineno++;
                linepos = 0;
            }
            // the bound is checked before reading, not after: an input that ends in whitespace
            // (a placeholder body like "${1 }") must end the scan, not walk off the string
            if (++pos >= input.length())
            {
                return previousToken = null;
            }
            ch = input.charAt(pos);
        }
        token.pos = pos;
        token.lineno = lineno;
        token.linepos = linepos;

        boolean isHex = false;

        if (Character.isDigit(ch)) // || (ch == decimalSeparator && Character.isDigit(peekNextChar())))
        // decided to no support this notation to favour element access via . operator
        {
            if (ch == '0' && (peekNextChar() == 'x' || peekNextChar() == 'X'))
            {
                isHex = true;
            }
            while ((isHex
                    && isHexDigit(
                    ch))
                    || (Character.isDigit(ch) || ch == decimalSeparator || ch == 'e' || ch == 'E'
                    || (ch == minusSign && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1)))
                    || (ch == '+' && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1))))
                    && (pos < input.length()))
            {
                token.append(input.charAt(pos++));
                linepos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            token.type = isHex ? Token.TokenType.HEX_LITERAL : Token.TokenType.LITERAL;
        }
        else if (ch == '"')
        {
            // stringa interpolata: il testo si conserva grezzo, con i suoi ${...} dentro, e viene
            // spezzato piu' tardi in parseTokens. Le graffe dentro un apice singolo non contano,
            // altrimenti "${m:'}'}" finirebbe a meta'
            pos++;
            linepos++;
            token.type = Token.TokenType.INTERPOLATION;
            int depth = 0;
            boolean inQuote = false;
            while (true)
            {
                if (pos >= input.length())
                {
                    if (expression != null && context != null)
                    {
                        throw new ExpressionException(context, this.expression, token, "Program truncated");
                    }
                    break;
                }
                char cur = input.charAt(pos);
                if (cur == '\\' && pos + 1 < input.length())
                {
                    char next = input.charAt(pos + 1);
                    if (next == '$')
                    {
                        // the escape survives into the raw text: expandInterpolation is the one that
                        // has to know this '$' does not open a placeholder, and it reads it later
                        token.append('\\');
                        token.append('$');
                    }
                    else
                    {
                        token.append(switch (next)
                        {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> next;
                        });
                    }
                    pos += 2;
                    linepos += 2;
                    continue;
                }
                if (cur == '\'' && depth > 0)
                {
                    inQuote = !inQuote;
                }
                else if (!inQuote)
                {
                    if (cur == '$' && pos + 1 < input.length() && input.charAt(pos + 1) == '{')
                    {
                        depth++;
                        token.append(cur);
                        pos++;
                        linepos++;
                        cur = input.charAt(pos);
                    }
                    else if (cur == '{' && depth > 0)
                    {
                        depth++;
                    }
                    else if (cur == '}' && depth > 0)
                    {
                        depth--;
                    }
                    else if (cur == '"' && depth == 0)
                    {
                        pos++;
                        linepos++;
                        break;
                    }
                }
                if (cur == '\n')
                {
                    lineno++;
                    linepos = 0;
                }
                token.append(cur);
                pos++;
                linepos++;
            }
            token.disguiseAs('"' + token.surface + '"', null);
        }
        else if (ch == '\'')
        {
            pos++;
            linepos++;
            token.type = Token.TokenType.STRINGPARAM;
            if (pos == input.length() && expression != null && context != null)
            {
                throw new ExpressionException(context, this.expression, token, "Program truncated");
            }
            ch = input.charAt(pos);
            while (ch != '\'')
            {
                if (ch == '\\')
                {
                    char nextChar = peekNextChar();
                    if (nextChar == 'n')
                    {
                        token.append('\n');
                    }
                    else if (nextChar == 't')
                    {
                        //throw new ExpressionException(context, this.expression, token,
                        //        "Tab character is not supported");
                        token.append('\t');
                    }
                    else if (nextChar == 'r')
                    {
                        throw new ExpressionException(context, this.expression, token,
                                "Carriage return character is not supported");
                        //token.append('\r');
                    }
                    else if (nextChar == '\\' || nextChar == '\'')
                    {
                        token.append(nextChar);
                    }
                    else
                    {
                        pos--;
                        linepos--;
                    }
                    pos += 2;
                    linepos += 2;
                    if (pos == input.length() && expression != null && context != null)
                    {
                        throw new ExpressionException(context, this.expression, token, "Program truncated");
                    }
                }
                else
                {
                    token.append(input.charAt(pos++));
                    linepos++;
                    if (ch == '\n')
                    {
                        lineno++;
                        linepos = 0;
                    }
                    if (pos == input.length() && expression != null && context != null)
                    {
                        throw new ExpressionException(context, this.expression, token, "Program truncated");
                    }
                }
                ch = input.charAt(pos);
            }
            pos++;
            linepos++;
            token.disguiseAs("'"+token.surface+"'", null);

        }
        else if (Character.isLetter(ch) || "_".indexOf(ch) >= 0)
        {
            while ((Character.isLetter(ch) || Character.isDigit(ch) || "_".indexOf(ch) >= 0
                    || isQualifierDot(ch, pos)
                    || token.length() == 0 && "_".indexOf(ch) >= 0) && (pos < input.length()))
            {
                token.append(input.charAt(pos++));
                linepos++;
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            // Remove optional white spaces after function or variable name
            if (Character.isWhitespace(ch))
            {
                while (Character.isWhitespace(ch) && pos < input.length())
                {
                    ch = input.charAt(pos++);
                    linepos++;
                    if (ch == '\n')
                    {
                        lineno++;
                        linepos = 0;
                    }
                }
                pos--;
                linepos--;
            }
            token.type = ch == '(' ? Token.TokenType.FUNCTION : Token.TokenType.VARIABLE;
            morphKeyword(token);
        }
        else if (ch == '(' || ch == ')' || ch == ',' ||
                ch == '{' || ch == '}' || ch == '[' || ch == ']')
        {
            if (ch == '(')
            {
                token.type = Token.TokenType.OPEN_PAREN;
            }
            else if (ch == ')')
            {
                token.type = Token.TokenType.CLOSE_PAREN;
            }
            else if (ch == ',')
            {
                token.type = Token.TokenType.COMMA;
                token.disguiseAs(", ", null);
            }
            else
            {
                token.type = Token.TokenType.MARKER;
            }
            token.append(ch);
            pos++;
            linepos++;

            if (expression != null && context != null && previousToken != null &&
                    previousToken.type == Token.TokenType.OPERATOR &&
                    (ch == ')' || ch == ',' || ch == ']' || ch == '}') &&
                    !previousToken.surface.equalsIgnoreCase(";")
            )
            {
                throw new ExpressionException(context, this.expression, previousToken,
                        "Can't have operator " + previousToken.surface + " at the end of a subexpression");
            }
        }
        else
        {
            String greedyMatch = "";
            int initialPos = pos;
            int initialLinePos = linepos;
            ch = input.charAt(pos);
            int validOperatorSeenUntil = -1;
            while (!Character.isLetter(ch) && !Character.isDigit(ch) && "_".indexOf(ch) < 0
                    && !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
                    && (pos < input.length()))
            {
                greedyMatch += ch;
                if (comments && "//".equals(greedyMatch))
                {

                    while (ch != '\n' && pos < input.length())
                    {
                        ch = input.charAt(pos++);
                        linepos++;
                        greedyMatch += ch;
                    }
                    if (ch == '\n')
                    {
                        lineno++;
                        linepos = 0;
                    }
                    token.append(greedyMatch);
                    token.type = Token.TokenType.MARKER;
                    return token; // skipping setting previous
                }
                pos++;
                linepos++;
                if (Expression.none.isAnOperator(greedyMatch))
                {
                    validOperatorSeenUntil = pos;
                }
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            if (newLinesMarkers && "$".equals(greedyMatch))
            {
                lineno++;
                linepos = 0;
                token.type = Token.TokenType.MARKER;
                token.append('$');
                return token; // skipping previous token lookback
            }
            if (validOperatorSeenUntil != -1)
            {
                token.append(input.substring(initialPos, validOperatorSeenUntil));
                pos = validOperatorSeenUntil;
                linepos = initialLinePos + validOperatorSeenUntil - initialPos;
            }
            else
            {
                token.append(greedyMatch);
            }

            if (previousToken == null || previousToken.type == Token.TokenType.OPERATOR
                    || previousToken.type == Token.TokenType.OPEN_PAREN || previousToken.type == Token.TokenType.COMMA
                    || (previousToken.type == Token.TokenType.MARKER && (previousToken.surface.equals("{") || previousToken.surface.equals("[")))
            )
            {
                token.disguiseAs(token.surface, null);
                token.surface += "u";
                token.type = Token.TokenType.UNARY_OPERATOR;
            }
            else
            {
                token.type = Token.TokenType.OPERATOR;
                token.disguiseAs(token.surface.equals(";") ? (token.surface + " ") : (" " + token.surface + " "), null);

            }
        }
        if (expression != null && context != null && previousToken != null &&
                (
                        token.type == Token.TokenType.LITERAL ||
                                token.type == Token.TokenType.HEX_LITERAL ||
                                token.type == Token.TokenType.VARIABLE ||
                                token.type == Token.TokenType.STRINGPARAM ||
                                (token.type == Token.TokenType.MARKER && (previousToken.surface.equalsIgnoreCase("{") || previousToken.surface.equalsIgnoreCase("["))) ||
                                token.type == Token.TokenType.FUNCTION
                ) && (
                previousToken.type == Token.TokenType.VARIABLE ||
                        previousToken.type == Token.TokenType.FUNCTION ||
                        previousToken.type == Token.TokenType.LITERAL ||
                        previousToken.type == Token.TokenType.CLOSE_PAREN ||
                        (previousToken.type == Token.TokenType.MARKER && (previousToken.surface.equalsIgnoreCase("}") || previousToken.surface.equalsIgnoreCase("]"))) ||
                        previousToken.type == Token.TokenType.HEX_LITERAL ||
                        previousToken.type == Token.TokenType.STRINGPARAM
        )
        )
        {
            throw new ExpressionException(context, this.expression, previousToken, "'" + token.surface + "' is not allowed after '" + previousToken.surface + "'");
        }
        return previousToken = token;
    }
}
