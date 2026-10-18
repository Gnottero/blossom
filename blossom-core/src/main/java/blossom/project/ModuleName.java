package blossom.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * A module's dotted name, e.g. {@code math.vector}.
 *
 * <p>This is also the single source of truth for how a name maps to a path: a module's name is its
 * path relative to {@code src/} with separators replaced by dots, and the {@code module ...;}
 * declaration at the top of the file must match it. The check lives here, not scattered in the loader.
 */
public record ModuleName(String value)
{
    /** the extension of Blossom sources */
    public static final String EXTENSION = ".bs";
    /** the extension of libraries: a module with no entrypoint of its own */
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
     * The name that belongs to file {@code file} inside the source directory {@code sourceRoot}.
     *
     * @throws IllegalArgumentException if the file is outside {@code sourceRoot} or is not a source file
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

    /** The file that should contain this module, inside {@code sourceRoot}. */
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
     * The name as written in an import, which uses slashes like a path ({@code "math/vector"}) or
     * dots ({@code "core.math"}), normalized to a dotted name.
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
