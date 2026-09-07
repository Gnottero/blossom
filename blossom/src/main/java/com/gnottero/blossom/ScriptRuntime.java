package com.gnottero.blossom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import blossom.interpreter.Module;
import blossom.interpreter.ScriptHost;
import blossom.value.Value;

/**
 * The scripts loaded on a server, and the directory they come from.
 *
 * <p>An app is a file under {@code <world>/blossom/}, loaded in its own {@link Interpreter}: two
 * apps share nothing, which is the starting point of the module system.
 */
public class ScriptRuntime
{
    /** the same logger as the mod: core classes look for it here */
    public static final org.slf4j.Logger LOG = BlossomMod.LOG;

    /** where the scripts of a world live */
    public static final String SCRIPT_DIRECTORY = "blossom";
    /** an app in a single file */
    private static final String SCRIPT_EXTENSION = blossom.project.ModuleName.EXTENSION;
    /** a library in a single file: it is imported, not loaded */
    private static final String LIBRARY_EXTENSION = blossom.project.ModuleName.LIBRARY_EXTENSION;

    /**
     * The state of the tick forced by {@code game_tick()}: when the current tick started and how
     * deep the recursion is nested. They live here and not in a local variable because a forced
     * tick can run code that forces another one.
     */
    public long tickStart = 0L;
    public int tickDepth = 0;
    /** raised when the server is shutting down: running scripts have to stop */
    public volatile boolean stopAll = false;

    /**
     * The execution limits applied to apps that declare none of their own
     * (PIANO_LINGUAGGIO.md §9). This is the watchdog: without it, a script looping inside an event
     * stops the tick and the server with it.
     */
    public volatile blossom.interpreter.ExecutionBudget.Policy defaultWatchdog =
            blossom.interpreter.ExecutionBudget.Policy.DEFAULT;

    /** the calls deferred to a future tick by {@code schedule} */
    public final com.gnottero.blossom.event.Scheduler scheduler = new com.gnottero.blossom.event.Scheduler();

    private final MinecraftServer server;
    private final Map<String, Interpreter> apps = new ConcurrentHashMap<>();

    public ScriptRuntime(MinecraftServer server)
    {
        this.server = server;
        ScriptHost.mainThread = Thread.currentThread();
        Path world = server.getWorldPath(LevelResource.ROOT);
        this.config = ServerConfig.read(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(),
                world,
                world.resolve(SCRIPT_DIRECTORY));
        this.registry = config.registry();
        this.defaultWatchdog = config.watchdog();
        createWritableRoots();
    }

    /**
     * Creates the directories of the writable roots of {@code core.io}.
     *
     * <p>A root that does not exist is not "empty": the symlink check has to resolve the real path
     * of the root, and that fails on a missing directory. Nobody creates the default roots,
     * {@code data} and {@code shared}, before the first start, so they are created here.
     */
    private void createWritableRoots()
    {
        config.ioRoots().paths().forEach((name, directory) ->
        {
            if (!config.ioRoots().isWritable(name))
            {
                return;
            }
            try
            {
                Files.createDirectories(directory);
            }
            catch (IOException e)
            {
                LOG.warn("Cannot create io root '{}' at {}: {}", name, directory, e.toString());
            }
        });
    }

    /**
     * The registry to fetch dependencies that are not already on disk from, or {@code null} when
     * none is configured on this server.
     *
     * <p>Off by default on purpose: it is the only part of the system that can talk to the network
     * and run code on a server that nobody copied there by hand. Turning it on is an administrator
     * decision, not a default.
     */
    public volatile blossom.project.@org.jspecify.annotations.Nullable PackRegistry registry = null;

    /** the server configuration: registry, {@code core.io} and {@code core.http} boundaries, watchdog */
    private final ServerConfig config;

    /** The world directories {@code core.io} can reach on this server. */
    public blossom.language.IoRoots ioRoots()
    {
        return config.ioRoots();
    }

    /** The hosts {@code core.http} can talk to on this server. */
    public blossom.language.HttpAccess httpAccess()
    {
        return config.httpAccess();
    }

    /** What surrounds a pack: the other packs of the world and, if configured, the registry. */
    public blossom.project.PackEnvironment packEnvironment()
    {
        return new WorldPacks(scriptDirectory(), registry);
    }

    /** The server this runtime runs on. */
    public MinecraftServer server()
    {
        return server;
    }

