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
 * {@code blossom.lock}: quale versione esatta e' stata scelta l'ultima volta, e con che impronta.
 *
 * <p>Un vincolo come {@code ^1.0.0} e' una domanda, e la risposta cambia nel tempo: senza lock la
 * stessa app caricata due volte puo' prendere due versioni diverse e "cambiare da sola". Con il
 * lock la risposta viene scritta la prima volta e riusata finche' qualcuno non chiede di aggiornarla.
 *
 * <p>Si blocca solo quello che puo' cambiare senza che l'utente lo veda: le dipendenze dal registry
 * (versione e impronta) e quelle a pack installato (la sola versione). Una dipendenza a percorso e'
 * la cartella accanto, che si sta modificando adesso, e bloccarla farebbe fallire il progetto ad
 * ogni salvataggio. L'impronta c'e' solo per il registry, l'unico caso in cui il contenuto arriva
 * da fuori.
 */
public record LockFile(Map<String, Entry> dependencies)
{
    public static final String FILE_NAME = "blossom.lock";
    /** la versione del formato del lock, non del progetto: se cambia, un lock vecchio si rigenera */
    private static final int FORMAT = 1;

    /**
     * Una riga del lock.
     *
     * @param source  da dove veniva: {@code registry} o {@code pack}
     * @param version la versione esatta scelta allora
     * @param sha256  l'impronta di quello che è stato scaricato, solo per il registry
     */
    public record Entry(String source, Version version, @Nullable String sha256)
    {
    }

    public static final LockFile EMPTY = new LockFile(Map.of());

    /** Il lock accanto al manifesto, o {@link #EMPTY} se non c'è ancora. */
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
            // un lock di un formato che non conosciamo si ignora, non si tenta di interpretare: al
            // prossimo salvataggio verrà riscritto in quello corrente
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
     * Scrive il lock, se è cambiato.
     *
     * @return se il file è stato toccato
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
        // ordinato per nome: un lock che cambia ordine ad ogni scrittura è un lock che sporca ogni
        // diff senza dire niente
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
