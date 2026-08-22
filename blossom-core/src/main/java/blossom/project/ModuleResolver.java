package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * From a module name to a file on disk.
 *
 * <p>A resolver represents the namespace of <em>one</em> project: its source directory, plus a
 * child resolver for each dependency. The name's first segment decides where to look:
 * {@code utils.vector} looks in a dependency called {@code utils} if one exists, otherwise in the
 * project itself. There is no "try several paths" search: a name has exactly one possible origin,
 * so a module can never be shadowed by another without anyone noticing.
 *
 * <p>The children are resolvers, not plain directories, because a dependency has its own
 * dependencies in turn, with its own aliases: inside {@code utils}, {@code math.vec} means whatever
 * {@code utils}'s manifest says, not whatever ours says. Without this separation, two projects that
 * both call a library {@code math} could not coexist, which is exactly the problem that mandatory
 * aliases solve between modules, carried one level up.
 */
public final class ModuleResolver
{
    private final Path sourceRoot;
    private final Map<String, ModuleResolver> dependencies;

    private ModuleResolver(Path sourceRoot, Map<String, ModuleResolver> dependencies)
    {
        this.sourceRoot = sourceRoot;
        this.dependencies = dependencies;
    }

    static ModuleResolver of(Path sourceRoot, Map<String, ModuleResolver> dependencies)
    {
        return new ModuleResolver(sourceRoot, Map.copyOf(dependencies));
    }

    /**
     * The resolver for a project, dependencies included, taking its {@code blossom.lock} into
     * account and rewriting it if the choices changed.
     *
     * @param refreshing whether to re-resolve while ignoring what the lock had chosen
     */
    public static ModuleResolver of(Path projectRoot, ProjectManifest manifest, PackEnvironment environment,
            boolean refreshing) throws IOException
    {
        return new DependencyResolver(environment, refreshing).resolveRoot(projectRoot, manifest);
    }

    /** A resolver for a single source directory, with no project around it. */
    public static ModuleResolver ofSourceRoot(Path sourceRoot)
    {
        return new ModuleResolver(sourceRoot, Map.of());
    }

    /**
     * Where a module lives, what it's called at home, and in which namespace its imports must be read.
     *
     * @param localName the name the file declares: inside a dependency this is the name without the
     *                  dependency's prefix, because that module doesn't know it is someone else's
     *                  dependency
     * @param scope     the resolver in which <em>that</em> file's imports must be resolved
     * @param prefix    the chain of aliases crossed to get here, with a trailing dot: used to
     *                  rebuild the module's global name, the one the host registers it under
     */
    public record Resolution(Path file, ModuleName localName, ModuleResolver scope, String prefix)
    {
        /** The module's name as seen from the project's root. */
        public ModuleName globalName()
        {
            return prefix.isEmpty() ? localName : new ModuleName(prefix + localName.value());
        }
    }

    public Resolution resolve(ModuleName name)
    {
        String value = name.value();
        int dot = value.indexOf('.');
        if (dot > 0)
        {
            ModuleResolver dependency = dependencies.get(value.substring(0, dot));
            if (dependency != null)
            {
                Resolution nested = dependency.resolve(new ModuleName(value.substring(dot + 1)));
                return new Resolution(nested.file(), nested.localName(), nested.scope(),
                        value.substring(0, dot + 1) + nested.prefix());
            }
        }
        return new Resolution(name.toPath(sourceRoot), name, this, "");
    }

    /** All the modules actually present, to suggest a name when the one written doesn't exist. */
    public List<String> knownModuleNames()
    {
        List<String> names = new ArrayList<>(namesUnder(sourceRoot, ""));
        dependencies.forEach((alias, resolver) -> resolver.collectNames(alias + ".", names));
        return names;
    }

    private void collectNames(String prefix, List<String> into)
    {
        into.addAll(namesUnder(sourceRoot, prefix));
        dependencies.forEach((alias, resolver) -> resolver.collectNames(prefix + alias + ".", into));
    }

    private static List<String> namesUnder(Path root, String prefix)
    {
        if (!Files.isDirectory(root))
        {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root))
        {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(ModuleName.EXTENSION))
                    .map(path -> prefix + ModuleName.ofPath(root, path).value())
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
