package blossom.interpreter;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.ExpressionException;
import blossom.exception.ImportError;
import blossom.exception.IntegrityException;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.UndefinedSymbolError;
import blossom.value.FunctionValue;
import blossom.value.Value;
import blossom.value.ValueGuard;

import org.jspecify.annotations.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class ScriptHost
{
    /** shared by every host, and reachable from any app thread through 'rand' */
    private static final Map<Long, Random> randomizers = new ConcurrentHashMap<>();

    public static Thread mainThread = null;
    private final Map<Value, ThreadPoolExecutor> executorServices = new ConcurrentHashMap<>();
    private final Map<Value, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ScriptServer scriptServer;
    protected boolean inTermination = false;
    public boolean strict;
    public Expression.LoadOverride loadOverrides;
    public Expression.@Nullable ExpressionNode root;


    /**
     * The execution limits of this app (PIANO_LINGUAGGIO.md §9): the manifest declares them, and
     * every context that starts an execution reads them.
     *
     * <p>The core default is "no limit" on purpose: outside a server there is no tick to protect,
     * and a test measuring the language must not fail because the machine is slow. The game layer
     * installs a real one.
     */
    private volatile ExecutionBudget.Policy watchdog = ExecutionBudget.Policy.UNLIMITED;

    public ExecutionBudget.Policy watchdog()
    {
        return watchdog;
    }

    public void setWatchdog(ExecutionBudget.Policy policy)
    {
        this.watchdog = policy;
    }

    /**
     * The app profiler (PIANO_LINGUAGGIO.md §9), off unless someone is measuring.
     *
     * <p>It lives on the host and not on the context because what is worth measuring, the event
     * handlers, runs in contexts created by the game, which nobody can pass in from outside.
     */
    private volatile Profiler profiler = Profiler.OFF;

    public Profiler profiler()
    {
        return profiler;
    }

    /** Turns measurement on and returns the profiler to read it from. */
    public Profiler startProfiling()
    {
        Profiler started = new Profiler();
        this.profiler = started;
        return started;
    }

    public void stopProfiling()
    {
        profiler.stop();
        this.profiler = Profiler.OFF;
    }

    public Random getRandom(long aLong)
    {
        if (randomizers.size() > 65536)
        {
            randomizers.clear();
        }
        return randomizers.computeIfAbsent(aLong, Random::new);
    }

    public boolean resetRandom(long aLong)
    {
        return randomizers.remove(aLong) != null;
    }

    public Path resolveScriptFile(String suffix)
    {
        return scriptServer.resolveResource(suffix);
    }

    public boolean canSynchronouslyExecute()
    {
        return true;
    }

    /**
     * Symbol table of a single module of an app. Apps can run code on their own threads via
     * {@code task()}, and every thread of an app shares its host, therefore all maps here are
     * concurrent: a plain hash map can be structurally corrupted by two threads defining a
     * function or assigning a global at the same time.
     */
    public static class ModuleData
    {
        Module parent;
        public final Map<String, FunctionValue> globalFunctions = new ConcurrentHashMap<>();
        /** the module's global variables: one namespace per module, never shared with another */
        public final Map<String, LazyValue> globals = new ConcurrentHashMap<>();
        public final Map<String, ModuleData> functionImports = new ConcurrentHashMap<>(); // imported functions string to module
        public final Map<String, ModuleData> globalsImports = new ConcurrentHashMap<>(); // imported global variables string to module
        public final Map<String, ModuleData> futureImports = new ConcurrentHashMap<>(); // imports not known before used
        /** what the module declares and exports, and the aliases it sees other modules under */
        public final ModuleSymbols symbols = new ModuleSymbols();

        /**
         * Import gate. A module that is registered but still running its top level code has no
         * functions or globals yet, so threads that join an import already in flight queue here
         * instead of seeing half a module. Null means the module is ready to use.
         */
        private volatile @Nullable CountDownLatch loading = null;
        private volatile @Nullable Thread loadingThread = null;
        /**
         * Whether the module body has already run.
         *
         * <p>Distinct from being registered: with cyclic imports every module is registered before
         * the first body starts, so that every alias already points somewhere. A module registered
         * but not yet executed has an empty symbol table, and whoever tries to read a symbol from it
         * deserves to be told it is a cycle and not that the symbol does not exist.
         */
        private volatile boolean executed = false;

        public boolean isExecuted()
        {
            return executed;
        }

        public ModuleData(Module parent)
        {
            this.parent = parent;
        }

        void beginLoading()
        {
            loadingThread = Thread.currentThread();
            loading = new CountDownLatch(1);
        }

        void finishLoading()
        {
            CountDownLatch latch = loading;
            loading = null;
            loadingThread = null;
            if (latch != null)
            {
                latch.countDown(); // also runs when the module failed: waiters must never hang
            }
        }

        void awaitLoaded(String name)
        {
            CountDownLatch latch = loading;
            // already usable, or this very thread is the one loading it, which is how circular
            // imports work - those resolve their symbols lazily through futureImports
            if (latch == null || loadingThread == Thread.currentThread())
            {
                return;
            }
            try
            {
                if (!latch.await(MODULE_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Timed out waiting for module '" + name + "' to finish loading");
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Interrupted while waiting for module '" + name + "' to load");
            }
        }
    }

    private static final long MODULE_LOAD_TIMEOUT_SECONDS = 30;

    /**
     * The set of modules of an app and their symbol tables, as one immutable snapshot.
     *
     * <p>Both maps have to agree with each other - resolving an import reads a name from
     * {@link #modules} and then its table from {@link #data} - so they are published together.
     * The maps accept a {@code null} key on purpose: the appless default host has no main module.
     */
    private record ModuleRegistry(Map<String, Module> modules, Map<Module, ModuleData> data)
    {
        ModuleRegistry with(String name, Module module, ModuleData moduleData)
        {
            Map<String, Module> newModules = new HashMap<>(modules);
            Map<Module, ModuleData> newData = new HashMap<>(data);
            newModules.put(name, module);
            newData.put(module, moduleData);
            return of(newModules, newData);
        }

        /** Map.copyOf would reject the null key of the appless host, so freeze the copies instead */
        static ModuleRegistry of(Map<String, Module> modules, Map<Module, ModuleData> data)
        {
            return new ModuleRegistry(Collections.unmodifiableMap(modules), Collections.unmodifiableMap(data));
        }
    }

    /**
     * read without locking, replaced wholesale under moduleRegistryLock: modules are registered
     * rarely (only when importing) but symbols are resolved through here constantly, so readers
     * must not pay for a lock
     */
    private volatile ModuleRegistry registry = new ModuleRegistry(Map.of(), Map.of());
    /**
     * serializes registry writes, so that two threads importing the same module cannot both run
     * its code
     */
    private final Object moduleRegistryLock = new Object();
    /**
     * the one lock of this host's globals, carried by every value stored in them. One guard for all
     * of them rather than one per name, because globals alias each other freely - 'global_a =
     * global_b' shares the underlying container - and because a single lock cannot deadlock
     */
    private final ValueGuard globalsGuard = new ValueGuard();

    public String getName()
    {
        return main == null ? null : main.name();
    }

    public String getVisualName()
    {
        return main == null ? "built-in default app" : main.name();
    }

    @Nullable
    public final Module main;

    @FunctionalInterface
    public interface ErrorSnooper
    {
        List<String> apply(Expression expression, Token token, Context context, String message);
    }

    public ErrorSnooper errorSnooper = null;

    protected ScriptHost(@Nullable Module code, ScriptServer scriptServer, Expression.LoadOverride loadOverrides)
    {
        this.main = code;
        this.strict = false;
        this.loadOverrides = loadOverrides;
        this.scriptServer = scriptServer;
        ModuleData moduleData = new ModuleData(code);
        this.registry = registry.with(code == null ? null : code.name(), code, moduleData);
    }

    /**
     * Registers {@code module} without running its body.
     *
     * <p>It is the first of the two phases mutually importing modules load in: first they all exist,
     * with their aliases already installed, then they run. Without that separation the body of the
     * first module of a cycle would find the alias to the second pointing at a module not yet in the
     * registry.
     */
    public void preRegisterModule(String moduleName)
    {
        synchronized (moduleRegistryLock)
        {
            if (findModuleData(moduleName.toLowerCase(Locale.ROOT)) != null)
            {
                return;
            }
            Module module = getModuleOrLibraryByName(moduleName);
            ModuleData data = new ModuleData(module);
            registry = registry.with(module.name(), module, data);
            onModuleRegistered(module, data);
        }
    }

    public void importModule(Context c, String moduleName)
    {
        String lowerCaseName = moduleName.toLowerCase(Locale.ROOT);
        ModuleData imported = findModuleData(lowerCaseName);
        if (imported != null)
        {
            imported.awaitLoaded(lowerCaseName);
            if (!imported.executed)
            {
                // registered but never executed: the normal case of the second phase, and also
                // that of a module importing another of the same cycle before its own turn
                runIfNeeded(c, lowerCaseName, imported);
            }
            return;  // aready imported
        }
        Module module = getModuleOrLibraryByName(moduleName);
        ModuleData data;
        boolean weLoadIt;
        // registering and running are two steps on purpose: only the registration is serialized,
        // module code runs unlocked so that it may import further modules or join tasks
        synchronized (moduleRegistryLock)
        {
            ModuleData registered = findModuleData(module.name());
            weLoadIt = registered == null;  // whoever registers the module is the one that runs it
            if (weLoadIt)
            {
                data = new ModuleData(module);
                data.beginLoading();
                registry = registry.with(module.name(), module, data);
            }
            else
            {
                data = registered;  // aready imported, once again, in case some discrepancies in names?
            }
        }
        if (!weLoadIt)
        {
            data.awaitLoaded(module.name());
            return;
        }
        try
        {
            onModuleRegistered(module, data);
            runModuleCode(c, module);
            data.executed = true;
        }
        finally
        {
            data.finishLoading();
        }
    }

    /**
     * Runs the body of an already registered module, once.
     *
     * <p>Synchronized on the module and not on the registry: two threads importing the same module
     * must not run its body twice, but the body can import more in turn, and holding the registry
     * lock while it runs would lead straight to a deadlock.
     */
    private void runIfNeeded(Context c, String moduleName, ModuleData data)
    {
        synchronized (data)
        {
            if (data.executed || data.loadingThread == Thread.currentThread())
            {
                return; // already done, or the cycle coming back on itself: carry on
            }
            data.beginLoading();
            try
            {
                runModuleCode(c, data.parent);
                data.executed = true;
            }
            finally
            {
                data.finishLoading();
            }
        }
    }

    /**
     * Called as soon as a module enters the registry and before its body runs.
     *
     * <p>It is the window to install the aliases of its imports: the body can use them from the
     * first line, so they have to be there already.
     */
    protected void onModuleRegistered(Module module, ModuleData data)
    {
    }

    private @Nullable ModuleData findModuleData(String moduleName)
    {
        ModuleRegistry currentRegistry = registry;
        Module module = currentRegistry.modules().get(moduleName);
        return module == null ? null : currentRegistry.data().get(module);
    }

    protected abstract Module getModuleOrLibraryByName(String name); // this should be shell out in the executor

    protected abstract void runModuleCode(Context c, Module module); // this should be shell out in the executor

    @Nullable
    public FunctionValue getFunction(String name)
    {
        return getFunction(main, name);
    }

    /**
     * The symbol table of {@code module}: declarations, exports and aliases.
     */
    public ModuleSymbols symbolsOf(Module module)
    {
        return getModuleData(module).symbols;
    }

    /** The character separating alias from symbol in a qualified access, e.g. {@code vec.add}. */
    public static final char QUALIFIER = '.';

    public static boolean isQualified(String name)
    {
        return name.indexOf(QUALIFIER) >= 0;
    }

    /**
     * Resolves {@code alias.symbol} in the module that imported it.
     *
     * <p>Fails at load time rather than at run time whenever it can: an unknown alias, a module that
     * was not imported, a symbol that does not exist or is not public are all errors with a position
     * and a suggestion, never a null that propagates.
     *
     * @param wantFunction whether to look among functions ({@code true}) or among module bindings
     */
    private ModuleData resolveQualifier(Module module, String qualifiedName, boolean wantFunction)
    {
        int separator = qualifiedName.lastIndexOf(QUALIFIER);
        String alias = qualifiedName.substring(0, separator);
        String symbol = qualifiedName.substring(separator + 1);
        ModuleData local = getModuleData(module);
        String targetName = local.symbols.moduleOfAlias(alias);
        if (targetName == null)
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Alias '" + alias + "' is not imported in this module.",
                    Suggestions.closestTo(alias, local.symbols.aliases()));
        }
        ModuleData target = findModuleData(targetName);
        if (target == null)
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Module '" + targetName + "', imported as '" + alias + "', is not loaded.");
        }
        if (!target.symbols.isExported(symbol) && !target.isExecuted())
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Module '" + targetName + "', imported as '" + alias + "', has not finished loading yet:"
                            + " '" + symbol + "' does not exist yet. This happens with circular imports, where one"
                            + " of the two modules has to run first. Move the use inside a function, which is"
                            + " evaluated when you call it and not while the module loads.");
        }
        if (!target.symbols.isExported(symbol))
        {
            boolean exists = wantFunction
                    ? target.globalFunctions.containsKey(symbol)
                    : target.symbols.isBinding(symbol);
            if (exists)
            {
                throw new ImportError(SourcePosition.UNKNOWN,
                        "Symbol '" + symbol + "' exists in module '" + targetName
                                + "' but is not public: add 'pub' to its declaration to export it.");
            }
            throw new UndefinedSymbolError(SourcePosition.UNKNOWN,
                    "Symbol '" + symbol + "' not found in module '" + targetName + "'.",
                    Suggestions.closestTo(symbol, target.symbols.exportedSymbols()));
        }
        return target;
    }

    /** The public function named by a qualified access {@code alias.function}. */
    public FunctionValue getQualifiedFunction(Module module, String qualifiedName)
    {
        ModuleData target = resolveQualifier(module, qualifiedName, true);
        String symbol = qualifiedName.substring(qualifiedName.lastIndexOf(QUALIFIER) + 1);
        FunctionValue function = target.globalFunctions.get(symbol);
        if (function == null)
        {
            throw new UndefinedSymbolError(SourcePosition.UNKNOWN,
                    "'" + qualifiedName + "' is a module binding, not a function: use call(...) if it holds a function.");
        }
        return function;
    }

    /** The public binding named by a qualified access {@code alias.name}. */
    public @Nullable LazyValue getQualifiedGlobal(Module module, String qualifiedName)
    {
        ModuleData target = resolveQualifier(module, qualifiedName, false);
        String symbol = qualifiedName.substring(qualifiedName.lastIndexOf(QUALIFIER) + 1);
        return target.globals.get(symbol);
    }

    public FunctionValue getAssertFunction(Module module, String name)
    {
        if (isQualified(name))
        {
            return getQualifiedFunction(module, name);
        }
        FunctionValue ret = getFunction(module, name);
        if (ret == null)
        {
            // the name may be a library one written without an import: the most common mistake,
            // and saying which line to add costs less than leaving "undefined" to someone who has
            // just read 'map' in the documentation
            String home = blossom.language.StdLib.homeModuleOf(name);
            if (home != null)
            {
                throw new UndefinedSymbolError(SourcePosition.UNKNOWN,
                        "'" + name + "' lives in module '" + home + "': add import \"" + home
                                + "\" as " + home.substring(home.lastIndexOf('.') + 1) + "; at the top of the file.");
            }
            if (module == main)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Function '" + name + "' is not defined yet");
            }
            else
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Function '" + name + "' is not defined nor visible by its name in the imported module '" + module.name() + "'");
            }
        }
        return ret;
    }

    @Nullable
    public FunctionValue getFunction(Module module, String name)
    {
        ModuleData local = getModuleData(module);
        FunctionValue ret = local.globalFunctions.get(name); // most uses would be from local scope anyways
        if (ret != null)
        {
            return ret;
        }
        ModuleData target = local.functionImports.get(name);
        if (target != null)
        {
            ret = target.globalFunctions.get(name);
            if (ret != null)
            {
                return ret;
            }
        }
        // not in local scope - will need to travel over import links
        target = local.futureImports.get(name);
        if (target == null)
        {
            return null;
        }
        target = findModuleDataFromFunctionImports(name, target, 0);
        if (target == null)
        {
            return null;
        }
        local.futureImports.remove(name);
        local.functionImports.put(name, target);
        return target.globalFunctions.get(name);
    }

    @Nullable
    private ModuleData findModuleDataFromFunctionImports(String name, ModuleData source, int ttl)
    {
        if (ttl > 64)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Cannot import " + name + ", either your imports are too deep or too loopy");
        }
        if (source.globalFunctions.containsKey(name))
        {
            return source;
        }
        if (source.functionImports.containsKey(name))
        {
            return findModuleDataFromFunctionImports(name, source.functionImports.get(name), ttl + 1);
        }
        if (source.futureImports.containsKey(name))
        {
            return findModuleDataFromFunctionImports(name, source.futureImports.get(name), ttl + 1);
        }
        return null;
    }

    public LazyValue getGlobalVariable(String name)
    {
        return getGlobalVariable(main, name);
    }

    public LazyValue getGlobalVariable(Module module, String name)
    {
        ModuleData local = getModuleData(module);
        // an app with shared globals finds everything right here, and never walks the import links
        LazyValue ret = local.globals.get(name); // most uses would be from local scope anyways
        if (ret != null)
        {
            return ret;
        }
        ModuleData target = local.globalsImports.get(name);
        if (target != null)
        {
            ret = target.globals.get(name);
            if (ret != null)
            {
                return ret;
            }
        }
        // not in local scope - will need to travel over import links
        target = local.futureImports.get(name);
        if (target == null)
        {
            return null;
        }
        target = findModuleDataFromGlobalImports(name, target, 0);
        if (target == null)
        {
            return null;
        }
        local.futureImports.remove(name);
        local.globalsImports.put(name, target);
        return target.globals.get(name);
    }

    private ModuleData findModuleDataFromGlobalImports(String name, ModuleData source, int ttl)
    {
        if (ttl > 64)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "module", "Cannot import " + name + ", either your imports are too deep or too loopy");
        }
        if (source.globals.containsKey(name))
        {
            return source;
        }
        if (source.globalsImports.containsKey(name))
        {
            return findModuleDataFromGlobalImports(name, source.globalsImports.get(name), ttl + 1);
        }
        if (source.futureImports.containsKey(name))
        {
            return findModuleDataFromGlobalImports(name, source.futureImports.get(name), ttl + 1);
        }
        return null;
    }

    public void delFunctionWithPrefix(Module module, String prefix)
    {
        ModuleData data = getModuleData(module);
        data.globalFunctions.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        data.functionImports.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    public void delFunction(Module module, String funName)
    {
        ModuleData data = getModuleData(module);
        data.globalFunctions.remove(funName);
        data.functionImports.remove(funName);
    }

    public void delGlobalVariableWithPrefix(Module module, String prefix)
    {
        ModuleData data = getModuleData(module);
        data.globals.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        data.globalsImports.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    public void delGlobalVariable(Module module, String varName)
    {
        ModuleData data = getModuleData(module);
        data.globals.remove(varName);
        data.globalsImports.remove(varName);
    }

    private ModuleData getModuleData(Module module)
    {
        ModuleData data = registry.data().get(module);
        if (data == null)
        {
            throw new IntegrityException("Module structure changed for the app. Did you reload the app with tasks running?");
        }
        return data;
    }

    public void addUserDefinedFunction(Context ctx, Module module, String name, FunctionValue fun)
    {
        getModuleData(module).globalFunctions.put(name, fun);
    }

    /**
     * Stores a global variable, marking its value as shared so that mutating the value itself -
     * appending to a list, putting into a map - is protected too.
     */
    public void setGlobalVariable(Module module, String name, LazyValue lv)
    {
        // a global only ever holds an already computed value, including the placeholder of one that
        // was merely mentioned (see Expression.uninitializedVariable), so this never runs user code
        ValueGuard.attach(lv.evalValue(null), globalsGuard);
        getModuleData(module).globals.put(name, lv);
    }

    /**
     * Returns the global variable {@code name}, creating it from {@code fallback} if no module of
     * this app has it yet.
     *
     * <p>The creation is atomic, so a thread merely mentioning an undefined global cannot overwrite
     * the value another thread is assigning to it at the same time.
     */
    public LazyValue getOrCreateGlobalVariable(Module module, String name, Supplier<LazyValue> fallback)
    {
        LazyValue existing = getGlobalVariable(module, name);
        if (existing != null)
        {
            return existing;
        }
        // the mapping function only builds a value, it never touches the map again, so it is safe
        // to run under the map's own entry lock
        return getModuleData(module).globals.computeIfAbsent(name, key -> fallback.get());
    }

    /**
     * Runs {@code action} while holding this app's guard for its global variables, making a
     * read-modify-write of a global atomic against the app's other threads.
     *
     * <p>This is the same guard that every value stored in a global carries, so an update of a
     * variable and a mutation of the container it holds exclude each other. The guard is reentrant,
     * so nested updates on one thread cannot self deadlock. Note that {@code action} must not
     * evaluate arbitrary app code, which could try to take the guard of another app.
     */
    public <T> T withGlobalsLocked(Supplier<T> action)
    {
        return globalsGuard.call(action);
    }

    public Stream<String> globalVariableNames(Module module, Predicate<String> predicate)
    {
        return Stream.concat(Stream.concat(
                getModuleData(module).globals.keySet().stream(),
                getModuleData(module).globalsImports.keySet().stream()
        ), getModuleData(module).futureImports.keySet().stream().filter(Expression::isGlobalVariableName)).filter(predicate);
    }

    public Stream<String> globalFunctionNames(Module module, Predicate<String> predicate)
    {
        return Stream.concat(Stream.concat(
                getModuleData(module).globalFunctions.keySet().stream(),
                getModuleData(module).functionImports.keySet().stream()
        ), getModuleData(module).futureImports.keySet().stream().filter(s -> !Expression.isGlobalVariableName(s))).filter(predicate);
    }

    public synchronized void handleExpressionException(String msg, ExpressionException exc)
    {
        System.out.println(msg + ": " + exc);
    }

    /**
     * A named lock for {@code synchronize()}. Fair, so that tasks queueing on a busy critical
     * section are served in arrival order instead of one of them starving.
     */
    public ReentrantLock getLock(Value name)
    {
        return locks.computeIfAbsent(name, n -> new ReentrantLock(true));
    }

    public ThreadPoolExecutor getExecutor(Value pool)
    {
        if (inTermination)
        {
            return null;
        }
        return executorServices.computeIfAbsent(pool, v -> (ThreadPoolExecutor) Executors.newCachedThreadPool());
    }

    public int taskCount()
    {
        return executorServices.values().stream().map(ThreadPoolExecutor::getActiveCount).reduce(0, Integer::sum);
    }

    public int taskCount(Value pool)
    {
        return executorServices.containsKey(pool) ? executorServices.get(pool).getActiveCount() : 0;
    }

    public void onClose()
    {
        inTermination = true;
        executorServices.values().forEach(ThreadPoolExecutor::shutdown);
        if (taskCount() > 0)
        {
            executorServices.values().forEach(e -> {
                ExecutorService stopper = Executors.newSingleThreadExecutor();
                stopper.submit(() -> {
                    try
                    {
                        // Wait a while for existing tasks to terminate
                        if (!e.awaitTermination(1500, TimeUnit.MILLISECONDS))
                        {
                            e.shutdownNow(); // Cancel currently executing tasks
                            // Wait a while for tasks to respond to being cancelled
                            if (!e.awaitTermination(1500, TimeUnit.MILLISECONDS))
                            {
                                Blossom.LOG.error("Failed to stop app's thread");
                            }
                        }
                    }
                    catch (InterruptedException ie)
                    {
                        // (Re-)Cancel if current thread also interrupted
                        e.shutdownNow();
                        // Preserve interrupt status
                        Thread.currentThread().interrupt();
                    }
                    stopper.shutdown();
                    stopper.shutdownNow();
                });
            });
        }
    }

    public ScriptServer scriptServer()
    {
        return scriptServer;
    }
}
