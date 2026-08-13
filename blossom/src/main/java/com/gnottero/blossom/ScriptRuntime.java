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
 * Gli script caricati su un server, e la cartella da cui vengono.
 *
 * <p>Un'app è un file sotto {@code <mondo>/blossom/}, caricato in un proprio {@link Interpreter}:
 * due app non condividono nulla, che è il punto di partenza del sistema a moduli della Fase 3.
 */
public class ScriptRuntime
{
    /** lo stesso logger del mod: le classi portate da Scarpet lo cercano qui */
    public static final org.slf4j.Logger LOG = BlossomMod.LOG;

    /** dove vivono gli script di un mondo */
    public static final String SCRIPT_DIRECTORY = "blossom";
    /** un'app in un file solo */
    private static final String SCRIPT_EXTENSION = blossom.project.ModuleName.EXTENSION;
    /** una libreria in un file solo: si importa, non si carica */
    private static final String LIBRARY_EXTENSION = blossom.project.ModuleName.LIBRARY_EXTENSION;

    /**
     * Lo stato del tick forzato da {@code game_tick()}: quando e' partito il tick corrente e quanto
     * a fondo e' annidata la ricorsione. Sono qui e non in una variabile locale perche' un tick
     * forzato puo' eseguire codice che ne forza un altro.
     */
    public long tickStart = 0L;
    public int tickDepth = 0;
    /** alzato quando il server sta chiudendo: gli script in corso devono fermarsi */
    public volatile boolean stopAll = false;

    /**
     * I limiti di esecuzione applicati alle app che non ne dichiarano di propri
     * (PIANO_LINGUAGGIO.md §9). È il watchdog: senza, uno script che cicla dentro un evento ferma il
     * tick e con lui il server.
     */
    public volatile blossom.interpreter.ExecutionBudget.Policy defaultWatchdog =
            blossom.interpreter.ExecutionBudget.Policy.DEFAULT;

    /** le chiamate rimandate a un tick futuro da {@code schedule} */
    public final com.gnottero.blossom.event.Scheduler scheduler = new com.gnottero.blossom.event.Scheduler();

    private final MinecraftServer server;
    private final Map<String, Interpreter> apps = new ConcurrentHashMap<>();

    public ScriptRuntime(MinecraftServer server)
    {
        this.server = server;
        ScriptHost.mainThread = Thread.currentThread();
        this.config = ServerConfig.read(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(),
                scriptDirectory().resolve(REGISTRY_CACHE_DIRECTORY));
        this.registry = config.registry();
    }

    /** dove il registry tiene quello che ha scaricato: dentro il mondo, come tutto il resto */
    private static final String REGISTRY_CACHE_DIRECTORY = ".cache";

    /**
     * Il registry da cui prendere le dipendenze che non stanno già su disco, o {@code null} se su
     * questo server non ne è configurato nessuno.
     *
     * <p>Spento per default apposta: è l'unico pezzo del sistema che può parlare con la rete e far
     * girare su un server codice che nessuno ha copiato lì a mano. Accenderlo è una scelta
     * dell'amministratore, non un default.
     */
    public volatile blossom.project.@org.jspecify.annotations.Nullable PackRegistry registry = null;

    /** la configurazione del server: registry e radici di {@code core.io} */
    private final ServerConfig config;

    /** Le cartelle a cui {@code core.io} puo' arrivare su questo server. */
    public blossom.language.IoRoots ioRoots()
    {
        return config.ioRoots();
    }

    /** Cosa c'è intorno a un pack: gli altri pack del mondo e, se configurato, il registry. */
    public blossom.project.PackEnvironment packEnvironment()
    {
        return new WorldPacks(scriptDirectory(), registry);
    }

    /** Il server su cui gira questo runtime. */
    public MinecraftServer server()
    {
        return server;
    }

