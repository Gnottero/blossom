package blossom.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import blossom.exception.ImportError;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Reads a project from disk: manifest, dependencies, entrypoint, and every module reachable from the
 * imports.
 *
 * <p>It does all of it before a single line runs, which is the point of §4 of the plan: a missing
 * module, a declared name that does not match its path, a wrong API level and a dependency on an
 * incompatible version are load errors, not accidents surfacing three weeks later because that
 * branch was rarely taken.
 *
 * <p>Cyclic imports on the other hand <b>are allowed</b>: every module is registered before the
 * first body runs, and execution order is the order the recursion unwinds in. What stays forbidden
 * is <em>reading</em>, while a module loads, a symbol of a module of the same cycle that has not run
 * yet: there the error says so, and the fix is to move the use inside a function.
 */
public final class ProjectLoader
{
    private ProjectLoader()
    {
    }

    /** The result of loading: the modules in dependency order, the first imported one first. */
    public record LoadedProject(ProjectManifest manifest, ModuleName entrypoint, Map<ModuleName, ModuleUnit> modules)
    {
        /** The modules in load order: a module appears after everything it imports. */
        public List<ModuleUnit> inDependencyOrder()
        {
            return new ArrayList<>(modules.values());
        }
    }

    /**
     * A module that has been read, with the preamble already split from the body.
     *
     * @param name           the global name, that is the one the host registers the module under
     * @param importedByAlias alias declared in the preamble to global name of the imported module.
     *                        The preamble alone is not enough: inside a dependency
     *                        {@code import "vector"} points to the module that from outside is
     *                        called {@code utils.vector}, and only whoever resolved the path knows
     *                        the translation
     */
    public record ModuleUnit(ModuleName name, Path file, ModuleSource source, Map<String, String> importedByAlias)
    {
    }

    public static LoadedProject load(Path root) throws IOException
    {
        return load(root, PackEnvironment.NONE);
    }

    /**
     * As above, but knowing what surrounds it: the other installed packs and the registry, where
     * non path dependencies are taken from.
     */
    public static LoadedProject load(Path root, PackEnvironment environment) throws IOException
    {
        return load(root, environment, false);
    }

    /**
     * @param refreshing whether to resolve dependencies again, ignoring what {@code blossom.lock}
     *                   picked last time
     */
    public static LoadedProject load(Path root, PackEnvironment environment, boolean refreshing) throws IOException
    {
        ProjectManifest manifest = ProjectManifest.read(root);
        SourcePosition manifestPosition = new SourcePosition(root.resolve(ProjectManifest.FILE_NAME).toString(), 1, 1);
        // before anything else: if the app targets another API, reading its modules is pointless
        manifest.apiLevel().requireSupportedBy(ApiLevel.CURRENT, manifest.name(), manifestPosition);

        ModuleResolver resolver = ModuleResolver.of(root, manifest, environment, refreshing);
        Path entryFile = manifest.entrypoint();
        if (!Files.isRegularFile(entryFile))
        {
            throw new IOException("The entrypoint declared in " + ProjectManifest.FILE_NAME + " does not exist: " + entryFile);
        }
        ModuleName entrypoint = ModuleName.ofPath(manifest.sourceRoot(root), entryFile);

        Map<ModuleName, ModuleUnit> loaded = new LinkedHashMap<>();
        loadInto(entrypoint, resolver, "", loaded, new ArrayDeque<>(), new LinkedHashSet<>());
        // NOT Map.copyOf: that loses insertion order, and order here is the contract, since a
        // module has to run after the ones it imports, otherwise its body finds the other's symbols
        // not yet registered
        return new LoadedProject(manifest, entrypoint, Collections.unmodifiableMap(new LinkedHashMap<>(loaded)));
    }

    /**
     * Loads {@code name} and, before it, everything it imports.
     *
     * @param name     the module name in the namespace of {@code scope}
     * @param scope    the resolver of the project the module belongs to
     * @param prefix   the alias chain leading from the root to {@code scope}, trailing dot included
     * @param chain    the import chain that led here, used to describe the cycle
     * @param onPath   the same modules, to recognise the cycle in constant time
     */
    private static void loadInto(
            ModuleName name,
            ModuleResolver scope,
            String prefix,
            Map<ModuleName, ModuleUnit> loaded,
            Deque<ModuleName> chain,
            Set<ModuleName> onPath)
    {
        if (blossom.language.StdLib.isBuiltinModule(name.value()))
        {
            return; // i moduli core.* non stanno su disco: sono il linguaggio
        }
        ModuleResolver.Resolution resolution = scope.resolve(name);
        ModuleName globalName = new ModuleName(prefix + resolution.prefix() + resolution.localName().value());
        if (loaded.containsKey(globalName))
        {
            return;
        }
        if (!onPath.add(globalName))
        {
            // cycle: someone higher up the chain is already loading this module, and they will
            // insert it when done. It is not an error, see DECISIONI.md D-052, but one of the two
            // has to run first, and the order is the one the recursion unwinds in: the innermost of
            // the cycle first
            return;
        }
        chain.addLast(globalName);

        ModuleUnit unit = read(globalName, resolution, scope);
        // the imports of this file are read in its own namespace and not in ours: inside a
        // dependency, 'math.vec' means whatever that dependency's manifest says
        String nestedPrefix = prefix + resolution.prefix();
        Map<String, String> importedByAlias = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleSource.ImportDeclaration> entry : unit.source().imports().entrySet())
        {
            ModuleName target = entry.getValue().target();
            importedByAlias.put(entry.getKey(), globalNameOf(target, resolution.scope(), nestedPrefix));
            loadInto(target, resolution.scope(), nestedPrefix, loaded, chain, onPath);
        }
        unit = new ModuleUnit(unit.name(), unit.file(), unit.source(), Map.copyOf(importedByAlias));

        chain.removeLast();
        onPath.remove(globalName);
        loaded.put(globalName, unit);
    }

    /** What the module {@code name} points to inside {@code scope} is called, seen from the root. */
    private static String globalNameOf(ModuleName name, ModuleResolver scope, String prefix)
    {
        if (blossom.language.StdLib.isBuiltinModule(name.value()))
        {
            return name.value(); // i core.* hanno lo stesso nome ovunque: non appartengono a un progetto
        }
        ModuleResolver.Resolution resolution = scope.resolve(name);
        return prefix + resolution.prefix() + resolution.localName().value();
    }

    private static ModuleUnit read(ModuleName globalName, ModuleResolver.Resolution resolution, ModuleResolver scope)
    {
        Path file = resolution.file();
        if (!Files.isRegularFile(file))
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Module '" + globalName + "' not found.",
                    Suggestions.closestTo(globalName.value(), scope.knownModuleNames()));
        }
        String code;
        try
        {
            code = Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Cannot read module '" + globalName + "': " + e.getMessage());
        }
        ModuleSource source = ModuleSource.parse(file.toString(), code);
        ModuleName declared = source.declaredName();
        if (declared == null)
        {
            throw new ParseError(new SourcePosition(file.toString(), 1, 1),
                    "Missing the 'module " + resolution.localName() + ";' declaration at the top of the file.");
        }
        if (!declared.equals(resolution.localName()))
        {
            throw new ParseError(new SourcePosition(file.toString(), 1, 1),
                    "The file declares 'module " + declared + ";' but its path corresponds to module '" + resolution.localName()
                            + "'. The name must match the file's position inside the source directory.");
        }
        return new ModuleUnit(globalName, file, source, Map.of());
    }
}
