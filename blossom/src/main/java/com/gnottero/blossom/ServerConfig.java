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
 * The mod configuration on the server: {@code config/blossomscript.json}.
 *
 * <p>It holds what an administrator decides and not what an app author does: where the packs that
 * are not already on disk come from, which world directories {@code core.io} can reach, which hosts
 * {@code core.http} can contact, and how long a script may run before the watchdog stops it.
 *
 * <pre>
 * {
 *   "root": "/srv/blossom-scripts",
 *   "registry": { "directory": "/srv/blossom-packs" },
 *   "io": { "roots": { "extra": "data/extra" }, "writable": ["extra"] },
 *   "http": { "hosts": ["api.example.com"], "timeout_ms": 5000 },
 *   "watchdog": { "timeout_ms": 2000, "max_depth": 256 }
 * }
 * </pre>
 *
 * <p>Every section is optional and, when missing, that part is off: a registry dependency fails
 * saying so, {@code core.io} opens nothing, {@code core.http} reaches nothing. These are the parts
 * that leave the process, towards the disk and towards the network, and that is a decision to be
 * taken rather than suffered.
 *
 * @see #read full key documentation in {@code docs/guide/12-configurazione.md}
 */
public record ServerConfig(Path scriptRoot, @Nullable PackRegistry registry, IoRoots ioRoots, HttpAccess httpAccess,
                           ExecutionBudget.Policy watchdog)
{
    private static final String FILE_NAME = BlossomMod.MOD_ID + ".json";
    /** where the registry keeps what it downloaded: inside the script directory, like everything else */
    private static final String REGISTRY_CACHE_DIRECTORY = ".cache";

    /**
     * The configuration of a server without the file: the default script directory and the
     * {@code core.io} roots every app has anyway.
     */
    public static ServerConfig defaults(Path scriptRoot, Path worldDirectory)
    {
        return new ServerConfig(scriptRoot, null, defaultIoRoots(worldDirectory), HttpAccess.NONE,
                ExecutionBudget.Policy.DEFAULT);
    }

    /**
     * The two roots that always exist, without anyone declaring them.
     *
     * <p>Once every app wrote into a directory of its own, {@code <world>/blossom/<app>.data/}, that
     * only it could see and only a separate module could reach. That directory is gone: there is one
     * place where scripts write, {@code data}, and one where they exchange things, {@code shared},
     * and both are reached with {@code core.io} like any other root. An app that wants its data to
     * itself uses a subdirectory named after it, which is what {@code <app>.data} did, but written
     * at the call site instead of hidden inside the mod.
     *
     * <p>Both are writable: they live inside the world directory, the mod creates them, and a read
     * only root nobody can fill would be useless. If the configuration file declares a root with the
     * same name, that one wins.
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
     * Reads the configuration from {@code configDirectory}.
     *
     * <p>An unreadable or malformed file does not prevent startup: it is reported in the log and the
     * defaults are used. A mod that refuses to load over one comma too many in an optional file is a
     * mod that breaks a server over something it was not using.
     *
     * @param worldDirectory the world directory: {@code core.io} roots resolve inside it and cannot
     *     leave it
     * @param defaultScriptRoot the script directory to use if the file declares no other one
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
     * The directory apps are loaded from: {@code "root"} if present, otherwise
     * {@code <world>/blossom/}.
     *
     * <p>A relative path resolves inside the world; an absolute one is taken as written, even
     * outside the world. That is intended: a script directory shared between several worlds is
     * exactly the case this key exists for, and whoever writes the server configuration file is
     * already the administrator of the disk. The boundary inside the world stays on
     * {@code io.roots} instead, which is what scripts can open on their own.
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
     * The {@code core.io} roots: one name per directory, all inside the world directory.
     *
     * <p>Paths are written relative to the world. An absolute one is valid only if it still points
     * inside, and it is the only mistake reported instead of silently accepted: outside the world
     * lies the rest of the server disk, which scripts must not be able to read under any
     * configuration.
     *
     * <p>A root named in {@code writable} but not declared in {@code roots} is almost always a typo
     * and is reported; the opposite, declaring a directory and forgetting to make it writable, fails
     * on its own at the first write.
     */
    private static IoRoots readIoRoots(JsonObject root, Path file, Path worldDirectory)
    {
        JsonObject io = section(root, "io");
        if (io == null)
        {
            return defaultIoRoots(worldDirectory);
        }
        Path world = worldDirectory.toAbsolutePath().normalize();
        // 'data' and 'shared' are always there; a declaration with the same name replaces them
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
     * The {@code core.http} hosts, with the limits that apply to every request.
     *
     * <p>Without {@code hosts} the section is useless and the network stays closed: it is the same
     * shape as the {@code core.io} roots, where permission is granted by name and never wholesale.
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
     * The execution limits for apps that declare none of their own.
     *
     * <p>{@code timeout_ms} set to zero removes the time limit: it is meant for profiling, and it is
     * reported because on a server it is the way to lose a tick without understanding why.
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

    /** @return section {@code name}, or {@code null} if missing or not an object */
    private static @Nullable JsonObject section(JsonObject root, String name)
    {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null;
    }

    private static long number(JsonObject object, String key, long fallback)
    {
        return object.has(key) ? object.get(key).getAsLong() : fallback;
    }
}
