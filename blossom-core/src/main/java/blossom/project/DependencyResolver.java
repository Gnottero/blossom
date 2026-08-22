package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import blossom.exception.CompatibilityError;
import blossom.exception.ImportError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * From a declared dependency to a directory on disk, with the version checked.
 *
 * <p>For each dependency, in this order: <b>find it</b> (a given directory, a pack installed in the
 * same world, or the registry), <b>read its manifest</b> (which says what version it really is and
 * what dependencies it has), and <b>check the constraint</b>, stopping if it is not satisfied.
 *
 * <p>The third step is the reason this class exists: without it, "depends on utils ^1.2" is just a
 * comment, and the first sign that the directory next door moved to 2.0 arrives as a function whose
 * signature changed, three screens further along.
 *
 * <p>Resolution is <b>transitive</b>, within each dependency's own namespace; a cycle between
 * projects is an error, just as it is between modules.
 */
public final class DependencyResolver
{
    /** A resolved dependency: where it lives and what it turned out to be. */
    public record Resolved(Dependency declared, Path root, @Nullable Version version, ModuleResolver modules)
    {
    }

    private final PackEnvironment environment;
    /** the projects currently being resolved, to detect a cycle between packs */
    private final Deque<Path> onPath = new ArrayDeque<>();
    /** what the lock said last time; empty if there was none or a refresh is in progress */
    private LockFile lock = LockFile.EMPTY;
    /** what the lock will say next time */
    private final Map<String, LockFile.Entry> recorded = new LinkedHashMap<>();
    /** {@code true} when re-resolving while ignoring what the lock had chosen */
    private final boolean refreshing;

    public DependencyResolver(PackEnvironment environment)
    {
        this(environment, false);
    }

    public DependencyResolver(PackEnvironment environment, boolean refreshing)
    {
        this.environment = environment;
        this.refreshing = refreshing;
    }

    /**
     * Resolves the root project's dependencies, taking its {@code blossom.lock} into account and
     * rewriting it if it changed.
     *
     * <p>Only the root has a lock. A dependency is, by definition, someone else's stuff: writing a
     * file inside its directory (which might live in the registry cache, or be read-only) would be
     * wrong twice over. The choices made for its dependencies still end up in the root's lock, under
     * the chain of aliases that leads down to them, so reproducibility is complete all the same.
     */
    public ModuleResolver resolveRoot(Path root, ProjectManifest manifest) throws IOException
    {
        lock = refreshing ? LockFile.EMPTY : LockFile.read(root);
        ModuleResolver resolver = resolveFor(root, manifest, "");
        new LockFile(Map.copyOf(recorded)).writeIfChanged(root);
        return resolver;
    }

    /**
     * Resolves all of the project's dependencies in {@code root} and builds its module resolver.
     */
    public ModuleResolver resolveFor(Path root, ProjectManifest manifest)
    {
        return resolveFor(root, manifest, "");
    }

    private ModuleResolver resolveFor(Path root, ProjectManifest manifest, String keyPrefix)
    {
        if (onPath.contains(root))
        {
            List<String> cycle = new java.util.ArrayList<>(onPath.stream().map(Path::toString).toList());
            cycle.add(root.toString());
            throw new ImportError(SourcePosition.UNKNOWN, "circular pack dependency: "
                    + String.join(" → ", cycle) + ". A pack cannot depend on itself, not even indirectly.");
        }
        onPath.addLast(root);
        try
        {
            Map<String, ModuleResolver> dependencies = new LinkedHashMap<>();
            for (Dependency declared : manifest.dependencies().values())
            {
                Resolved resolved = resolve(declared, keyPrefix + declared.alias());
                dependencies.put(normalizeAlias(declared.alias()), resolved.modules());
            }
            return ModuleResolver.of(manifest.sourceRoot(root), dependencies);
        }
        finally
        {
            onPath.removeLast();
        }
    }