    /** La cartella degli script del mondo corrente, creata alla prima richiesta. */
    public Path scriptDirectory()
    {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve(SCRIPT_DIRECTORY);
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
     * Valuta del codice al volo, senza caricarlo come app: ogni chiamata parte da un interprete
     * pulito, perché non c'è nessuno stato che abbia senso conservare tra due comandi diversi.
     */
    public Value runOneShot(CommandSourceStack source, String code)
    {
        Interpreter interpreter = newInterpreter(null);
        return evaluate(interpreter, source, code);
    }

    /**
     * Carica (o ricarica) l'app {@code name} dal file {@code <mondo>/blossom/<name>.sc}, eseguendone
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
        // un ricaricamento parte pulito: gli handler e i task della versione precedente non devono
        // sopravvivere alla loro app
        com.gnottero.blossom.event.EventBus.unregisterApp(module.name());
        scheduler.unregisterApp(module.name());
        Interpreter interpreter = newInterpreter(module);
        apps.put(module.name(), interpreter);
        try
        {
            return evaluate(interpreter, source, module.code());
        }
        catch (RuntimeException e)
        {
            apps.remove(module.name());
            com.gnottero.blossom.event.EventBus.unregisterApp(module.name());
            throw e;
        }
    }

    /**
     * Come {@link #load}, ma rifacendo la scelta delle versioni delle dipendenze invece di riusare
     * quella scritta in {@code blossom.lock}.
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
     * Carica un pack: una cartella con dentro {@code blossom.json}, il suo {@code src/} e tutti i
     * moduli che l'entrypoint importa.
     *
     * <p>Manifesto mancante, modulo mancante, nome che non combacia col percorso e import ciclico
     * sono errori di caricamento: l'app o entra intera o non entra.
     */
    private Value loadPack(CommandSourceStack source, String appName, Path root, boolean refreshLock) throws IOException
    {
        com.gnottero.blossom.event.EventBus.unregisterApp(appName);
        scheduler.unregisterApp(appName);
        try
        {
            // l'app entra nell'elenco prima di girare: il codice a livello di modulo puo' gia'
            // registrare eventi, e chi li registra deve trovare la propria app.
            //
            // Va messa con 'put' e non con 'putIfAbsent': a un ricaricamento la voce c'e' gia', ed e'
            // quella dell'app vecchia. Con 'putIfAbsent' gli event.on(...) del preambolo avrebbero
            // legato i loro handler all'interprete di prima, mentre la funzione registrata viene dai
            // moduli nuovi: al primo evento il vecchio host non riconosce il modulo e la chiamata
            // muore con "Module structure changed for the app".
            Interpreter interpreter = Interpreter.forProject(root,
                    expression -> BlossomApi.apply(expression, appName),
                    host ->
                    {
                        apps.put(appName, Interpreter.of(host));
                        // i default del server; se il manifesto dichiara i suoi, li sovrascrive subito dopo
                        host.setWatchdog(defaultWatchdog);
                        applyIoRoots(host);
                        return new ScriptContext(host, source);
                    },
                    packEnvironment(), refreshLock);
            apps.put(appName, interpreter);
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
     * Carica i pack che nel loro manifesto hanno {@code "autoload": true}.
     *
     * <p>Viene chiamata appena il mondo è aperto e prima che {@code server_starts} venga generato:
     * un'app che parte da sola deve poter ascoltare anche quell'evento, che altrimenti sarebbe già
     * passato quando lei arriva.
     *
     * <p>Un pack che non carica non ferma gli altri: l'errore va nel log e in chat come qualunque
     * altro errore di un'app, e il resto del mondo parte lo stesso.
     *
     * @return i nomi delle app caricate
     */
    public List<String> loadAutoloadApps(CommandSourceStack source)
    {
        List<String> loaded = new ArrayList<>();
        for (String name : availableApps())
        {
            Path pack = scriptDirectory().resolve(name);
            if (!Files.isRegularFile(pack.resolve(blossom.project.ProjectManifest.FILE_NAME)))
            {
                // un'app in un file solo non ha manifesto, quindi non ha modo di chiedere l'autoload
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

    /** Esegue {@code code} dentro l'app già caricata {@code name}, che ne vede funzioni e globali. */
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
     * Esegue {@code code} misurando dove va il tempo, funzione per funzione.
     *
     * @param name l'app in cui eseguire, o {@code null} per un interprete pulito
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
     * Accende la misura su un'app già caricata: da qui in poi conta anche quello che l'app fa da
     * sola, cioè i suoi handler di evento, che sono il motivo per cui questo comando esiste.
     */
    public blossom.interpreter.Profiler startProfiling(String name)
    {
        return requireApp(name).host().startProfiling();
    }

    /** Chiude la misura aperta da {@link #startProfiling} e restituisce cosa ha visto. */
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
     * I nomi delle app presenti su disco, caricate o no: sia i file singoli {@code <nome>.bs} sia i
     * pack, cioè le cartelle con dentro un {@code blossom.json}.
     *
     * <p>La cartella viene riletta ad ogni chiamata apposta: un pack copiato nel mondo a server
     * acceso dev'essere caricabile subito, senza riavvii.
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

    /** @return il nome dell'app che sta in {@code entry}, o {@code null} se non è un'app */
    private @org.jspecify.annotations.Nullable String appNameOf(Path entry)
    {
        String fileName = entry.getFileName().toString();
        if (Files.isDirectory(entry))
        {
            return Files.isRegularFile(entry.resolve(blossom.project.ProjectManifest.FILE_NAME)) ? fileName : null;
        }
        // le librerie (.bsl) si importano, non si caricano: non sono app
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

    /** L'interprete di un'app caricata, o {@code null} se non e' caricata. */
    public Interpreter interpreterOf(String name)
    {
        return apps.get(name.toLowerCase(Locale.ROOT));
    }

    private Interpreter newInterpreter(Module main)
    {
        String appName = main == null ? "<comando>" : main.name();
        Interpreter.Builder builder = Interpreter.builder()
                .storage(scriptDirectory())
                .withApi(expression -> BlossomApi.apply(expression, appName));
        if (main != null)
        {
            builder.main(main);
        }
        Interpreter interpreter = builder.build();
        interpreter.host().setWatchdog(defaultWatchdog);
        applyIoRoots(interpreter.host());
        return interpreter;
    }

    /** Dichiara all'host le cartelle a cui {@code core.io} puo' arrivare su questo server. */
    private void applyIoRoots(ScriptHost host)
    {
        ((blossom.interpreter.BlossomServer) host.scriptServer()).setIoRoots(ioRoots());
    }

    private Value evaluate(Interpreter interpreter, CommandSourceStack source, String code)
    {
        ScriptHost host = interpreter.host();
        Context context = new ScriptContext(host, source);
        return interpreter.evalIn(context, code, interactiveWatchdog(host));
    }

    /**
     * I limiti per il codice che un utente ha lanciato a mano.
     *
     * <p>Un comando o un caricamento possono legittimamente macinare a lungo - chi ha premuto invio
     * sta aspettando, non c'è nessun evento di gioco che resta appeso - quindi la soglia è molto più
     * larga di quella dentro il tick. Un'app che ha dichiarato i propri limiti però comanda: se li ha
     * scritti nel manifesto, li vuole anche qui.
     */
    private blossom.interpreter.ExecutionBudget.Policy interactiveWatchdog(ScriptHost host)
    {
        return host.watchdog().equals(defaultWatchdog)
                ? blossom.interpreter.ExecutionBudget.Policy.INTERACTIVE
                : host.watchdog();
    }
}
