package blossom.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import blossom.exception.ImportError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * The core's minimal host: executes code without any game environment around it.
 *
 * <p>It serves two purposes (pure JUnit tests and the benchmark) and is the base the mod derives
 * its own host from. It knows the project's modules by name, and for each one the preamble already
 * read, so it can install a module's aliases before its body runs.
 */
public class BlossomHost extends ScriptHost
{
    private final Map<String, Module> libraries;
    /**
     * Module name to (alias to global name of the imported module).
     *
     * <p>Not the preamble as-is: inside a dependency {@code import "vector" as v} points to the
     * module this host registers as {@code utils.vector}, and only whoever resolved the paths
     * knows the translation. Keeping the raw preamble here would mean redoing that resolution on
     * every access, and getting it wrong.
     */
    private final Map<String, Map<String, String>> importAliases;
    /** Applied to every newly created expression: the point where the game layer injects its API. */
    private final UnaryOperator<Expression> setup;

    public BlossomHost(@Nullable Module code, BlossomServer server, Map<String, Module> libraries,
                       Map<String, Map<String, String>> importAliases, UnaryOperator<Expression> setup)
    {
        super(code, server, Expression.LoadOverride.DEFAULT);
        this.libraries = libraries;
        this.importAliases = importAliases;
        this.setup = setup;
        if (code != null)
        {
            installAliases(code, symbolsOf(code));
        }
    }

    /**
     * Prepares an expression the way this host sees it: text source, owning module, and the
     * built-in functions of the layer above it.
     */
    public Expression prepare(@Nullable Module module, String code)
    {
        Expression expression = new Expression(code);
        expression.asAModule(module);
        expression.asATextSource();
        return setup.apply(expression);
    }

    @Override
    protected void onModuleRegistered(Module module, ModuleData data)
    {
        installAliases(module, data.symbols);
    }

    private void installAliases(Module module, ModuleSymbols symbols)
    {
        Map<String, String> aliases = importAliases.get(module.name());
        if (aliases == null)
        {
            return;
        }
        aliases.forEach(symbols::declareAlias);
    }

    @Override
    protected Module getModuleOrLibraryByName(String name)
    {
        if (blossom.language.StdLib.isBuiltinModule(name))
        {
            // A library module has no body to run: it exists only as a namespace
            return new Module(name, "null", true);
        }
        Module module = libraries.get(name);
        if (module == null)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Module '" + name + "' not found.",
                    Suggestions.closestTo(name, libraries.keySet()));
        }
        return module;
    }

    @Override
    protected void runModuleCode(Context c, Module module)
    {
        prepare(module, module.code()).executeAndEvaluate(c, true, loadOverrides, null);
    }

    /** Defensive copy of the known libraries, for whoever wants to build a related host. */
    public Map<String, Module> libraries()
    {
        return new HashMap<>(libraries);
    }
}
