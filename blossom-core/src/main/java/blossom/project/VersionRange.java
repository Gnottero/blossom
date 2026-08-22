package blossom.project;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A constraint on a version, in the form written in {@code blossom.json}.
 *
 * <pre>
 * "1.2.0"     exactly that version
 * "^1.2.0"    compatible: from 1.2.0 (included) to 2.0.0 (excluded)
 * "~1.2.0"    patches only: from 1.2.0 (included) to 1.3.0 (excluded)
 * "&gt;=1.2.0"   that one or later
 * "&gt;=1.2, &lt;2" several constraints together, comma separated: all of them apply
 * "*"         any version
 * </pre>
 *
 * <p>There is no npm style OR: a constraint accepting two disconnected ranges almost always means
 * nobody decided which version to support, and that decision belongs to whoever writes the manifest.
 * A pre-release never satisfies a constraint that does not name it, since without that rule
 * publishing a beta would be enough to have everyone install it.
 */
public final class VersionRange
{
    /** Any stable version. */
    public static final VersionRange ANY = new VersionRange(List.of(), "*");

    private sealed interface Constraint
            permits AtLeast, LessThan, AtMost, Exactly, GreaterThan
    {
        boolean accepts(Version version);
    }

    private record AtLeast(Version bound) implements Constraint
    {
        @Override
        public boolean accepts(Version version)
        {
            return version.compareTo(bound) >= 0;
        }
    }

    private record LessThan(Version bound) implements Constraint
    {
        @Override
        public boolean accepts(Version version)
        {
            return version.compareTo(bound) < 0;
        }
    }

    private record AtMost(Version bound) implements Constraint
    {
        @Override
        public boolean accepts(Version version)
        {
            return version.compareTo(bound) <= 0;
        }
    }

    private record Exactly(Version bound) implements Constraint
    {
        @Override
        public boolean accepts(Version version)
        {
            return version.equals(bound);
        }
    }

    private record GreaterThan(Version bound) implements Constraint
    {
        @Override
        public boolean accepts(Version version)
        {
            return version.compareTo(bound) > 0;
        }
    }

    private final List<Constraint> constraints;
    private final String text;
    /** pre-releases pass only if the constraint names one */
    private final boolean allowsPreRelease;

    private VersionRange(List<Constraint> constraints, String text)
    {
        this.constraints = constraints;
        this.text = text;
        this.allowsPreRelease = text.contains("-");
    }

    /**
     * @throws IllegalArgumentException if the constraint is not written in one of the accepted forms
     */
    public static VersionRange parse(String text)
    {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.equals("*"))
        {
            return ANY;
        }
        List<Constraint> constraints = new ArrayList<>();
        for (String part : trimmed.split(","))
        {
            constraints.addAll(parseOne(part.trim(), trimmed));
        }
        return new VersionRange(List.copyOf(constraints), trimmed);
    }

    private static List<Constraint> parseOne(String part, String whole)
    {
        if (part.startsWith("^"))
        {
            Version base = Version.parse(part.substring(1));
            return List.of(new AtLeast(base), new LessThan(nextIncompatible(base)));
        }
        if (part.startsWith("~"))
        {
            Version base = Version.parse(part.substring(1));
            return List.of(new AtLeast(base), new LessThan(new Version(base.major(), base.minor() + 1, 0, null)));
        }
        if (part.startsWith(">="))
        {
            return List.of(new AtLeast(Version.parse(part.substring(2))));
        }
        if (part.startsWith("<="))
        {
            return List.of(new AtMost(Version.parse(part.substring(2))));
        }
        if (part.startsWith("<"))
        {
            return List.of(new LessThan(Version.parse(part.substring(1))));
        }
        if (part.startsWith(">"))
        {
            return List.of(new GreaterThan(Version.parse(part.substring(1))));
        }
        if (part.startsWith("="))
        {
            return List.of(new Exactly(Version.parse(part.substring(1))));
        }
        if (part.chars().anyMatch(c -> c == '|'))
        {
            throw new IllegalArgumentException("constraint '" + whole + "': the '||' alternative is not supported."
                    + " Pick the range you actually want to support.");
        }
        return List.of(new Exactly(Version.parse(part)));
    }

    /**
     * The first version {@code ^} no longer accepts.
     *
     * <p>Below 1.0.0 semver says every increment may break, so {@code ^0.2.3} stops at 0.3.0 and
     * {@code ^0.0.3} at 0.0.4. Not a detail: almost every young library lives there.
     */
    private static Version nextIncompatible(Version base)
    {
        if (base.major() > 0)
        {
            return new Version(base.major() + 1, 0, 0, null);
        }
        if (base.minor() > 0)
        {
            return new Version(0, base.minor() + 1, 0, null);
        }
        return new Version(0, 0, base.patch() + 1, null);
    }

    public boolean accepts(@Nullable Version version)
    {
        if (version == null)
        {
            return false;
        }
        if (version.isPreRelease() && !allowsPreRelease)
        {
            return false;
        }
        for (Constraint constraint : constraints)
        {
            if (!constraint.accepts(version))
            {
                return false;
            }
        }
        return true;
    }

    /** The highest of the given versions satisfying the constraint, or {@code null} if none. */
    public @Nullable Version best(Iterable<Version> candidates)
    {
        Version best = null;
        for (Version candidate : candidates)
        {
            if (accepts(candidate) && (best == null || candidate.compareTo(best) > 0))
            {
                best = candidate;
            }
        }
        return best;
    }

    public boolean isAny()
    {
        return constraints.isEmpty();
    }

    @Override
    public String toString()
    {
        return text;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof VersionRange range && text.equals(range.text);
    }

    @Override
    public int hashCode()
    {
        return text.hashCode();
    }
}
