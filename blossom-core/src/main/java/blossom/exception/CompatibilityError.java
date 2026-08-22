package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * The app and its surroundings don't agree: wrong API level, a dependency on a version that
 * doesn't satisfy the declared constraint, lock integrity that doesn't check out.
 *
 * <p>Its own category rather than a generic {@code RuntimeError} because it's the one error that
 * has nothing to do with the code: the program can be perfectly correct and still fail, and whoever
 * reads it needs to understand right away that they should look at the manifest, not the source.
 *
 * <p>Always raised at load time, never at runtime: if a version doesn't check out, it's discovered
 * before a single line is executed.
 */
public class CompatibilityError extends BlossomError
{
    public CompatibilityError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public CompatibilityError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
