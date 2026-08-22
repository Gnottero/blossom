package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * Everything else: the generic category, with a subcode that says what it's actually about.
 *
 * <p>The subcode exists because "RuntimeError" alone isn't programmatically distinguishable, which
 * is exactly the flaw of the single-error system we started from. An error that keeps landing here
 * is the signal that it needs its own category.
 */
public class BlossomRuntimeError extends BlossomError
{
    private final String subcode;

    public BlossomRuntimeError(SourcePosition position, String subcode, String message)
    {
        this(position, subcode, message, null);
    }

    public BlossomRuntimeError(SourcePosition position, String subcode, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
        this.subcode = subcode;
    }

    public String subcode()
    {
        return subcode;
    }

    @Override
    public String kind()
    {
        return "RuntimeError[" + subcode + "]";
    }
}
