package blossom.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * The folders {@code core.io} can reach, and with what permissions.
 *
 * <p>Scripts have no absolute paths: an administrator declares named <em>roots</em>, and calls
 * start from there, e.g. {@code io.read_text('shared', 'note/today.txt')}. Three properties
 * follow from this on purpose: without any declarations nothing is opened, the root is visible at
 * the call site, and write access is a separate permission granted root by root.
 *
 * <p>The path is normalized and then actually resolved before the comparison, because {@code ..}
 * is not the only way to escape a folder: a symbolic link inside the root can point outside
 * without any textual check noticing. An optional external {@link #contains boundary} (on the
 * mod, the world folder) also limits where the roots themselves can live.
 *
 * @see Io the module that uses them
 */
public final class IoRoots
{
    /** No roots: {@code core.io} exists but reaches nowhere. */
    public static final IoRoots NONE = new IoRoots(Map.of(), Set.of());

    private final Map<String, Path> roots;
    private final Set<String> writable;
    private final @Nullable Path confinement;

    public IoRoots(Map<String, Path> roots, Set<String> writable)
    {
        this(roots, writable, null);
    }

    /**
     * @param confinement the folder beyond which no root may lead, or {@code null} if there is no
     *     external limit. On the mod this is the world folder: roots are declared inside it, and
     *     the rest of the server's disk is unreachable no matter the configuration.
     */
    public IoRoots(Map<String, Path> roots, Set<String> writable, @Nullable Path confinement)
    {
        Map<String, Path> normalized = new LinkedHashMap<>();
        roots.forEach((name, path) -> normalized.put(name, path.toAbsolutePath().normalize()));
        this.roots = Map.copyOf(normalized);
        this.writable = Set.copyOf(writable);
        this.confinement = confinement == null ? null : confinement.toAbsolutePath().normalize();
    }

    /** @return whether {@code directory} is inside the boundary, or whether there is no boundary to respect */
    public boolean contains(Path directory)
    {
        return confinement == null || directory.toAbsolutePath().normalize().startsWith(confinement);
    }

    /** The roots by name, already normalized: used by whoever composes a larger set out of them. */
    public Map<String, Path> paths()
    {
        return roots;
    }

    /** The names of the writable roots. */
    public Set<String> writableNames()
    {
        return writable;
    }

    public List<String> names()
    {
        return roots.keySet().stream().sorted().toList();
    }

    public boolean isWritable(String name)
    {
        return writable.contains(name);
    }

    /**
     * The file indicated by {@code relative} inside root {@code name}.
     *
     * @param forWriting whether the operation modifies something: a read-only root fails here
     * @throws BlossomRuntimeError if the root does not exist, is not writable, or the path escapes
     */
    public Path resolve(String name, String relative, boolean forWriting)
    {
        Path root = roots.get(name);
        if (root == null)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    roots.isEmpty()
                            ? "No io root is configured on this server: core.io cannot open anything."
                                    + " Roots are declared in the mod configuration."
                            : "Unknown io root: '" + name + "'. The configured ones are " + names() + ".",
                    Suggestions.closestTo(name, names()));
        }
        if (forWriting && !writable.contains(name))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Root '" + name + "' is read-only: to write to it, declare it writable in the mod"
                            + " configuration.");
        }
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || !contains(candidate))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Path '" + relative + "' escapes root '" + name + "'.");
        }
        return checkReal(root, candidate, name, relative);
    }

    /**
     * Re-checks after following symbolic links.
     *
     * <p>The comparison on the normalized path stops {@code ..}, not a link placed inside the
     * root that points outward. If the file does not exist yet (the case of a write), the folder
     * that will contain it, which does exist, is checked instead.
     */
    private Path checkReal(Path root, Path candidate, String name, String relative)
    {
        try
        {
            Path realRoot = root.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing))
            {
                existing = existing.getParent();
            }
            Path real = existing == null ? null : existing.toRealPath();
            if (real != null && (!real.startsWith(realRoot) || !contains(real) || !contains(realRoot)))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                        "Path '" + relative + "' escapes root '" + name + "' through a symbolic link.");
            }
        }
        catch (IOException e)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Cannot verify '" + relative + "' in root '" + name + "': " + e.getMessage());
        }
        return candidate;
    }
}
