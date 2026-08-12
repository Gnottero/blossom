package blossom.project;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Un registry raggiunto via http, con un indice statico e archivi zip.
 *
 * <h2>Il formato dell'indice</h2>
 *
 * <p>{@code <base>/index.json}, e nient'altro: niente API, niente query, niente stato lato server.
 * Un file che si può servire da qualunque cosa serva file statici, compreso un bucket o una pagina
 * di progetto.
 *
 * <pre>
 * {
 *   "vector": {
 *     "1.4.0": { "url": "vector-1.4.0.zip", "sha256": "9f86d0..." },
 *     "2.0.0": { "url": "vector-2.0.0.zip", "sha256": "2c26b4..." }
 *   }
 * }
 * </pre>
 *
 * <p>{@code url} può essere relativo alla base o assoluto. {@code sha256} è <b>obbligatorio</b>: un
 * archivio che arriva dalla rete e viene scompattato senza controllo è codice arbitrario che gira su
 * un server, e "il registry è nostro" non è una garanzia di integrità — chiunque stia in mezzo alla
 * connessione può sostituirlo.
 *
 * <h2>Cache</h2>
 *
 * <p>Ogni versione viene scaricata una volta sola e scompattata sotto
 * {@code <cache>/<nome>/<versione>/}. Le volte successive si usa quella copia senza toccare la rete:
 * un server che riavvia non deve rifare venti download, e uno senza connessione deve poter comunque
 * caricare le app che ha già.
 */
public final class HttpRegistry implements PackRegistry
{
    /** oltre questo, l'archivio non è più una libreria: è qualcosa che non vogliamo scompattare */
    private static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private record Published(URI url, String sha256)
    {
    }

    private final URI base;
    private final Path cache;
    private final HttpClient client;
    /** l'indice, letto una volta per sessione: rileggerlo ad ogni dipendenza sarebbe una richiesta a testa */
    private Map<String, Map<Version, Published>> index = null;