    /**
     * The script directory, created on first request.
     *
     * <p>It is {@code <world>/blossom/}, unless the server configuration declares a {@code root} of
     * its own: in that case that one wins (see {@link ServerConfig}).
     */
    public Path scriptDirectory()
    {
        Path directory = config.scriptRoot();
        try
        {
            Files.createDirectories(directory);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Cannot create " + directory, e);
        }
        return directory;
    }

    /**
     * Evaluates code on the fly, without loading it as an app: every call starts from a clean
     * interpreter, because no state is worth keeping between two different commands.
     */
    public Value runOneShot(CommandSourceStack source, String code)
    {
        Interpreter interpreter = newInterpreter(null);
        return evaluate(interpreter, source, code);
    }

    /**
     * Loads (or reloads) app {@code name} from file {@code <world>/blossom/<name>.bs}, running its
     * il codice a livello di modulo.
     */
    public Value load(CommandSourceStack source, String name) throws IOException
    {
        String appName = name.toLowerCase(Locale.ROOT);
        Path pack = scriptDirectory().resolve(appName);
        if (Files.isRegularFile(pack.resolve(blossom.project.ProjectManifest.FILE_NAME)))
        {
            return loadPack(source, appName, pack, false);
        }
        Path file = scriptDirectory().resolve(appName + SCRIPT_EXTENSION);
        if (!Files.isRegularFile(file))
        {
            throw new IOException("No app named '" + name + "' in " + scriptDirectory()
                    + ": it needs a file '" + appName + SCRIPT_EXTENSION + "' or a directory '" + appName
                    + "' containing " + blossom.project.ProjectManifest.FILE_NAME + ".");
        }
        Module module = Module.fromPath(file);
        boolean reloading = fireReloading(module.name());
        // a reload starts clean: handlers and tasks of the previous version must not outlive their
        // app
        com.gnottero.blossom.event.EventBus.unregisterApp(module.name());
        scheduler.unregisterApp(module.name());
        Interpreter interpreter = newInterpreter(module);
        apps.put(module.name(), interpreter);
        try
        {
            Value result = evaluate(interpreter, source, module.code());
            fireReloaded(module.name(), reloading);
            return result;
        }
        catch (RuntimeException e)
        {
            apps.remove(module.name());
            com.gnottero.blossom.event.EventBus.unregisterApp(module.name());
            throw e;
        }
    }

    /**
     * Fires {@code app_reloading} if that app is already loaded, before tearing it down.
     *
     * <p>It has to be called while the handlers of the old version are still there: that is the only
     * moment the app about to disappear can save anything. A first load is not a reload and fires
     * nothing, because there is no previous version to talk about.
     *
     * @return whether it really was a reload, that is whether {@code app_reloaded} will fire too
     */
    private boolean fireReloading(String appName)
    {
        if (!apps.containsKey(appName))
        {
            return false;
        }
        com.gnottero.blossom.event.Event.APP_RELOADING.fireOnServer(server,
                () -> List.of(new blossom.value.StringValue(appName)));
        return true;
    }

    /** Fires {@code app_reloaded} after the new code has run, if it was a reload. */
    private void fireReloaded(String appName, boolean reloading)
    {
        if (reloading)
        {
            com.gnottero.blossom.event.Event.APP_RELOADED.fireOnServer(server,
                    () -> List.of(new blossom.value.StringValue(appName)));
        }
    }

    /**
     * Like {@link #load}, but redoing the dependency version resolution instead of reusing the one
     * written in {@code blossom.lock}.
     */
    public Value update(CommandSourceStack source, String name) throws IOException
    {
        String appName = name.toLowerCase(Locale.ROOT);
        Path pack = scriptDirectory().resolve(appName);
        if (!Files.isRegularFile(pack.resolve(blossom.project.ProjectManifest.FILE_NAME)))
        {
            throw new IOException("'" + name + "' is not a pack: only a project with a "
                    + blossom.project.ProjectManifest.FILE_NAME + " has dependencies.");
        }
        return loadPack(source, appName, pack, true);
    }

