package com.gnottero.blossom;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import blossom.interpreter.ExecutionBudget;
import blossom.language.HttpAccess;
import blossom.language.IoRoots;
import blossom.project.DirectoryRegistry;
import blossom.project.HttpRegistry;
import blossom.project.PackRegistry;

/**
 * La configurazione del mod sul server: {@code config/blossomscript.json}.
 *
 * <p>Contiene quello che decide un amministratore e non chi scrive un'app: da dove si prendono i
 * pack che non sono già su disco, a quali cartelle del mondo {@code core.io} può arrivare, quali
 * host può contattare {@code core.http}, e quanto a lungo può girare uno script prima che il
 * watchdog lo fermi.
 *
 * <pre>
 * {
 *   "root": "/srv/blossom-scripts",
 *   "registry": { "directory": "/srv/blossom-packs" },
 *   "io": { "roots": { "condiviso": "dati/condiviso" }, "writable": ["condiviso"] },
 *   "http": { "hosts": ["api.example.com"], "timeout_ms": 5000 },
 *   "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
 * }
 * </pre>
 *
 * <p>Ogni sezione è facoltativa e, quando manca, quella parte è spenta: una dipendenza dal registry
 * fallisce dicendolo, {@code core.io} non apre niente, {@code core.http} non raggiunge niente. Sono i
 * pezzi che escono dal processo - verso il disco e verso la rete - e quella è una decisione da
 * prendere, non da subire.
 *
 * @see #read documentazione completa delle chiavi in {@code docs/guide/12-configurazione.md}
 */
