package blossom.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.interpreter.ExecutionBudget;

/**
 * The {@code blossom.json} manifest of a project (PIANO_LINGUAGGIO.md §7).
 *
 * <p>It is the only place name, version, entrypoint, dependencies and execution limits live: none of
 * that information is inferred from a file name or an implicit convention.
 *
 * <p>The project version and the dependency constraints are semver ({@link Version},
 * {@link VersionRange}); {@code api_level} instead is {@code MAJOR.MINOR} and describes the Blossom
 * API, not the app (see {@link ApiLevel}). They are two different numberings on purpose: one says
 * "what I am", the other "what I need".
 */
public record ProjectManifest(
        String name,
        Version version,
        Path entrypoint,
        ApiLevel apiLevel,
        boolean autoload,
        Map<String, Dependency> dependencies,
        ExecutionBudget.@Nullable Policy watchdog)
{
    public static final String FILE_NAME = "blossom.json";

    /** The manifest of a project whose root is {@code root}. */
    public static ProjectManifest read(Path root) throws IOException
    {
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            throw new IOException("Missing " + FILE_NAME + " in " + root);
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        SourcePosition where = new SourcePosition(file.toString(), 1, 1);
        JsonObject root_;
        try
        {
            root_ = JsonParser.parseString(json).getAsJsonObject();
        }
        catch (JsonParseException | IllegalStateException e)
        {
            throw new ParseError(where, FILE_NAME + " is not a valid json object: " + e.getMessage());
        }

        String name = requireString(root_, "name", where);
        Version version = parseVersion(optionalString(root_, "version", "0.1.0"), where);
        String entrypoint = optionalString(root_, "entrypoint", "src/main" + ModuleName.EXTENSION);
        ApiLevel apiLevel = parseApiLevel(root_, where);

        return new ProjectManifest(name, version, root.resolve(entrypoint).normalize(), apiLevel,
                readAutoload(root_, where), readDependencies(root_, root, where), readWatchdog(root_, where));
    }

    private static Version parseVersion(String text, SourcePosition where)
    {
        try
        {
            return Version.parse(text);
        }
        catch (IllegalArgumentException e)
        {
            throw new ParseError(where, "'version': " + e.getMessage());
        }
    }

    private static ApiLevel parseApiLevel(JsonObject object, SourcePosition where)
    {
        if (!object.has("api_level"))
        {
            // not declaring it means "I use nothing recent", that is the lowest level
            return ApiLevel.DEFAULT;
        }
        try
        {
            return ApiLevel.parse(object.get("api_level").getAsString());
        }
        catch (IllegalArgumentException | UnsupportedOperationException e)
        {
            throw new ParseError(where, "'api_level': " + e.getMessage());
        }
    }

    /**
     * The {@code "autoload"} field: when true, the pack loads on its own as soon as the world is
     * open, without anyone running {@code /blossom script load}.
     *
     * <p>It is a declaration of the pack and not a list in the server configuration, on purpose:
     * whoever copies a directory into <code>&lt;world&gt;/blossom/</code> has already said all that
     * is needed, and an app that has to start on its own knows that about itself. Absent means
     * {@code false}: a pack that does not declare it never starts unless someone asks.
     */
    private static boolean readAutoload(JsonObject object, SourcePosition where)
    {
        if (!object.has("autoload"))
        {
            return false;
        }
        JsonElement declared = object.get("autoload");
        if (!declared.isJsonPrimitive() || !declared.getAsJsonPrimitive().isBoolean())
        {
            throw new ParseError(where, "'autoload' must be true or false");
        }
        return declared.getAsBoolean();
    }

    /**
     * La sezione {@code "dependencies"}: tre forme, mutuamente esclusive (vedi {@link Dependency}).
     */
    private static Map<String, Dependency> readDependencies(JsonObject object, Path root, SourcePosition where)
    {
        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        if (!object.has("dependencies"))
        {
            return Map.of();
        }
        JsonElement declared = object.get("dependencies");
        if (!declared.isJsonObject())
        {
            throw new ParseError(where, "'dependencies' must be an object {name -> spec}");
        }
        for (Map.Entry<String, JsonElement> entry : declared.getAsJsonObject().entrySet())
        {
            String alias = entry.getKey();
            if (!entry.getValue().isJsonObject())
            {
                throw new ParseError(where, "dependency '" + alias + "' must be an object, e.g."
                        + " {\"path\": \"../" + alias + "\"} or {\"version\": \"^1.0.0\"}");
            }
            dependencies.put(alias, readDependency(alias, entry.getValue().getAsJsonObject(), root, where));
        }
        return Map.copyOf(dependencies);
    }

    private static Dependency readDependency(String alias, JsonObject spec, Path root, SourcePosition where)
    {
        boolean hasPath = spec.has("path");
        boolean hasPack = spec.has("pack");
        if (hasPath && hasPack)
        {
            throw new ParseError(where, "dependency '" + alias + "' declares both 'path' and 'pack':"
                    + " they are two different origins, and on a conflict there would be no way to tell which wins.");
        }
        VersionRange range = readRange(alias, spec, where);
        if (hasPath)
        {
            return Dependency.ofPath(alias, root.resolve(spec.get("path").getAsString()).normalize(), range);
        }
        if (hasPack)
        {
            return Dependency.ofPack(alias, spec.get("pack").getAsString(), range);
        }
        if (!spec.has("version"))
        {
            throw new ParseError(where, "dependency '" + alias + "' does not say where it comes from: it needs"
                    + " a 'path' (a directory), a 'pack' (a pack installed in the same world) or a"
                    + " 'version' (from the registry).");
        }
        return Dependency.ofRegistry(alias, alias, range);
    }

    private static VersionRange readRange(String alias, JsonObject spec, SourcePosition where)
    {
        if (!spec.has("version"))
        {
            return VersionRange.ANY;
        }
        try
        {
            return VersionRange.parse(spec.get("version").getAsString());
        }
        catch (IllegalArgumentException | UnsupportedOperationException e)
        {
            throw new ParseError(where, "dependency '" + alias + "': " + e.getMessage());
        }
    }

    /** The source directory, that is the one holding the declared entrypoint. */
    public Path sourceRoot(Path root)
    {
        Path parent = entrypoint.getParent();
        return parent == null ? root : parent;
    }

    /**
     * The {@code "watchdog"} section: the execution limits of the app (PIANO_LINGUAGGIO.md §9).
     *
     * <pre>
     * "watchdog": { "timeout_ms": 2000, "max_depth": 256 }   // explicit limits
     * "watchdog": false                                       // no limit, a deliberate choice
     * </pre>
     *
     * <p>Absent means "the server ones": the vast majority of apps has no reason to know anything
     * about them, and whoever touches them is doing it deliberately. {@code null} here means exactly
     * "not declared", which differs from "no limit".
     */
    private static ExecutionBudget.@Nullable Policy readWatchdog(JsonObject object, SourcePosition where)
    {
        if (!object.has("watchdog"))
        {
            return null;
        }
        JsonElement declared = object.get("watchdog");
        if (declared.isJsonPrimitive() && declared.getAsJsonPrimitive().isBoolean())
        {
            if (declared.getAsBoolean())
            {
                return null; // 'true' = quelli di default, cioè come non averlo scritto
            }
            return ExecutionBudget.Policy.UNLIMITED;
        }
        if (!declared.isJsonObject())
        {
            throw new ParseError(where, "'watchdog' must be false or an object"
                    + " {\"timeout_ms\": ..., \"max_depth\": ...}");
        }
        JsonObject spec = declared.getAsJsonObject();
        ExecutionBudget.Policy fallback = ExecutionBudget.Policy.DEFAULT;
        long timeout = optionalNumber(spec, "timeout_ms", fallback.timeoutMillis(), where);
        long depth = optionalNumber(spec, "max_depth", fallback.maxDepth(), where);
        return new ExecutionBudget.Policy(timeout, (int) depth);
    }

    private static long optionalNumber(JsonObject object, String key, long fallback, SourcePosition where)
    {
        if (!object.has(key))
        {
            return fallback;
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            throw new ParseError(where, "'" + key + "' must be a number");
        }
        long number = value.getAsLong();
        if (number < 0)
        {
            throw new ParseError(where, "'" + key + "' cannot be negative (0 = no limit)");
        }
        return number;
    }

    private static String requireString(JsonObject object, String key, SourcePosition where)
    {
        if (!object.has(key) || !object.get(key).isJsonPrimitive())
        {
            throw new ParseError(where, "missing required field '" + key + "' in " + FILE_NAME);
        }
        return object.get(key).getAsString();
    }

    private static String optionalString(JsonObject object, String key, String fallback)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
