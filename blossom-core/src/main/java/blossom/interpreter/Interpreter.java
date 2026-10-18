package blossom.interpreter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import blossom.project.ModuleSource;
import blossom.project.ProjectLoader;
import blossom.value.Value;

/**
 * The facade of the core: given code, it returns a value.
 *
 * <p>It is the only entry point an external caller needs to know to run Blossom code without
 * Minecraft. One instance keeps its own host, so global variables and defined functions survive
 * between calls to {@link #eval(String)}, which is what a REPL, a test and the mod command line
 * need.
 */
public final class Interpreter
{
    private final BlossomHost host;

    private Interpreter(BlossomHost host)
    {
        this.host = host;
    }

    /**
     * An interpreter over an already built host.
     *
     * <p>Needed by whoever has to hold a reference to the app while its code is still running: the
     * body of a module can already register an event, and whoever registers it must be able to find
     * the app it belongs to.
     */
    public static Interpreter of(ScriptHost host)
    {
        return new Interpreter((BlossomHost) host);
    }

    /**
     * An isolated interpreter: no disk access, no importable library, only the language.
     */
    public static Interpreter standalone()
    {
        return builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Loads a project: manifest, entrypoint and every module reachable from the imports.
     *
     * <p>Modules run in dependency order, each one after everything it imports, so that when the
     * body of a module uses {@code alias.symbol} that symbol already exists. A missing module, a
     * name that does not match its path and a cyclic import were already caught by the loader,
     * before a single line ran.
     */
    public static Interpreter forProject(Path root) throws IOException
    {
        return forProject(root, UnaryOperator.identity());
    }

    /**
     * Like {@link #forProject(Path)}, but with extra built-in functions installed on every
     * expression of the project: that is how the Minecraft layer gives its API to multi file
     * projects too, and not only to single file apps.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup) throws IOException
    {
        return forProject(root, setup, Context::new);
    }

    /**
     * As above, but the caller decides the context modules run in: the game layer needs it to tell
     * scripts who loaded them, since the body of a module can already call functions that need a
     * world.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory) throws IOException
    {
        return forProject(root, setup, contextFactory, blossom.project.PackEnvironment.NONE);
    }

    /**
     * As above, knowing what surrounds the project: the other installed packs and the registry,
     * where the dependencies that are not local paths come from.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory,
            blossom.project.PackEnvironment environment) throws IOException
    {
        return forProject(root, setup, contextFactory, environment, false);
    }

    /**
     * @param refreshingLock whether to redo dependency version resolution instead of reusing the
     *                       one written in {@code blossom.lock}
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory,
            blossom.project.PackEnvironment environment, boolean refreshingLock) throws IOException
    {
        ProjectLoader.LoadedProject project = ProjectLoader.load(root, environment, refreshingLock);
        Map<String, Module> modules = new LinkedHashMap<>();
        Map<String, Map<String, String>> importAliases = new LinkedHashMap<>();
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            String name = unit.name().value();
            modules.put(name, new Module(name, unit.source().body(), !unit.name().equals(project.entrypoint())));
            importAliases.put(name, unit.importedByAlias());
        }
        Module entry = modules.get(project.entrypoint().value());
        // the maps stay ordered: dependency order is the order modules have to exist and run in,
        // and Map.copyOf would lose it
        BlossomHost host = new BlossomHost(entry, new BlossomServer(),
                Collections.unmodifiableMap(new LinkedHashMap<>(modules)),
                Collections.unmodifiableMap(new LinkedHashMap<>(importAliases)), setup);
        Interpreter interpreter = new Interpreter(host);
        Context context = contextFactory.apply(host);
        // order matters: the factory is where whoever embeds the core installs their own defaults
        // (in game, the server limits), and the app manifest overrides them
        if (project.manifest().watchdog() != null)
        {
            host.setWatchdog(project.manifest().watchdog());
        }
        host.strict = project.manifest().strict();
        // first they all exist, with their aliases installed, then they run: that is what makes
        // cyclic imports possible, where no order lets every module already see everything it
        // imports registered
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            host.preRegisterModule(unit.name().value());
        }
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            if (unit.name().equals(project.entrypoint()))
            {
                continue; // l'entrypoint gira per ultimo, e per intero
            }
            context.beginExecution(); // ogni modulo ha il suo budget: il corpo di uno non paga per l'altro
            host.importModule(context, unit.name().value());
        }
        interpreter.evalIn(context, entry.code());
        return interpreter;
    }

    /**
     * Evaluates {@code code} in the host of this interpreter, keeping the state between calls.
     */
    public Value eval(String code)
    {
        return evalIn(new Context(host), code);
    }

    /**
     * Like {@link #eval(String)}, but in the given context: that is how the game layer tells the
     * script who launched it, without the core having to know what a player is.
     *
     * @param context must belong to the host of this interpreter
     */
    public Value evalIn(Context context, String code)
    {
        return evalIn(context, code, host.watchdog());
    }

    /**
     * As above, but with execution limits decided by the caller.
     *
     * <p>It separates code running on behalf of the game, an event inside the tick where the budget
     * is tight, from code a user launched by hand and is waiting on.
     */
    public Value evalIn(Context context, String code, ExecutionBudget.Policy policy)
    {
        if (context.host != host)
        {
            throw new IllegalArgumentException("the context belongs to another host");
        }
        context.beginExecution(policy); // an execution starts here: the clock starts from here
        // a snippet can have a preamble too: without this, importing a module from the command
        // line would be impossible and the only way to use the library would be writing a file
        ModuleSource source = ModuleSource.parse(host.main == null ? "<code>" : host.main.name(), code);
        ModuleSymbols symbols = host.symbolsOf(host.main);
        source.imports().forEach((alias, declaration) -> symbols.declareAlias(alias, declaration.target().value()));
        for (blossom.project.ModuleName imported : source.importedModules())
        {
            if (!blossom.language.StdLib.isBuiltinModule(imported.value()))
            {
                host.importModule(context, imported.value());
            }
        }
        return host.prepare(host.main, source.body()).executeAndEvaluate(context, true, host.loadOverrides, null).getLeft();
    }

    /**
     * Like {@link #evalIn}, but compiled once to be run many times: the expression of
     * {@code /blossom script scan} and {@code fill}, evaluated block after block. The preamble is
     * read here, once; the returned expression keeps its tree after the first run.
     */
    public Expression compileIn(Context context, String code)
    {
        if (context.host != host)
        {
            throw new IllegalArgumentException("the context belongs to another host");
        }
        ModuleSource source = ModuleSource.parse(host.main == null ? "<code>" : host.main.name(), code);
        ModuleSymbols symbols = host.symbolsOf(host.main);
        source.imports().forEach((alias, declaration) -> symbols.declareAlias(alias, declaration.target().value()));
        for (blossom.project.ModuleName imported : source.importedModules())
        {
            if (!blossom.language.StdLib.isBuiltinModule(imported.value()))
            {
                host.importModule(context, imported.value());
            }
        }
        return host.prepare(host.main, source.body());
    }

    public ScriptHost host()
    {
        return host;
    }

    public static final class Builder
    {
        private final Map<String, Module> libraries = new HashMap<>();
        /** module name to (alias to imported module): outside a project names carry no prefix */
        private final Map<String, Map<String, String>> importAliases = new HashMap<>();
        private @Nullable Module main = null;
        private UnaryOperator<Expression> setup = UnaryOperator.identity();

        private Builder()
        {
        }

        /** The main module, if the interpreter runs one; otherwise the code runs anonymous. */
        public Builder main(Module module)
        {
            this.main = module;
            return this;
        }

        /**
         * Makes {@code module} importable under its name, reading its preamble.
         *
         * <p>The registered body is the one stripped of the preamble, so the module sees its own
         * aliases and the parser never meets declarations it cannot evaluate.
         */
        public Builder library(String name, String code)
        {
            ModuleSource source = ModuleSource.parse(name, code);
            libraries.put(name, new Module(name, source.body(), true));
            importAliases.put(name, aliasesOf(source));
            return this;
        }

        /**
         * Registers extra built-in functions on every expression of the host: the hook the
         * Minecraft layer installs its own API through.
         */
        public Builder withApi(UnaryOperator<Expression> setup)
        {
            UnaryOperator<Expression> previous = this.setup;
            this.setup = expression -> setup.apply(previous.apply(expression));
            return this;
        }

        public Interpreter build()
        {
            return new Interpreter(new BlossomHost(main, new BlossomServer(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(libraries)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(importAliases)), setup));
        }

        private static Map<String, String> aliasesOf(ModuleSource source)
        {
            Map<String, String> aliases = new HashMap<>();
            source.imports().forEach((alias, declaration) -> aliases.put(alias, declaration.target().value()));
            return Map.copyOf(aliases);
        }
    }
}