    public HttpRegistry(URI base, Path cache)
    {
        this.base = base;
        this.cache = cache;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public List<Version> availableVersions(String name)
    {
        try
        {
            return List.copyOf(index().getOrDefault(name.toLowerCase(Locale.ROOT), Map.of()).keySet());
        }
        catch (IOException e)
        {
            // il registry irraggiungibile non e' "il pack non esiste": chi chiama distingue i due
            // casi solo se il secondo arriva come errore, quindi lo rilanciamo
            throw new blossom.exception.ImportError(blossom.exception.SourcePosition.UNKNOWN,
                    "Cannot read the index of " + describe() + ": " + e.getMessage());
        }
    }

    @Override
    public Path fetch(String name, Version version) throws IOException
    {
        Path destination = cache.resolve(name.toLowerCase(Locale.ROOT)).resolve(version.toString());
        if (Files.isRegularFile(destination.resolve(ProjectManifest.FILE_NAME)))
        {
            return destination; // già in cache: la rete non si tocca
        }
        Published published = index().getOrDefault(name.toLowerCase(Locale.ROOT), Map.of()).get(version);
        if (published == null)
        {
            throw new IOException("the index has no '" + name + "' at version " + version);
        }
        byte[] archive = download(published.url());
        String actual = Integrity.sha256Of(archive);
        if (!actual.equalsIgnoreCase(published.sha256()))
        {
            throw new IOException("the fingerprint of " + published.url() + " does not match the one declared"
                    + " in the index (expected " + published.sha256() + ", found " + actual + "):"
                    + " this is not the published archive and will not be unpacked");
        }
        Path staging = Files.createTempDirectory(cacheParent(), "blossom-fetch-");
        try
        {
            unzip(archive, staging);
            Files.createDirectories(destination.getParent());
            // si sposta solo alla fine: una cartella di cache a metà sarebbe indistinguibile da una
            // completa, e verrebbe riusata così com'è al riavvio dopo
            try
            {
                Files.move(rootOf(staging), destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException e)
            {
                Files.move(rootOf(staging), destination);
            }
        }
        finally
        {
            deleteRecursively(staging);
        }
        return destination;
    }

    private Path cacheParent() throws IOException
    {
        Files.createDirectories(cache);
        return cache;
    }

    /**
     * La radice del progetto dentro l'archivio scompattato.
     *
     * <p>Gli zip di una release contengono quasi sempre una sola cartella con dentro tutto: se il
     * manifesto non è alla radice ma in quell'unica cartella, si scende.
     */
    private static Path rootOf(Path unpacked) throws IOException
    {
        if (Files.isRegularFile(unpacked.resolve(ProjectManifest.FILE_NAME)))
        {
            return unpacked;
        }
        try (var entries = Files.list(unpacked))
        {
            List<Path> children = entries.toList();
            if (children.size() == 1 && Files.isDirectory(children.getFirst())
                    && Files.isRegularFile(children.getFirst().resolve(ProjectManifest.FILE_NAME)))
            {
                return children.getFirst();
            }
        }
        throw new IOException("the archive contains no " + ProjectManifest.FILE_NAME);
    }

    private synchronized Map<String, Map<Version, Published>> index() throws IOException
    {
        if (index != null)
        {
            return index;
        }
        URI indexUri = base.resolve("index.json");
        String body = new String(download(indexUri), StandardCharsets.UTF_8);
        JsonObject root;
        try
        {
            root = JsonParser.parseString(body).getAsJsonObject();
        }
        catch (JsonParseException | IllegalStateException e)
        {
            throw new IOException(indexUri + " is not a valid json index: " + e.getMessage());
        }
        Map<String, Map<Version, Published>> parsed = new LinkedHashMap<>();
        for (String name : root.keySet())
        {
            if (!root.get(name).isJsonObject())
            {
                continue;
            }
            Map<Version, Published> versions = new LinkedHashMap<>();
            JsonObject byVersion = root.getAsJsonObject(name);
            for (String versionText : byVersion.keySet())
            {
                Version version = Version.tryParse(versionText);
                if (version == null || !byVersion.get(versionText).isJsonObject())
                {
                    continue; // una riga malformata non deve rendere illeggibile tutto l'indice
                }
                JsonObject spec = byVersion.getAsJsonObject(versionText);
                if (!spec.has("url") || !spec.has("sha256"))
                {
                    // senza impronta la riga è inutilizzabile: meglio non vederla che scaricarla
                    // senza poterla verificare
                    continue;
                }
                versions.put(version, new Published(base.resolve(spec.get("url").getAsString()),
                        spec.get("sha256").getAsString()));
            }
            parsed.put(name.toLowerCase(Locale.ROOT), Map.copyOf(versions));
        }
        index = Map.copyOf(parsed);
        return index;
    }

    private byte[] download(URI uri) throws IOException
    {
        if (!uri.getScheme().equals("https") && !uri.getScheme().equals("http"))
        {
            throw new IOException(uri + ": only http and https are allowed");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<byte[]> response;
        try
        {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading " + uri);
        }
        if (response.statusCode() != 200)
        {
            throw new IOException(uri + " responded " + response.statusCode());
        }
        if (response.body().length > MAX_ARCHIVE_BYTES)
        {
            throw new IOException(uri + " is larger than " + (MAX_ARCHIVE_BYTES / 1024 / 1024) + " MB");
        }
        return response.body();
    }

    /**
     * Scompatta l'archivio, rifiutando le voci che uscirebbero dalla cartella di destinazione.
     *
     * <p>Il controllo sul percorso non è una precauzione teorica: uno zip con dentro
     * {@code ../../mods/qualcosa.jar} è il modo classico di far scrivere un file dove non si deve, e
     * qui dall'altra parte c'è la cartella di un server.
     */
    private static void unzip(byte[] archive, Path destination) throws IOException
    {
        Path root = destination.normalize();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root))
                {
                    throw new IOException("the archive contains an entry that escapes the destination directory: " + entry.getName());
                }
                if (entry.isDirectory())
                {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                total += copyLimited(zip, target, MAX_ARCHIVE_BYTES - total);
            }
        }
    }

    /** Copia una voce, fermandosi se lo scompattato supera il limite (una zip bomb è compressibile). */
    private static long copyLimited(InputStream source, Path target, long remaining) throws IOException
    {
        try (var out = Files.newOutputStream(target))
        {
            byte[] buffer = new byte[8192];
            long written = 0;
            int read;
            while ((read = source.read(buffer)) > 0)
            {
                written += read;
                if (written > remaining)
                {
                    throw new IOException("the unpacked archive exceeds the size limit");
                }
                out.write(buffer, 0, read);
            }
            return written;
        }
    }

    private static void deleteRecursively(Path root) throws IOException
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (var walk = Files.walk(root))
        {
            List<Path> paths = new ArrayList<>(walk.toList());
            java.util.Collections.reverse(paths);
            for (Path path : paths)
            {
                Files.deleteIfExists(path);
            }
        }
    }

    @Override
    public String describe()
    {
        return "registry " + base;
    }
}
