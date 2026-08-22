package blossom.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;

/**
 * {@code blossom.lock}: exactly which version was chosen last time, and with what fingerprint.
 *
 * <p>A constraint like {@code ^1.0.0} is a question, and the answer changes over time: without a
 * lock, the same app loaded twice could pick two different versions and "change on its own". With
 * the lock, the answer is written the first time and reused until someone asks to update it.
 *
 * <p>Only what can change without the user seeing it gets locked: registry dependencies (version and
 * fingerprint) and installed-pack dependencies (version only). A path dependency is the directory
 * next door, being edited right now, and locking it would make the project fail on every save. The
 * fingerprint exists only for the registry, the one case where the content comes from outside.
 */
public record LockFile(Map<String, Entry> dependencies)
{
    public static final String FILE_NAME = "blossom.lock";
    /** the lock format's version, not the project's: if it changes, an old lock regenerates itself */
    private static final int FORMAT = 1;

    /**
     * One entry of the lock.
     *
     * @param source  where it came from: {@code registry} or {@code pack}
     * @param version the exact version chosen at the time
     * @param sha256  the fingerprint of what was downloaded, only for the registry
     */
    public record Entry(String source, Version version, @Nullable String sha256)
    {
    }

    public static final LockFile EMPTY = new LockFile(Map.of());

    /** The lock next to the manifest, or {@link #EMPTY} if there isn't one yet. */
    public static LockFile read(Path projectRoot)
    {
        Path file = projectRoot.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            return EMPTY;
        }
        SourcePosition where = new SourcePosition(file.toString(), 1, 1);
        JsonObject root;
        try
        {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        catch (JsonParseException | IllegalStateException e)
        {
            throw new ParseError(where, FILE_NAME + " is not a valid json object: " + e.getMessage()
                    + ". Delete it to have it regenerated.");
        }
        if (!root.has("format") || root.get("format").getAsInt() != FORMAT)
        {
            // a lock in a format we don't know is ignored, not interpreted: it will be rewritten
            // in the current format on the next save
            return EMPTY;
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        JsonElement declared = root.get("dependencies");
        if (declared != null && declared.isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : declared.getAsJsonObject().entrySet())
            {
                JsonObject spec = entry.getValue().getAsJsonObject();
                Version version = Version.tryParse(spec.get("version").getAsString());
                if (version == null)
                {
                    throw new ParseError(where, "entry '" + entry.getKey() + "' has no valid version."
                            + " Delete " + FILE_NAME + " to have it regenerated.");
                }
                entries.put(entry.getKey(), new Entry(
                        spec.has("source") ? spec.get("source").getAsString() : "registry",
                        version,
                        spec.has("sha256") ? spec.get("sha256").getAsString() : null));
            }
        }
        return new LockFile(Map.copyOf(entries));
    }

    public @Nullable Entry of(String alias)
    {
        return dependencies.get(alias);
    }

    /**
     * Writes the lock, if it changed.
     *
     * @return whether the file was touched
     */
    public boolean writeIfChanged(Path projectRoot) throws IOException
    {
        if (equals(read(projectRoot)))
        {
            return false;
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonObject entries = new JsonObject();
        // sorted by name: a lock that reorders itself on every write is a lock that dirties every
        // diff without saying anything
        new TreeMap<>(dependencies).forEach((alias, entry) ->
        {
            JsonObject spec = new JsonObject();
            spec.addProperty("source", entry.source());
            spec.addProperty("version", entry.version().toString());
            if (entry.sha256() != null)
            {
                spec.addProperty("sha256", entry.sha256());
            }
            entries.add(alias, spec);
        });
        root.add("dependencies", entries);
        Files.writeString(projectRoot.resolve(FILE_NAME),
                new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
        return true;
    }

    public boolean isEmpty()
    {
        return dependencies.isEmpty();
    }
}
