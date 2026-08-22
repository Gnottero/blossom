package blossom.exception;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The root of Blossom's typed errors (PIANO_LINGUAGGIO.md §4).
 *
 * <p>An error carries four things: its category (the java type itself, so it is distinguishable
 * even from a targeted {@code catch} written in Blossom), the position in the source, a call
 * stack that crosses module boundaries, and, when it makes sense, a suggestion.
 *
 * <p>Extends {@code InternalExpressionException} so a typed error stays catchable anywhere a
 * generic error was, starting with a {@code try} written in Blossom, without every catch site
 * needing to be updated.
 *
 * <p>The stack is built while unwinding: whoever catches the error while leaving a function adds
 * its own frame with {@link #pushFrame}. This is the opposite of a java stack trace captured at
 * throw time, and it matters because the innermost frame alone never says enough: {@code add}
 * failed, but called from where?
 */
public abstract class BlossomError extends InternalExpressionException
{
    /**
     * The position of a token inside an expression, for errors that arise during evaluation but
     * know exactly which piece of code they tripped over.
     */
    public static SourcePosition positionOf(blossom.interpreter.Expression expression, blossom.interpreter.Token token)
    {
        return new SourcePosition(expression.getModuleName() == null ? "<code>" : expression.getModuleName(),
                token.lineno + 1, token.linepos + 1);
    }

    /** One link of the call chain: which function, at what point in the source. */
    public record Frame(String function, SourcePosition position)
    {
    }

    /** Indentation applied to every line of a formatted error. */
    private static final String INDENT = "  ";

    private final SourcePosition position;
    private final @Nullable String suggestion;
    private final List<Frame> frames = new ArrayList<>();

    protected BlossomError(SourcePosition position, String message)
    {
        this(position, message, null);
    }

    protected BlossomError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(message);
        this.position = position;
        this.suggestion = suggestion;
    }

    /**
     * The category name shown to the user: by default the simple class name, which is exactly
     * what the plan lists ({@code ParseError}, {@code ImportError}, ...).
     */
    public String kind()
    {
        return getClass().getSimpleName();
    }

    public SourcePosition position()
    {
        return position;
    }

    public @Nullable String suggestion()
    {
        return suggestion;
    }

    public List<Frame> frames()
    {
        return List.copyOf(frames);
    }

    /** Adds one level of call while the error unwinds. */
    public BlossomError pushFrame(String function, SourcePosition callSite)
    {
        frames.add(new Frame(function, callSite));
        return this;
    }

    /**
     * The complete message, in the format from the plan:
     *
     * <pre>
     * UndefinedSymbolError
     *   in math.vector.add  (src/math/vector.bs:4:12)
     *   called from main    (src/main.bs:8:1)
     *
     *   Symbol 'ad' not found in module 'math.vector'.
     *   Did you mean: add?
     * </pre>
     */
    public String format()
    {
        StringBuilder out = new StringBuilder(kind());
        List<Frame> allFrames = frames();
        int width = 0;
        for (int i = 0; i < allFrames.size(); i++)
        {
            width = Math.max(width, label(i).length() + allFrames.get(i).function().length());
        }
        for (int i = 0; i < allFrames.size(); i++)
        {
            Frame frame = allFrames.get(i);
            String head = label(i) + frame.function();
            out.append("\n").append(INDENT).append(head)
               .append(" ".repeat(width - head.length() + 2))
               .append('(').append(frame.position()).append(')');
        }
        if (allFrames.isEmpty() && position.isKnown())
        {
            out.append("\n").append(INDENT).append("in ").append(position);
        }
        out.append("\n\n").append(indent(getMessage()));
        if (suggestion != null)
        {
            out.append("\n").append(INDENT).append("Did you mean: ").append(suggestion).append('?');
        }
        return out.toString();
    }

    private static String label(int frameIndex)
    {
        return frameIndex == 0 ? "in " : "called from ";
    }

    /**
     * Indents every line of the text, not just the first: a multi-line message (the code snippet
     * of a {@code ParseError}, for example) stays aligned with the rest of the error instead of
     * falling back to the margin from the second line on. Empty lines stay empty, with no
     * trailing spaces.
     */
    private static String indent(String text)
    {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++)
        {
            if (i > 0)
            {
                out.append('\n');
            }
            String line = stripTrailing(lines[i]);
            if (!line.isEmpty())
            {
                out.append(INDENT).append(line);
            }
        }
        return out.toString();
    }

    private static String stripTrailing(String line)
    {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\r'))
        {
            end--;
        }
        return line.substring(0, end);
    }

    @Override
    public String toString()
    {
        return format();
    }

}