    /** The alias as an import writes it: lowercase, with hyphens turned into underscores. */
    static String normalizeAlias(String alias)
    {
        return alias.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private Resolved resolve(Dependency declared, String lockKey)
    {
        Path root = switch (declared.kind())
        {
            case PATH -> requirePath(declared);
            case PACK -> requireInstalledPack(declared);
            case REGISTRY -> fetchFromRegistry(declared, lock.of(lockKey));
        };

        ProjectManifest manifest = readManifest(root);
        Version found = manifest == null ? null : manifest.version();
        checkVersion(declared, found, root, manifest == null);
        record(lockKey, declared, root, found);

        // a dependency without a manifest is just a source directory: it has no dependencies of
        // its own, and there is nothing to resolve underneath it
        ModuleResolver modules = manifest == null
                ? ModuleResolver.ofSourceRoot(root)
                : resolveFor(root, manifest, lockKey + '.');
        return new Resolved(declared, root, found, modules);
    }

    /**
     * Records in the lock what was chosen, and checks the fingerprint of what comes from outside.
     *
     * <p>A path dependency does not enter the lock: it is the directory next door, being edited
     * right now, and locking it would mean making the project fail on every save.
     */
    private void record(String lockKey, Dependency declared, Path root, @Nullable Version found)
    {
        if (declared.kind() == Dependency.Kind.PATH || found == null)
        {
            return;
        }
        String fingerprint = null;
        if (declared.kind() == Dependency.Kind.REGISTRY)
        {
            try
            {
                fingerprint = Integrity.sha256Of(root);
            }
            catch (IOException e)
            {
                throw new ImportError(SourcePosition.UNKNOWN,
                        "Cannot compute the fingerprint of '" + declared.alias() + "' in " + root + ": " + e.getMessage());
            }
            LockFile.Entry locked = lock.of(lockKey);
            // only when the version matches: if the version changed (because the constraint was
            // rewritten, or because the lock was refreshed), a different fingerprint is normal, not tampering
            if (locked != null && locked.sha256() != null && locked.version().equals(found)
                    && !locked.sha256().equals(fingerprint))
            {
                throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                        + "' " + found + " is no longer what " + LockFile.FILE_NAME + " recorded:"
                        + " its content changed after it was downloaded. If the change is intended, update the"
                        + " lock file; otherwise someone has tampered with the cache.");
            }
        }
        recorded.put(lockKey, new LockFile.Entry(
                declared.kind().name().toLowerCase(java.util.Locale.ROOT), found, fingerprint));
    }

    private Path requirePath(Dependency declared)
    {
        Path path = java.util.Objects.requireNonNull(declared.path());
        if (!Files.isDirectory(path))
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' points at " + path + ", which is not a directory.");
        }
        return path;
    }

    private Path requireInstalledPack(Dependency declared)
    {
        String name = java.util.Objects.requireNonNull(declared.pack());
        Optional<Path> installed = environment.installedPack(name);
        if (installed.isEmpty())
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' wants the pack '" + name + "' installed in this world, but it is not there."
                    + " Install it under the script directory, or declare it with a 'path'.",
                    Suggestions.closestTo(name, environment.installedPackNames()));
        }
        return installed.get();
    }

    private Path fetchFromRegistry(Dependency declared, LockFile.@Nullable Entry locked)
    {
        PackRegistry registry = environment.registry();
        if (registry == null)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' comes from the registry, but no registry is configured on this server."
                    + " Configure one, or declare the dependency with a 'path' or a 'pack'.");
        }
        String name = declared.targetName();
        List<Version> available = registry.availableVersions(name);
        if (available.isEmpty())
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Registry " + registry.describe()
                    + " knows no pack named '" + name + "'.");
        }
        Version best = pinned(declared, locked, available);
        if (best == null)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Registry " + registry.describe()
                    + " has '" + name + "' in versions " + available.stream().map(Version::toString).sorted().toList()
                    + ", none of which satisfies the constraint " + declared.version() + ".");
        }
        try
        {
            return registry.fetch(name, best);
        }
        catch (IOException e)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Cannot obtain '" + name + "' "
                    + best + " from " + registry.describe() + ": " + e.getMessage());
        }
    }

    /**
     * The version to take: the one the lock had chosen, if it still exists and still satisfies the
     * constraint; otherwise the highest compatible one.
     *
     * <p>A lock pointing at a version the manifest no longer accepts is not an error: it means the
     * constraint was changed on purpose, and the choice must be redone.
     */
    private @Nullable Version pinned(Dependency declared, LockFile.@Nullable Entry locked, List<Version> available)
    {
        if (locked != null && available.contains(locked.version()) && declared.version().accepts(locked.version()))
        {
            return locked.version();
        }
        return declared.version().best(available);
    }

    private static @Nullable ProjectManifest readManifest(Path root)
    {
        try
        {
            return ProjectManifest.read(root);
        }
        catch (IOException e)
        {
            return null; // no manifest: it's just a plain source directory
        }
    }

    private static void checkVersion(Dependency declared, @Nullable Version found, Path root, boolean withoutManifest)
    {
        if (declared.version().isAny())
        {
            return;
        }
        if (withoutManifest)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' requires version " + declared.version() + ", but " + root + " has no "
                    + ProjectManifest.FILE_NAME + " and therefore declares no version."
                    + " Add one, or drop the constraint.");
        }
        if (!declared.version().accepts(found))
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' (" + declared.describe() + ") is at version " + found
                    + ", which does not satisfy the constraint " + declared.version() + " declared in the manifest.");
        }
    }
}