public record ServerConfig(Path scriptRoot, @Nullable PackRegistry registry, IoRoots ioRoots, HttpAccess httpAccess,
                           ExecutionBudget.Policy watchdog)
{
    private static final String FILE_NAME = BlossomMod.MOD_ID + ".json";
    /** dove il registry tiene quello che ha scaricato: dentro la cartella degli script, come tutto il resto */
    private static final String REGISTRY_CACHE_DIRECTORY = ".cache";

    /**
     * La configurazione di un server che non ha il file: la cartella degli script di default e le
     * radici di {@code core.io} che ogni app ha comunque.
     */
    public static ServerConfig defaults(Path scriptRoot, Path worldDirectory)
    {
        return new ServerConfig(scriptRoot, null, defaultIoRoots(worldDirectory), HttpAccess.NONE,
                ExecutionBudget.Policy.DEFAULT);
    }

    /**
     * Le due radici che esistono sempre, senza che nessuno le dichiari.
     *
     * <p>Un tempo ogni app scriveva in una cartella sua, {@code <mondo>/blossom/<app>.data/}, che
     * solo lei vedeva e che solo un modulo a parte sapeva raggiungere. Quella cartella non c'è
     * più: c'è un posto solo dove gli script scrivono - {@code data} - e uno dove si scambiano roba -
     * {@code shared} - e ci si arriva con {@code core.io} come a qualunque altra radice. Un'app che
     * vuole i suoi dati per sé usa una sottocartella col suo nome, che è quello che
     * {@code <app>.data} faceva, ma scritto nel punto della chiamata invece che nascosto dentro il
     * mod.
     *
     * <p>Sono entrambe scrivibili: sono dentro la cartella del mondo, le crea il mod, e una radice
     * di sola lettura che nessuno può riempire non servirebbe a niente. Se il file di
     * configurazione dichiara una radice con lo stesso nome, vince la sua.
     */
    private static IoRoots defaultIoRoots(Path worldDirectory)
    {
        Path world = worldDirectory.toAbsolutePath().normalize();
        return new IoRoots(
                Map.of("data", world.resolve(BlossomMod.MOD_ID).resolve("data"),
                        "shared", world.resolve(BlossomMod.MOD_ID).resolve("shared")),
                Set.of("data", "shared"),
                world);
    }

    /**
     * Legge la configurazione da {@code configDirectory}.
     *
     * <p>Un file illeggibile o malformato non impedisce l'avvio: viene segnalato nel log e si parte
     * con i default. Un mod che si rifiuta di caricare per una virgola di troppo in un file
     * facoltativo è un mod che rompe un server per una cosa che non stava usando.
     *
     * @param worldDirectory la cartella del mondo: le radici di {@code core.io} si risolvono lì
     *     dentro e non possono uscirne
     * @param defaultScriptRoot la cartella degli script da usare se il file non ne dichiara un'altra
     */
    public static ServerConfig read(Path configDirectory, Path worldDirectory, Path defaultScriptRoot)
    {
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            return defaults(defaultScriptRoot, worldDirectory);
        }
        try
        {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Path scriptRoot = readScriptRoot(root, worldDirectory, defaultScriptRoot);
            return new ServerConfig(
                    scriptRoot,
                    readRegistry(root, file, scriptRoot.resolve(REGISTRY_CACHE_DIRECTORY)),
                    readIoRoots(root, file, worldDirectory),
                    readHttpAccess(root, file),
                    readWatchdog(root, file));
        }
        catch (IOException | RuntimeException e)
        {
            BlossomMod.LOG.warn("{} is not readable ({}), starting with defaults", file, e.toString());
            return defaults(defaultScriptRoot, worldDirectory);
        }
    }

    /**
     * La cartella da cui si caricano le app: {@code "root"} se c'è, altrimenti {@code <mondo>/blossom/}.
     *
     * <p>Un percorso relativo si risolve dentro il mondo; uno assoluto vale com'è, anche fuori dal
     * mondo. È voluto: una cartella di script condivisa fra più mondi è esattamente il caso per cui
     * questa chiave esiste, e chi scrive il file di configurazione del server è già l'amministratore
     * del disco. Il confine dentro il mondo resta invece su {@code io.roots}, che è quello che gli
     * script possono aprire da soli.
     */
    private static Path readScriptRoot(JsonObject root, Path worldDirectory, Path defaultScriptRoot)
    {
        if (!root.has("root") || !root.get("root").isJsonPrimitive())
        {
            return defaultScriptRoot;
        }
        String declared = root.get("root").getAsString();
        if (declared.isBlank())
        {
            return defaultScriptRoot;
        }
        return worldDirectory.resolve(declared).toAbsolutePath().normalize();
    }

    private static @Nullable PackRegistry readRegistry(JsonObject root, Path file, Path cacheDirectory)
    {
        JsonObject registry = section(root, "registry");
        if (registry == null)
        {
            return null;
        }
        if (registry.has("directory"))
        {
            return new DirectoryRegistry(Path.of(registry.get("directory").getAsString()));
        }
        if (registry.has("url"))
        {
            return new HttpRegistry(URI.create(registry.get("url").getAsString()), cacheDirectory);
        }
        BlossomMod.LOG.warn("{}: the 'registry' section says neither 'directory' nor 'url', ignoring it", file);
        return null;
    }

    /**
     * Le radici di {@code core.io}: un nome per cartella, tutte dentro la cartella del mondo.
     *
     * <p>I percorsi si scrivono relativi al mondo. Uno assoluto vale solo se punta comunque dentro,
     * ed è l'unico modo di sbagliarsi che viene segnalato invece che accettato in silenzio: fuori dal
     * mondo c'è il resto del disco del server, che gli script non devono poter leggere in nessuna
     * configurazione.
     *
     * <p>Una radice nominata in {@code writable} ma non dichiarata in {@code roots} è quasi sempre un
     * refuso e viene segnalata; il contrario - dichiarare una cartella e dimenticarsi di renderla
     * scrivibile - fallisce da solo alla prima scrittura.
     */
    private static IoRoots readIoRoots(JsonObject root, Path file, Path worldDirectory)
    {
        JsonObject io = section(root, "io");
        if (io == null)
        {
            return defaultIoRoots(worldDirectory);
        }
        Path world = worldDirectory.toAbsolutePath().normalize();
        // 'data' e 'shared' ci sono sempre; una dichiarazione con lo stesso nome le rimpiazza
        Map<String, Path> roots = new LinkedHashMap<>(defaultIoRoots(world).paths());
        if (io.has("roots") && io.get("roots").isJsonObject())
        {
            io.getAsJsonObject("roots").entrySet().forEach(entry ->
            {
                Path directory = world.resolve(entry.getValue().getAsString()).toAbsolutePath().normalize();
                if (!directory.startsWith(world))
                {
                    BlossomMod.LOG.warn("{}: io root '{}' points outside the world folder ({}), ignoring it",
                            file, entry.getKey(), directory);
                    return;
                }
                roots.put(entry.getKey(), directory);
            });
        }
        Set<String> writable = new LinkedHashSet<>(defaultIoRoots(world).writableNames());
        if (io.has("writable") && io.get("writable").isJsonArray())
        {
            io.getAsJsonArray("writable").forEach(name -> writable.add(name.getAsString()));
        }
        writable.stream().filter(name -> !roots.containsKey(name)).forEach(name ->
                BlossomMod.LOG.warn("{}: 'io.writable' names '{}', which is not among the declared roots", file, name));
        return new IoRoots(roots, writable, world);
    }

    /**
     * Gli host di {@code core.http}, con i limiti che valgono per tutte le richieste.
     *
     * <p>Senza {@code hosts} la sezione non serve a niente e la rete resta chiusa: è la stessa forma
     * delle radici di {@code core.io}, dove il permesso si concede per nome e mai in blocco.
     */
    private static HttpAccess readHttpAccess(JsonObject root, Path file)
    {
        JsonObject http = section(root, "http");
        if (http == null)
        {
            return HttpAccess.NONE;
        }
        List<String> hosts = new java.util.ArrayList<>();
        if (http.has("hosts") && http.get("hosts").isJsonArray())
        {
            http.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
        }
        Set<String> schemes = new LinkedHashSet<>();
        if (http.has("schemes") && http.get("schemes").isJsonArray())
        {
            http.getAsJsonArray("schemes").forEach(scheme -> schemes.add(scheme.getAsString()));
        }
        if (schemes.isEmpty())
        {
            schemes.add("https");
        }
        if (schemes.contains("http"))
        {
            BlossomMod.LOG.warn("{}: 'http.schemes' allows plain http: requests to those hosts travel"
                    + " unencrypted and can be read and altered in transit", file);
        }
        if (hosts.contains("*"))
        {
            BlossomMod.LOG.warn("{}: 'http.hosts' contains '*': every host on the internet is reachable"
                    + " from every app installed on this server", file);
        }
        return new HttpAccess(hosts, schemes,
                (int) number(http, "timeout_ms", HttpAccess.NONE.timeoutMillis()),
                number(http, "max_response_bytes", HttpAccess.NONE.maxResponseBytes()),
                http.has("follow_redirects") && http.get("follow_redirects").getAsBoolean());
    }

    /**
     * I limiti di esecuzione per le app che non ne dichiarano di propri.
     *
     * <p>{@code timeout_ms} a zero toglie il limite di tempo: serve a chi sta profilando, ed è
     * segnalato perché su un server è il modo di far sparire un tick senza capire perché.
     */
    private static ExecutionBudget.Policy readWatchdog(JsonObject root, Path file)
    {
        JsonObject watchdog = section(root, "watchdog");
        if (watchdog == null)
        {
            return ExecutionBudget.Policy.DEFAULT;
        }
        long timeout = number(watchdog, "timeout_ms", ExecutionBudget.Policy.DEFAULT.timeoutMillis());
        int depth = (int) number(watchdog, "max_depth", ExecutionBudget.Policy.DEFAULT.maxDepth());
        if (timeout <= 0)
        {
            BlossomMod.LOG.warn("{}: 'watchdog.timeout_ms' is not positive: a script that loops will hang"
                    + " the server tick with nothing to stop it", file);
        }
        return new ExecutionBudget.Policy(timeout, depth);
    }

    /** @return la sezione {@code name}, o {@code null} se manca o non è un oggetto */
    private static @Nullable JsonObject section(JsonObject root, String name)
    {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null;
    }

    private static long number(JsonObject object, String key, long fallback)
    {
        return object.has(key) ? object.get(key).getAsLong() : fallback;
    }
}
