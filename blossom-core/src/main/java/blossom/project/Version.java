package blossom.project;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * A semver version: {@code MAJOR.MINOR.PATCH} with an optional pre-release.
 *
 * <p>The implemented subset is <a href="https://semver.org">semver 2.0.0</a> minus build metadata
 * ({@code +sha}), which by definition takes part in neither ordering nor equality: accepting and
 * ignoring it would invite using it to tell two releases apart, which is exactly what the
 * specification says not to do.
 *
 * <p>The rules that matter here:
 * <ul>
 *   <li>the three numbers are compared first, then the pre-release;
 *   <li>a version with a pre-release comes <em>before</em> the same one without ({@code 1.0.0-rc1 <
 *       1.0.0});
 *   <li>between two pre-releases the identifiers are compared dot by dot, numeric ones before
 *       alphanumeric ones.
 * </ul>
 */
public record Version(int major, int minor, int patch, @Nullable String preRelease) implements Comparable<Version>
{
    public static final Version ZERO = new Version(0, 0, 0, null);

    /**
     * @throws IllegalArgumentException if the text is not a version
     */
    public static Version parse(String text)
    {
        String trimmed = text.trim();
        String core = trimmed;
        String pre = null;
        int dash = trimmed.indexOf('-');
        if (dash >= 0)
        {
            core = trimmed.substring(0, dash);
            pre = trimmed.substring(dash + 1);
            if (pre.isEmpty())
            {
                throw new IllegalArgumentException("version '" + text + "': the pre-release after '-' is empty");
            }
        }
        String[] parts = core.split("\\.", -1);
        if (parts.length != 3)
        {
            throw new IllegalArgumentException("version '" + text + "': three numbers are required, e.g. 1.2.0");
        }
        return new Version(number(parts[0], text), number(parts[1], text), number(parts[2], text), pre);
    }

    /** Like {@link #parse}, but returns {@code null} instead of throwing. */
    public static @Nullable Version tryParse(String text)
    {
        try
        {
            return parse(text);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static int number(String part, String whole)
    {
        if (part.isEmpty() || !part.chars().allMatch(Character::isDigit))
        {
            throw new IllegalArgumentException("version '" + whole + "': '" + part + "' is not a number");
        }
        if (part.length() > 1 && part.charAt(0) == '0')
        {
            throw new IllegalArgumentException("version '" + whole + "': '" + part + "' has a leading zero");
        }
        return Integer.parseInt(part);
    }

    public boolean isPreRelease()
    {
        return preRelease != null;
    }

    /** The same version without the pre-release: used to compare the three numbers alone. */
    public Version release()
    {
        return preRelease == null ? this : new Version(major, minor, patch, null);
    }

    @Override
    public int compareTo(Version other)
    {
        int byNumbers = Comparator.comparingInt(Version::major)
                .thenComparingInt(Version::minor)
                .thenComparingInt(Version::patch)
                .compare(this, other);
        if (byNumbers != 0)
        {
            return byNumbers;
        }
        if (preRelease == null && other.preRelease == null)
        {
            return 0;
        }
        // a release always comes after any of its pre-releases
        if (preRelease == null)
        {
            return 1;
        }
        if (other.preRelease == null)
        {
            return -1;
        }
        return comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(String left, String right)
    {
        List<String> mine = List.of(left.split("\\.", -1));
        List<String> theirs = List.of(right.split("\\.", -1));
        for (int i = 0; i < Math.max(mine.size(), theirs.size()); i++)
        {
            if (i >= mine.size())
            {
                return -1; // meno identificatori = precede, a parità di prefisso
            }
            if (i >= theirs.size())
            {
                return 1;
            }
            String a = mine.get(i);
            String b = theirs.get(i);
            boolean aNumeric = isNumeric(a);
            boolean bNumeric = isNumeric(b);
            if (aNumeric && bNumeric)
            {
                int byValue = Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                if (byValue != 0)
                {
                    return byValue;
                }
            }
            else if (aNumeric != bNumeric)
            {
                return aNumeric ? -1 : 1; // i numerici precedono gli alfanumerici
            }
            else
            {
                int byText = a.compareTo(b);
                if (byText != 0)
                {
                    return byText;
                }
            }
        }
        return 0;
    }

    private static boolean isNumeric(String identifier)
    {
        return !identifier.isEmpty() && identifier.chars().allMatch(Character::isDigit);
    }

    @Override
    public String toString()
    {
        String core = major + "." + minor + "." + patch;
        return preRelease == null ? core : core + '-' + preRelease.toLowerCase(Locale.ROOT);
    }
}
