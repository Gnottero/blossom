package blossom.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Il nome puntato di un modulo, es. {@code math.vector}.
 *
 * <p>È anche la sola fonte di verità sulla corrispondenza fra nome e percorso: il nome di un modulo
 * è il suo percorso relativo a {@code src/} con i separatori sostituiti da punti, e la
 * dichiarazione {@code module ...;} in testa al file deve combaciare. Il controllo sta qui e non
 * sparso nel loader.
 */
public record ModuleName(String value)
{
    /** l'estensione dei sorgenti Blossom, come da PIANO_LINGUAGGIO.md §7 */
    public static final String EXTENSION = ".bs";
    /** l'estensione delle librerie: un modulo che non ha un entrypoint suo */
    public static final String LIBRARY_EXTENSION = ".bsl";

    public ModuleName
    {
        if (value.isEmpty())
        {
            throw new IllegalArgumentException("a module name cannot be empty");
        }
        for (String segment : value.split("\\.", -1))
        {
            if (!isValidSegment(segment))
            {
                throw new IllegalArgumentException("'" + value + "' is not a valid module name: segment '"
                        + segment + "' must start with a letter and contain only letters, digits and underscores");
            }
        }
    }

    private static boolean isValidSegment(String segment)
    {
        if (segment.isEmpty() || !Character.isLetter(segment.charAt(0)))
        {
            return false;
        }
        return segment.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    /**
     * Il nome che compete al file {@code file} dentro la cartella sorgenti {@code sourceRoot}.
     *
     * @throws IllegalArgumentException se il file è fuori da {@code sourceRoot} o non è un sorgente
     */
    public static ModuleName ofPath(Path sourceRoot, Path file)
    {
        Path relative = sourceRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        String name = relative.toString().replace(java.io.File.separatorChar, '.');
        if (!name.endsWith(EXTENSION))
        {
            throw new IllegalArgumentException("'" + file + "' is not a Blossom source file (missing " + EXTENSION + ")");
        }
        return new ModuleName(name.substring(0, name.length() - EXTENSION.length()).toLowerCase(Locale.ROOT));
    }

    /** Il file che dovrebbe contenere questo modulo, dentro {@code sourceRoot}. */
    public Path toPath(Path sourceRoot)
    {
        Path path = sourceRoot;
        List<String> segments = List.of(value.split("\\."));
        for (int i = 0; i < segments.size() - 1; i++)
        {
            path = path.resolve(segments.get(i));
        }
        return path.resolve(segments.getLast() + EXTENSION);
    }

    /**
     * Il nome scritto in un import, che usa le barre come un percorso ({@code "math/vector"}) oppure
     * i punti ({@code "core.math"}), normalizzato a nome puntato.
     */
    public static ModuleName ofImportTarget(String target)
    {
        String normalized = target.replace('\\', '/').replace('/', '.').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(EXTENSION))
        {
            normalized = normalized.substring(0, normalized.length() - EXTENSION.length());
        }
        return new ModuleName(normalized);
    }

    @Override
    public String toString()
    {
        return value;
    }
}
