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
 * A registry reached over http: a static index and some zip archives.
 *
 * <p>The index is {@code <base>/index.json} and nothing else, no API, no server-side state, so
 * anything that serves static files can serve it:
 *
 * <pre>
 * { "vector": { "1.4.0": { "url": "vector-1.4.0.zip", "sha256": "9f86d0..." } } }
 * </pre>
 *
 * <p>{@code url} is relative to the base or absolute. {@code sha256} is <b>mandatory</b>: unpacking
 * an archive without checking it is arbitrary code on a server, and anyone in the middle of the
 * connection could substitute it.
 *
 * <p>Each version is downloaded once and stays under {@code <cache>/<name>/<version>/}: a restart
 * does not redo twenty downloads, and a server without a connection still loads the apps it already has.
 */
public final class HttpRegistry implements PackRegistry
{
    /** beyond this, the archive is no longer a library: it's something we don't want to unpack */
    private static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private record Published(URI url, String sha256)
    {
    }

    private final URI base;
    private final Path cache;
    private final HttpClient client;
    /** the index, read once per session: re-reading it for each dependency would be one request each */
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
            // an unreachable registry is not "the pack doesn't exist": the caller can only tell the
            // two cases apart if the second one arrives as an error, so we rethrow it
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
            return destination; // already in cache: no need to touch the network
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
            // moved only at the very end: a half-written cache directory would be indistinguishable
            // from a complete one, and would be reused as-is on the next restart
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
     * The project's root inside the unpacked archive.
     *
     * <p>A release's zip almost always contains a single directory holding everything: if the
     * manifest is not at the root but inside that one directory, descend into it.
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
                    continue; // one malformed entry must not make the whole index unreadable
                }
                JsonObject spec = byVersion.getAsJsonObject(versionText);
                if (!spec.has("url") || !spec.has("sha256"))
                {
                    // without a fingerprint the entry is unusable: better to skip it than to
                    // download it with no way to verify it
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
     * Unpacks the archive, rejecting entries that would escape the destination directory.
     *
     * <p>The path check is not a theoretical precaution: a zip containing
     * {@code ../../mods/something.jar} is the classic way to make a file get written where it
     * shouldn't, and on the other end here is a server's directory.
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

    /** Copies an entry, stopping if the unpacked size exceeds the limit (a zip bomb compresses well). */
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
