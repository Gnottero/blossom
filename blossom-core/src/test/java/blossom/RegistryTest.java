package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.CompatibilityError;
import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import blossom.project.DirectoryRegistry;
import blossom.project.HttpRegistry;
import blossom.project.Integrity;
import blossom.project.LockFile;
import blossom.project.PackEnvironment;
import blossom.project.PackRegistry;
import blossom.project.Version;

/** Registry (cartella e http), lock file e integrità. */
class RegistryTest
{
    private record Env(@Nullable PackRegistry registry) implements PackEnvironment
    {
        @Override
        public Optional<Path> installedPack(String name)
        {
            return Optional.empty();
        }

        @Override
        public List<String> installedPackNames()
        {
            return List.of();
        }
    }

    private static final String VECTOR_IMPORT = "import \"vector/v\" as v;\n";

    private static void writeProject(Path root, String name, String version, String extra, String moduleBody)
            throws IOException
    {
        writeProject(root, name, version, extra, moduleBody, "");
    }

    /**
     * @param mainImports quello che l'entrypoint importa: un modulo che nessuno importa non viene
     *                    caricato, quindi una dipendenza va nominata da qualche parte per esistere
     */
    private static void writeProject(Path root, String name, String version, String extra, String moduleBody,
            String mainImports) throws IOException
    {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("blossom.json"), "{\"name\": \"" + name + "\", \"version\": \"" + version
                + "\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\"" + extra + "}");
        Files.writeString(root.resolve("src/main.bs"), "module main;\n" + mainImports);
        Files.writeString(root.resolve("src/v.bs"), "module v;\n\n" + moduleBody);
    }

    private static Interpreter load(Path app, PackRegistry registry) throws IOException
    {
        return Interpreter.forProject(app, java.util.function.UnaryOperator.identity(), Context::new, new Env(registry));
    }

    // --- registry a cartella ---

    @Test
    void aDirectoryRegistryPublishesOneFolderPerVersion(@TempDir Path tmp) throws IOException
    {
        Path registryRoot = tmp.resolve("registry");
        writeProject(registryRoot.resolve("vector/1.0.0"), "vector", "1.0.0", "", "pub fn value() -> 100;\n");
        writeProject(registryRoot.resolve("vector/1.4.0"), "vector", "1.4.0", "", "pub fn value() -> 140;\n");
        Files.createDirectories(registryRoot.resolve("vector/appunti")); // non è una versione: va ignorata

        DirectoryRegistry registry = new DirectoryRegistry(registryRoot);
        assertEquals(List.of(Version.parse("1.0.0"), Version.parse("1.4.0")),
                registry.availableVersions("vector").stream().sorted().toList());
        assertTrue(registry.availableVersions("inesistente").isEmpty());

        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
        assertEquals("140", load(tmp.resolve("app"), registry)
                .eval("import \"vector/v\" as v; v.value()").getString());
    }

    @Test
    void aNameThatEscapesTheRegistryFindsNothing(@TempDir Path tmp)
    {
        DirectoryRegistry registry = new DirectoryRegistry(tmp.resolve("registry"));
        assertTrue(registry.availableVersions("../../etc").isEmpty());
    }

    // --- lock ---

    @Test
    void theChosenVersionIsWrittenToTheLockAndReused(@TempDir Path tmp) throws IOException
    {
        Path registryRoot = tmp.resolve("registry");
        writeProject(registryRoot.resolve("vector/1.0.0"), "vector", "1.0.0", "", "pub fn value() -> 100;\n");
        DirectoryRegistry registry = new DirectoryRegistry(registryRoot);

        Path app = tmp.resolve("app");
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
        load(app, registry);

        LockFile lock = LockFile.read(app);
        assertEquals(Version.parse("1.0.0"), lock.of("vector").version());
        assertEquals("registry", lock.of("vector").source());

        // esce una 1.4.0, ma il lock ha gia' deciso: senza aggiornarlo, l'app non cambia da sola
        writeProject(registryRoot.resolve("vector/1.4.0"), "vector", "1.4.0", "", "pub fn value() -> 140;\n");
        assertEquals("100", load(app, registry).eval("import \"vector/v\" as v; v.value()").getString());
    }

    @Test
    void refreshingTheLockPicksTheNewVersion(@TempDir Path tmp) throws IOException
    {
        Path registryRoot = tmp.resolve("registry");
        writeProject(registryRoot.resolve("vector/1.0.0"), "vector", "1.0.0", "", "pub fn value() -> 100;\n");
        DirectoryRegistry registry = new DirectoryRegistry(registryRoot);

        Path app = tmp.resolve("app");
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
        load(app, registry);
        writeProject(registryRoot.resolve("vector/1.4.0"), "vector", "1.4.0", "", "pub fn value() -> 140;\n");

        blossom.project.ProjectLoader.load(app, new Env(registry), true);
        assertEquals(Version.parse("1.4.0"), LockFile.read(app).of("vector").version());
    }

    @Test
    void aLockPointingAtAVersionTheRangeNoLongerAcceptsIsRedone(@TempDir Path tmp) throws IOException
    {
        Path registryRoot = tmp.resolve("registry");
        writeProject(registryRoot.resolve("vector/1.0.0"), "vector", "1.0.0", "", "pub fn value() -> 100;\n");
        writeProject(registryRoot.resolve("vector/2.0.0"), "vector", "2.0.0", "", "pub fn value() -> 200;\n");
        DirectoryRegistry registry = new DirectoryRegistry(registryRoot);

        Path app = tmp.resolve("app");
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
        load(app, registry);

        // il vincolo cambia apposta: il lock non deve impedire il cambiamento voluto
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^2.0.0\"}}", "", VECTOR_IMPORT);
        assertEquals("200", load(app, registry).eval("import \"vector/v\" as v; v.value()").getString());
        assertEquals(Version.parse("2.0.0"), LockFile.read(app).of("vector").version());
    }

    @Test
    void aDependencyChangedAfterBeingLockedIsRefused(@TempDir Path tmp) throws IOException
    {
        Path registryRoot = tmp.resolve("registry");
        Path published = registryRoot.resolve("vector/1.0.0");
        writeProject(published, "vector", "1.0.0", "", "pub fn value() -> 100;\n");
        DirectoryRegistry registry = new DirectoryRegistry(registryRoot);

        Path app = tmp.resolve("app");
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
        load(app, registry);

        // qualcuno tocca la copia pubblicata senza cambiare la versione: e' esattamente quello che
        // l'impronta serve a vedere
        Files.writeString(published.resolve("src/v.bs"), "module v;\n\npub fn value() -> 666;\n");
        CompatibilityError error = assertThrows(CompatibilityError.class, () -> load(app, registry));
        assertTrue(error.getMessage().contains("content changed"), error.getMessage());
    }

    @Test
    void aPathDependencyIsNotLocked(@TempDir Path tmp) throws IOException
    {
        // bloccarla vorrebbe dire far fallire il progetto ad ogni salvataggio della cartella accanto
        writeProject(tmp.resolve("utils"), "utils", "1.0.0", "", "pub fn value() -> 1;\n");
        Path app = tmp.resolve("app");
        writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"utils\": {\"path\": \"../utils\"}}", "",
                "import \"utils/v\" as u;\n");
        load(app, null);
        assertTrue(LockFile.read(app).isEmpty());
        assertFalse(Files.exists(app.resolve(LockFile.FILE_NAME)));
    }

    // --- registry http ---

    @Test
    void anHttpRegistryDownloadsVerifiesAndCaches(@TempDir Path tmp) throws IOException
    {
        byte[] archive = zipOf(java.util.Map.of(
                "vector/blossom.json", "{\"name\": \"vector\", \"version\": \"1.4.0\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\"}",
                "vector/src/main.bs", "module main;\n",
                "vector/src/v.bs", "module v;\n\npub fn value() -> 140;\n"));
        String sha = Integrity.sha256Of(archive);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/index.json", exchange ->
        {
            byte[] body = ("{\"vector\": {\"1.4.0\": {\"url\": \"vector-1.4.0.zip\", \"sha256\": \"" + sha + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/vector-1.4.0.zip", exchange ->
        {
            exchange.sendResponseHeaders(200, archive.length);
            exchange.getResponseBody().write(archive);
            exchange.close();
        });
        server.start();
        try
        {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpRegistry registry = new HttpRegistry(base, tmp.resolve("cache"));
            assertEquals(List.of(Version.parse("1.4.0")), registry.availableVersions("vector"));

            Path app = tmp.resolve("app");
            writeProject(app, "app", "0.1.0", ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}", "", VECTOR_IMPORT);
            assertEquals("140", load(app, registry).eval("import \"vector/v\" as v; v.value()").getString());
            assertTrue(Files.isRegularFile(tmp.resolve("cache/vector/1.4.0/blossom.json")), "va messo in cache");
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void anArchiveWhoseFingerprintDoesNotMatchIsNotUnpacked(@TempDir Path tmp) throws IOException
    {
        byte[] archive = zipOf(java.util.Map.of(
                "blossom.json", "{\"name\": \"vector\", \"version\": \"1.0.0\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\"}",
                "src/main.bs", "module main;\n"));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/index.json", exchange ->
        {
            // l'impronta dichiarata non e' quella dell'archivio servito: e' il caso di chi sta in
            // mezzo alla connessione e sostituisce il file
            byte[] body = ("{\"vector\": {\"1.0.0\": {\"url\": \"v.zip\", \"sha256\": \"" + "00".repeat(32) + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v.zip", exchange ->
        {
            exchange.sendResponseHeaders(200, archive.length);
            exchange.getResponseBody().write(archive);
            exchange.close();
        });
        server.start();
        try
        {
            HttpRegistry registry = new HttpRegistry(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), tmp.resolve("cache"));
            IOException error = assertThrows(IOException.class, () -> registry.fetch("vector", Version.parse("1.0.0")));
            assertTrue(error.getMessage().contains("fingerprint"), error.getMessage());
            assertFalse(Files.exists(tmp.resolve("cache/vector/1.0.0")), "niente deve finire in cache");
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void anEntryWithoutAFingerprintIsIgnored(@TempDir Path tmp) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/index.json", exchange ->
        {
            byte[] body = "{\"vector\": {\"1.0.0\": {\"url\": \"v.zip\"}}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try
        {
            HttpRegistry registry = new HttpRegistry(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), tmp.resolve("cache"));
            assertTrue(registry.availableVersions("vector").isEmpty(),
                    "senza impronta la riga e' inutilizzabile: meglio non vederla");
        }
        finally
        {
            server.stop(0);
        }
    }

    private static byte[] zipOf(java.util.Map<String, String> entries) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out))
        {
            // ordinati: uno zip che cambia ordine ad ogni build cambia impronta senza motivo
            for (String name : new java.util.TreeSet<>(entries.keySet()))
            {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(entries.get(name).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
