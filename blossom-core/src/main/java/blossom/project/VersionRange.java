package blossom.project;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Un vincolo su una versione, nella forma che si scrive in {@code blossom.json}.
 *
 * <p>Le forme accettate, deliberatamente poche:
 *
 * <pre>
 * "1.2.0"     esattamente quella versione
 * "^1.2.0"    compatibile: dalla 1.2.0 (inclusa) fino alla 2.0.0 (esclusa)
 * "~1.2.0"    solo patch: dalla 1.2.0 (inclusa) fino alla 1.3.0 (esclusa)
 * "&gt;=1.2.0"   quella o successiva
 * "&gt;=1.2, &lt;2" più vincoli insieme, separati da virgola: valgono tutti
 * "*"         qualunque versione
 * </pre>
 *
 * <p>Non c'è l'operatore OR (il {@code ||} di npm). Un vincolo che accetta due intervalli scollegati
 * quasi sempre vuol dire che la dipendenza è stata rotta due volte e nessuno ha deciso quale
 * versione supportare davvero: meglio che quella decisione la prenda chi scrive il manifesto.
 *
 * <h2>Pre-release</h2>
 *
 * <p>Un pre-release non soddisfa mai un vincolo che non lo nomina esplicitamente. {@code ^1.0.0} non
 * accetta {@code 2.0.0-rc1}, e nemmeno {@code 1.1.0-rc1}: chi vuole una beta la chiede per nome.
 * Senza questa regola bastherebbe pubblicare una beta per farla installare a tutti.
 */
public final class VersionRange
{
    /** Qualunque versione stabile. */
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
    /** i pre-release passano solo se il vincolo ne nomina uno */
    private final boolean allowsPreRelease;

    private VersionRange(List<Constraint> constraints, String text)
    {
        this.constraints = constraints;
        this.text = text;
        this.allowsPreRelease = text.contains("-");
    }

    /**
     * @throws IllegalArgumentException se il vincolo non è scritto in una delle forme accettate
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
     * La prima versione che {@code ^} non accetta più.
     *
     * <p>Sotto la 1.0.0 semver dice che ogni incremento può rompere, quindi {@code ^0.2.3} si ferma
     * alla 0.3.0 e {@code ^0.0.3} alla 0.0.4. Non è un dettaglio: quasi tutte le librerie giovani
     * stanno lì.
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

    /** La più alta fra le versioni date che soddisfa il vincolo, o {@code null} se nessuna. */
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
