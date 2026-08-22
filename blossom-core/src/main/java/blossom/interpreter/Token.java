package blossom.interpreter;

import org.jspecify.annotations.Nullable;

public class Token implements Comparable<Token> {

    enum TokenType {
        FUNCTION(true, false), OPERATOR(true, false), UNARY_OPERATOR(true, false),
        VARIABLE(false, false), CONSTANT(false, true),
        LITERAL(false, true), HEX_LITERAL(false, true), STRINGPARAM(false, true),
        /** una stringa fra doppi apici, che il tokenizer espande nei pezzi che la compongono */
        INTERPOLATION(false, true),
        OPEN_PAREN(false, true), COMMA(false, true), CLOSE_PAREN(false, true), MARKER(false, true);

        final boolean functional;
        final boolean constant;

        TokenType(boolean functional, boolean constant) {
            this.functional = functional;
            this.constant = constant;
        }

        public boolean isFunctional() {
            return functional;
        }

        public boolean isConstant() {
            return constant;
        }
    }

    public String surface = "";
    public String display = "";
    public String comment = "";
    public TokenType type;
    public int pos;
    public int ordinal = 0;
    public int linepos;
    public int lineno;

    public Expression.@Nullable ExpressionNode node = null;

    /**
     * Se questa e' la chiamata scritta dopo un valore, {@code f()(2)} o {@code (fn(x) -> x)(2)}.
     *
     * <p>Il token e' un {@code call} sintetico, ma i suoi argomenti non sono tutti scritti fra le
     * parentesi: il primo - la funzione da chiamare - e' il valore che sta prima, e va ripescato
     * dallo stack invece che dalla lista dei parametri.
     */
    public boolean postfixCall = false;

    public static final Token NONE = new Token();

    public Token morphedInto(TokenType newType, String newSurface) {
        Token created = new Token();
        created.surface = newSurface;
        created.type = newType;
        created.pos = pos;
        created.linepos = linepos;
        created.lineno = lineno;
        created.node = node;
        created.ordinal = ordinal + 1;
        return created;
    }

    public void swapPlace(Token other) {
        int order = other.ordinal;
        other.ordinal = ordinal;
        ordinal = order;
    }

    public void morph(TokenType type, String s) {
        this.type = type;
        this.surface = s;
        this.display = "";
        this.comment = "";
    }

    public Token disguiseAs(@Nullable String s, @Nullable String s1) {
        if (s != null) {
            display = s;
        }
        if (s1 != null) {
            comment = s1;
        }
        return this;
    }

    public void append(char c) {
        surface += c;
    }

    public void append(String s) {
        surface += s;
    }

    public char charAt(int pos) {
        return surface.charAt(pos);
    }

    public int length() {
        return surface.length();
    }

    @Override
    public String toString() {
        return surface;
    }

    @Override
    public int compareTo(Token o) {
        // compare by lineno, then by linepos, then by ordinal
        if (lineno != o.lineno)
        {
            return lineno - o.lineno;
        }
        if (linepos != o.linepos)
        {
            return linepos - o.linepos;
        }
        return ordinal - o.ordinal;
    }
}
