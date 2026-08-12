package com.gnottero.blossom;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import blossom.project.DirectoryRegistry;
import blossom.project.HttpRegistry;
import blossom.project.PackRegistry;

/**
 * La configurazione del mod sul server: {@code config/blossomscript.json}.
 *
 * <p>Contiene le due cose che vanno decise da un amministratore e non da chi scrive un'app: da dove
 * si prendono i pack che non sono già su disco, e a quali cartelle {@code core.io} può arrivare.
 *
 * <pre>
 * {
 *   "registry": { "directory": "/srv/blossom-packs" },
 *   "io": {
 *     "roots": { "condiviso": "/srv/blossom-condiviso", "log": "/var/log/mio-server" },
 *     "writable": ["condiviso"]
 *   }
 * }
 * </pre>
 *
 * <p>Il registry può anche essere {@code { "url": "https://esempio.org/blossom/" }}.
 *
 * <p>Senza file, o senza una delle due sezioni, quella parte è spenta: una dipendenza dal registry
 * fallisce con un errore che lo dice, e {@code core.io} non apre niente. Sono spenti per default
 * apposta — sono i due pezzi del sistema che escono dalla cartella del mondo, uno verso la rete e
 * uno verso il disco del server, e quella è una decisione che va presa, non subita.
 */
public record ServerConfig(@Nullable PackRegistry registry, blossom.language.IoRoots ioRoots)
{
    public static final ServerConfig DEFAULT = new ServerConfig(null, blossom.language.IoRoots.NONE);
    private static final String FILE_NAME = BlossomMod.MOD_ID + ".json";

    /**
     * Legge la configurazione da {@code configDirectory}.
     *
     * <p>Un file illeggibile o malformato non impedisce l'avvio: viene segnalato nel log e si parte
     * con i default. Un mod che si rifiuta di caricare per una virgola di troppo in un file
     * facoltativo è un mod che rompe un server per una cosa che non stava usando.
     *
     * @param cacheDirectory dove il registry http tiene quello che ha scaricato
     */
    public static ServerConfig read(Path configDirectory, Path cacheDirectory)
    {
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            return DEFAULT;
        }
        try
        {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ServerConfig(readRegistry(root, file, cacheDirectory), readIoRoots(root, file));
        }
        catch (IOException | RuntimeException e)
        {
            BlossomMod.LOG.warn("{} is not readable ({}), starting with defaults", file, e.toString());
            return DEFAULT;
        }
    }

    private static @Nullable PackRegistry readRegistry(JsonObject root, Path file, Path cacheDirectory)
    {
        if (!root.has("registry") || !root.get("registry").isJsonObject())
        {
            return null;
        }
        JsonObject registry = root.getAsJsonObject("registry");
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
     * Le radici di {@code core.io}: un nome per cartella, e l'elenco di quelle scrivibili.
     *
     * <p>Una radice nominata in {@code writable} ma non dichiarata in {@code roots} è quasi sempre un
     * refuso, e viene segnalata: il contrario - dichiarare una cartella e dimenticarsi di renderla
     * scrivibile - fallisce da solo alla prima scrittura, questo no.
     */
    private static blossom.language.IoRoots readIoRoots(JsonObject root, Path file)
    {
        if (!root.has("io") || !root.get("io").isJsonObject())
        {
            return blossom.language.IoRoots.NONE;
        }
        JsonObject io = root.getAsJsonObject("io");
        java.util.Map<String, Path> roots = new java.util.LinkedHashMap<>();
        if (io.has("roots") && io.get("roots").isJsonObject())
        {
            io.getAsJsonObject("roots").entrySet()
                    .forEach(entry -> roots.put(entry.getKey(), Path.of(entry.getValue().getAsString())));
        }
        java.util.Set<String> writable = new java.util.LinkedHashSet<>();
        if (io.has("writable") && io.get("writable").isJsonArray())
        {
            io.getAsJsonArray("writable").forEach(name -> writable.add(name.getAsString()));
        }
        writable.stream().filter(name -> !roots.containsKey(name)).forEach(name ->
                BlossomMod.LOG.warn("{}: 'io.writable' names '{}', which is not among the declared roots", file, name));
        return new blossom.language.IoRoots(roots, writable);
    }
}
