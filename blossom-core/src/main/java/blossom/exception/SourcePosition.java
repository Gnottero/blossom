package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * Where, exactly: file, line, column.
 *
 * <p>Every error must be able to report this. A small immutable value, since
 * many of them get constructed during parsing.
 */
public record SourcePosition(String file, int line, int column)
{
    /** Unknown position: used only where the information is genuinely unavailable, never as a shortcut. */
    public static final SourcePosition UNKNOWN = new SourcePosition("<unknown>", 0, 0);

    public boolean isKnown()
    {
        return line > 0;
    }

    @Override
    public String toString()
    {
        return isKnown() ? file + ":" + line + ":" + column : file;
    }

    public static SourcePosition of(@Nullable String file, int line, int column)
    {
        return file == null ? UNKNOWN : new SourcePosition(file, line, column);
    }
}