    /**
     * Loads a pack: a directory holding {@code blossom.json}, its {@code src/} and every module the
     * entrypoint imports.
     *
     * <p>A missing manifest, a missing module, a name that does not match its path and a cyclic
     * import are load errors: the app either enters whole or not at all.
     */
    private Value loadPack(CommandSourceStack source, String appName, Path root, boolean refreshLock) throws IOException
    {
        boolean reloading = fireReloading(appName);
        com.gnottero.blossom.event.EventBus.unregisterApp(appName);
        scheduler.unregisterApp(appName);
        try
        {
            // the app enters the list before running: module level code can already register
            // events, and whoever registers them has to find their own app.
            //
            // It goes in with 'put' and not 'putIfAbsent': on a reload the entry is already there,
            // and it is the old app's. With 'putIfAbsent' the event.on(...) calls of the preamble
            // would bind their handlers to the previous interpreter, while the registered function
            // comes from the new modules: on the first event the old host does not recognise the
            // module and the call dies with "Module structure changed for the app".
            Interpreter interpreter = Interpreter.forProject(root,
                    expression -> BlossomApi.apply(expression, appName),
                    host ->
                    {
                        apps.put(appName, Interpreter.of(host));
                        // the server defaults; if the manifest declares its own, it overrides them right after
                        host.setWatchdog(defaultWatchdog);
                        applyAccess(host);
                        return new ScriptContext(host, source);
                    },
                    packEnvironment(), refreshLock);
            apps.put(appName, interpreter);
            fireReloaded(appName, reloading);
            return new blossom.value.StringValue(appName);
        }
        catch (RuntimeException e)
        {
            apps.remove(appName);
            com.gnottero.blossom.event.EventBus.unregisterApp(appName);
            throw e;
        }
    }

    /**
     * Loads the packs whose manifest says {@code "autoload": true}.
     *
     * <p>Called as soon as the world is open and before {@code server_starts} fires: an app that
     * starts on its own must be able to listen to that event too, which would otherwise be gone by
     * the time it arrives.
     *
     * <p>A pack that fails to load does not stop the others: the error goes to the log and to chat
     * like any other app error, and the rest of the world starts anyway.
     *
     * @return the names of the loaded apps
     */
    public List<String> loadAutoloadApps(CommandSourceStack source)
    {
        List<String> loaded = new ArrayList<>();
        for (String name : availableApps())
        {
            Path pack = scriptDirectory().resolve(name);
            if (!Files.isRegularFile(pack.resolve(blossom.project.ProjectManifest.FILE_NAME)))
            {
                // a single file app has no manifest, so it has no way to ask for autoload
                continue;
            }
            try
            {
                if (!blossom.project.ProjectManifest.read(pack).autoload())
                {
                    continue;
                }
            }
            catch (IOException | RuntimeException e)
            {
                com.gnottero.blossom.util.AppErrors.report(name, "reading " + blossom.project.ProjectManifest.FILE_NAME,
                        e.getMessage() == null ? e.toString() : e.getMessage());
                continue;
            }
            try
            {
                loadPack(source, name, pack, false);
                loaded.add(name);
                LOG.info("Autoloaded app '{}'", name);
            }
            catch (IOException | RuntimeException e)
            {
                com.gnottero.blossom.util.AppErrors.report(name, "autoload", e);
            }
        }
        return loaded;
    }

    /** Runs {@code code} inside the loaded app {@code name}, seeing its functions and globals. */
    public Value runIn(CommandSourceStack source, String name, String code)
    {
        Interpreter interpreter = apps.get(name.toLowerCase(Locale.ROOT));
        if (interpreter == null)
        {
            throw new IllegalArgumentException("App '" + name + "' is not loaded");
        }
        return evaluate(interpreter, source, code);
    }

    // --- profiling (PIANO_LINGUAGGIO.md §9) ---

    /**
     * Runs {@code code} measuring where the time goes, function by function.
     *
     * @param name the app to run in, or {@code null} for a clean interpreter
     */
    public blossom.interpreter.Profiler profileRun(CommandSourceStack source, @org.jspecify.annotations.Nullable String name, String code)
    {
        Interpreter interpreter;
        if (name == null)
        {
            interpreter = newInterpreter(null);
        }
        else
        {
            interpreter = apps.get(name.toLowerCase(Locale.ROOT));
            if (interpreter == null)
            {
                throw new IllegalArgumentException("App '" + name + "' is not loaded");
            }
        }
        ScriptHost host = interpreter.host();
        blossom.interpreter.Profiler profiler = host.startProfiling();
        try
        {
            evaluate(interpreter, source, code);
        }
        finally
        {
            host.stopProfiling();
        }
        return profiler;
    }

    /**
     * Turns measurement on for an app already loaded: from here on it also counts what the app does
     * on its own, that is its event handlers, which are the reason this command exists.
     */
    public blossom.interpreter.Profiler startProfiling(String name)
    {
        return requireApp(name).host().startProfiling();
    }

