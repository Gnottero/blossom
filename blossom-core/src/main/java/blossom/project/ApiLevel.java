package blossom.project;

import blossom.exception.CompatibilityError;
import blossom.exception.SourcePosition;

/**
 * The API level declared in the manifest, and the rule that says whether an app can run.
 *
 * <p>{@code api_level} is {@code MAJOR.MINOR} and describes Blossom's API, not the app's. <b>MAJOR</b>
 * changes when something that used to work stops working (a symbol removed or renamed, a signature
 * or event that changes); <b>MINOR</b> only when something is added. Bug fixes move neither.
 *
 * <p>An app runs if MAJOR matches exactly and its MINOR does not exceed the runtime's. The two
 * directions fail for different reasons, and the message says which. The field is optional: an app
 * that omits it declares the lowest MINOR of the current MAJOR.
 */
public record ApiLevel(int major, int minor) implements Comparable<ApiLevel>
{
    /**
     * The API level provided by this runtime.
     *
     * <p>Must be raised together with the change that justifies it, not at the end of a release
     * cycle: a number bumped later, in bulk, no longer tells which version each function dates back to.
     */
    public static final ApiLevel CURRENT = new ApiLevel(2, 0);

    /**
     * What is assumed for a manifest that does not declare it: the lowest MINOR of the current
     * MAJOR. Pinning it to a literal would make every manifest without the field unloadable the
     * moment MAJOR moves on, which is the opposite of "I use nothing recent".
     */
    public static final ApiLevel DEFAULT = new ApiLevel(CURRENT.major(), 0);

    /**
     * @throws IllegalArgumentException if not in the form {@code MAJOR.MINOR}
     */
    public static ApiLevel parse(String text)
    {
        String[] parts = text.trim().split("\\.");
        if (parts.length != 2)
        {
            throw new IllegalArgumentException("api_level '" + text + "': it must have the form MAJOR.MINOR, e.g. 1.0");
        }
        try
        {
            return new ApiLevel(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("api_level '" + text + "': MAJOR and MINOR must be numbers");
        }
    }

    /** @return whether an app declaring this level can run on {@code runtime} */
    public boolean isSupportedBy(ApiLevel runtime)
    {
        return major == runtime.major() && minor <= runtime.minor();
    }

    /**
     * Checks the level and fails with a message that says what to do.
     *
     * @throws CompatibilityError if the app cannot run on this runtime
     */
    public void requireSupportedBy(ApiLevel runtime, String appName, SourcePosition where)
    {
        if (isSupportedBy(runtime))
        {
            return;
        }
        if (major != runtime.major())
        {
            throw new CompatibilityError(where, "App '" + appName + "' declares api_level " + this
                    + ", but this Blossom provides API " + runtime + ". These are two different APIs: between one"
                    + " MAJOR and the next, something that worked was removed or changed meaning, so the app has"
                    + " to be updated, not forced.");
        }
        throw new CompatibilityError(where, "App '" + appName + "' declares api_level " + this
                + ", but this Blossom only goes up to " + runtime + ". The app uses something this version does"
                + " not have yet: update the mod, or lower 'api_level' if you do not actually need it.");
    }

    @Override
    public int compareTo(ApiLevel other)
    {
        int byMajor = Integer.compare(major, other.major);
        return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString()
    {
        return major + "." + minor;
    }
}