    /** Closes the measurement opened by {@link #startProfiling} and returns what it saw. */
    public blossom.interpreter.Profiler stopProfiling(String name)
    {
        ScriptHost host = requireApp(name).host();
        blossom.interpreter.Profiler profiler = host.profiler();
        if (!profiler.isRecording())
        {
            throw new IllegalArgumentException("No profiling in progress for app '" + name + "'");
        }
        host.stopProfiling();
        return profiler;
    }

    private Interpreter requireApp(String name)
    {
        Interpreter interpreter = apps.get(name.toLowerCase(Locale.ROOT));
        if (interpreter == null)
        {
            throw new IllegalArgumentException("App '" + name + "' is not loaded");
        }
        return interpreter;
    }

    public boolean unload(String name)
    {
        com.gnottero.blossom.event.EventBus.unregisterApp(name.toLowerCase(Locale.ROOT));
        scheduler.unregisterApp(name.toLowerCase(Locale.ROOT));
        return apps.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public List<String> loadedApps()
    {
        return new ArrayList<>(apps.keySet());
    }

    /**
     * The names of the apps on disk, loaded or not: both single files {@code <name>.bs} and packs,
     * that is directories holding a {@code blossom.json}.
     *
     * <p>The directory is reread on every call on purpose: a pack copied into the world while the
     * server runs has to be loadable right away, with no restart.
     */
    public List<String> availableApps()
    {
        try (Stream<Path> entries = Files.list(scriptDirectory()))
        {
            return entries
                    .map(this::appNameOf)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }

    /** @return the name of the app living in {@code entry}, or {@code null} if it is not an app */
    private @org.jspecify.annotations.Nullable String appNameOf(Path entry)
    {
        String fileName = entry.getFileName().toString();
        if (Files.isDirectory(entry))
        {
            return Files.isRegularFile(entry.resolve(blossom.project.ProjectManifest.FILE_NAME)) ? fileName : null;
        }
        // libraries (.bsl) are imported, not loaded: they are not apps
        if (fileName.endsWith(LIBRARY_EXTENSION) || !fileName.endsWith(SCRIPT_EXTENSION))
        {
            return null;
        }
        return fileName.substring(0, fileName.length() - SCRIPT_EXTENSION.length());
    }

    public void shutdown()
    {
        stopAll = true;
        scheduler.clear();
        com.gnottero.blossom.event.EventBus.clear();
        apps.clear();
    }

    /** The interpreter of a loaded app, or {@code null} if it is not loaded. */
    public Interpreter interpreterOf(String name)
    {
        return apps.get(name.toLowerCase(Locale.ROOT));
    }

    private Interpreter newInterpreter(Module main)
    {
        String appName = main == null ? "<comando>" : main.name();
        Interpreter.Builder builder = Interpreter.builder()
                .withApi(expression -> BlossomApi.apply(expression, appName));
        if (main != null)
        {
            builder.main(main);
        }
        Interpreter interpreter = builder.build();
        interpreter.host().setWatchdog(defaultWatchdog);
        applyAccess(interpreter.host());
        return interpreter;
    }

    /** Declares this server's boundaries to the host: directories for {@code core.io}, hosts for {@code core.http}. */
    private void applyAccess(ScriptHost host)
    {
        blossom.interpreter.BlossomServer scriptServer = (blossom.interpreter.BlossomServer) host.scriptServer();
        scriptServer.setIoRoots(ioRoots());
        scriptServer.setHttpAccess(httpAccess());
    }

    private Value evaluate(Interpreter interpreter, CommandSourceStack source, String code)
    {
        ScriptHost host = interpreter.host();
        Context context = new ScriptContext(host, source);
        return interpreter.evalIn(context, code, interactiveWatchdog(host));
    }

    /**
     * The limits for code a user launched by hand.
     *
     * <p>A command or a load may legitimately grind for a while, since whoever pressed enter is
     * waiting and no game event is left hanging, so the threshold is much wider than the one inside
     * the tick. An app that declared its own limits wins though: if it wrote them in the manifest,
     * it wants them here too.
     */
    private blossom.interpreter.ExecutionBudget.Policy interactiveWatchdog(ScriptHost host)
    {
        return host.watchdog().equals(defaultWatchdog)
                ? blossom.interpreter.ExecutionBudget.Policy.INTERACTIVE
                : host.watchdog();
    }
}
