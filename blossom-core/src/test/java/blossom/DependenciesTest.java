package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.CompatibilityError;
import blossom.exception.ImportError;
import blossom.interpreter.Interpreter;
import blossom.project.ApiLevel;
import blossom.project.PackEnvironment;
import blossom.project.PackRegistry;

/**
 * Dependencies between projects: by path, by installed pack, from a registry; the version
 * constraint that actually gets checked; and transitive resolution.
 */
class DependenciesTest
{
    /** A minimal project: manifest, a module exporting a function. */
    private static void writeProject(Path root, String name, String version, String extraManifest, String... modules)
            throws IOException
    {
        Files.createDirectories(root.resolve("src"));
        // the API level must be declared: without it a project defaults to 1.0 and no longer loads
        String apiLevel = extraManifest.contains("api_level") ? "" : ", \"api_level\": \"2.0\"";
        Files.writeString(root.resolve("blossom.json"), "{\"name\": \"" + name + "\", \"version\": \"" + version
                + "\", \"entrypoint\": \"src/main.bs\"" + apiLevel + extraManifest + "}");
        for (int i = 0; i + 1 < modules.length; i += 2)
        {
            Path file = root.resolve("src").resolve(modules[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, modules[i + 1]);
        }
    }

    /** An environment where the "installed" packs are the subfolders of a given directory. */
    private record Installed(Path directory, @Nullable PackRegistry registry) implements PackEnvironment
    {
        @Override
        public Optional<Path> installedPack(String name)
        {
            Path candidate = directory.resolve(name);
            return Files.isRegularFile(candidate.resolve("blossom.json")) ? Optional.of(candidate) : Optional.empty();
        }

        @Override
        public List<String> installedPackNames()
        {
            try (var entries = Files.list(directory))
            {
                return entries.map(p -> p.getFileName().toString()).sorted().toList();
            }
            catch (IOException e)
            {
                return List.of();
            }
        }
    }

    @Test
    void aPathDependencyIsUsableThroughItsAlias(@TempDir Path tmp) throws IOException
    {
        writeProject(tmp.resolve("utils"), "utils", "1.2.0", "",
                "main.bs", "module main;\n",
                "vector.bs", "module vector;\n\npub fn add(a, b) -> a + b;\n");
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"utils\": {\"path\": \"../utils\", \"version\": \"^1.0.0\"}}",
                "main.bs", "module main;\n\nimport \"utils/vector\" as vec;\n\npub let v = vec.add(2, 3);\n");

        Interpreter interpreter = Interpreter.forProject(tmp.resolve("app"));
        assertEquals("5", interpreter.eval("import \"utils/vector\" as vec; vec.add(2, 3)").getString());
    }

    @Test
    void aVersionThatDoesNotSatisfyTheRangeStopsTheLoad(@TempDir Path tmp) throws IOException
    {
        writeProject(tmp.resolve("utils"), "utils", "2.0.0", "",
                "main.bs", "module main;\n",
                "vector.bs", "module vector;\n\npub fn add(a, b) -> a + b;\n");
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"utils\": {\"path\": \"../utils\", \"version\": \"^1.0.0\"}}",
                "main.bs", "module main;\n\nimport \"utils/vector\" as vec;\n");

        CompatibilityError error = assertThrows(CompatibilityError.class, () -> Interpreter.forProject(tmp.resolve("app")));
        assertTrue(error.getMessage().contains("2.0.0"), error.getMessage());
        assertTrue(error.getMessage().contains("^1.0.0"), error.getMessage());
    }

    @Test
    void aPackDependencyIsFoundByNameAmongTheInstalledOnes(@TempDir Path tmp) throws IOException
    {
        Path installed = tmp.resolve("mondo");
        writeProject(installed.resolve("torre"), "torre", "1.0.0", "",
                "main.bs", "module main;\n",
                "guardia.bs", "module guardia;\n\npub fn allarme() -> 'suona';\n");
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"torre\": {\"pack\": \"torre\", \"version\": \"^1.0.0\"}}",
                "main.bs", "module main;\n\nimport \"torre/guardia\" as g;\n");

        Interpreter interpreter = Interpreter.forProject(tmp.resolve("app"), java.util.function.UnaryOperator.identity(),
                blossom.interpreter.Context::new, new Installed(installed, null));
        assertEquals("suona", interpreter.eval("import \"torre/guardia\" as g; g.allarme()").getString());
    }

    @Test
    void aMissingPackSaysWhatIsInstalled(@TempDir Path tmp) throws IOException
    {
        Path installed = tmp.resolve("mondo");
        writeProject(installed.resolve("torre"), "torre", "1.0.0", "", "main.bs", "module main;\n");
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"torree\": {\"pack\": \"torree\"}}",
                "main.bs", "module main;\n");

        ImportError error = assertThrows(ImportError.class,
                () -> Interpreter.forProject(tmp.resolve("app"), java.util.function.UnaryOperator.identity(),
                        blossom.interpreter.Context::new, new Installed(installed, null)));
        assertEquals("torre", error.suggestion());
    }

    @Test
    void aRegistryDependencyWithoutARegistryIsAClearError(@TempDir Path tmp) throws IOException
    {
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}",
                "main.bs", "module main;\n");

        CompatibilityError error = assertThrows(CompatibilityError.class, () -> Interpreter.forProject(tmp.resolve("app")));
        assertTrue(error.getMessage().contains("no registry is configured"), error.getMessage());
    }

    @Test
    void dependenciesAreResolvedTransitivelyInTheirOwnNamespace(@TempDir Path tmp) throws IOException
    {
        // 'basso' is a dependency of 'medio', which is in turn a dependency of the app: the app
        // must not know that 'basso' exists, and 'medio' calls it with the alias it chose itself
        writeProject(tmp.resolve("basso"), "basso", "1.0.0", "",
                "main.bs", "module main;\n",
                "core.bs", "module core;\n\npub fn base() -> 7;\n");
        writeProject(tmp.resolve("medio"), "medio", "1.0.0",
                ", \"dependencies\": {\"b\": {\"path\": \"../basso\"}}",
                "main.bs", "module main;\n",
                "sopra.bs", "module sopra;\n\nimport \"b/core\" as base;\n\npub fn doppio() -> base.base() * 2;\n");
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"m\": {\"path\": \"../medio\"}}",
                "main.bs", "module main;\n\nimport \"m/sopra\" as sopra;\n");

        Interpreter interpreter = Interpreter.forProject(tmp.resolve("app"));
        assertEquals("14", interpreter.eval("import \"m/sopra\" as sopra; sopra.doppio()").getString());
    }

    @Test
    void aCycleBetweenPacksIsAnError(@TempDir Path tmp) throws IOException
    {
        writeProject(tmp.resolve("a"), "a", "1.0.0", ", \"dependencies\": {\"b\": {\"path\": \"../b\"}}",
                "main.bs", "module main;\n");
        writeProject(tmp.resolve("b"), "b", "1.0.0", ", \"dependencies\": {\"a\": {\"path\": \"../a\"}}",
                "main.bs", "module main;\n");

        ImportError error = assertThrows(ImportError.class, () -> Interpreter.forProject(tmp.resolve("a")));
        assertTrue(error.getMessage().contains("circular pack dependency"), error.getMessage());
    }

    @Test
    void aDependencyCannotDeclareBothPathAndPack(@TempDir Path tmp) throws IOException
    {
        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"x\": {\"path\": \"../x\", \"pack\": \"x\"}}",
                "main.bs", "module main;\n");

        blossom.exception.ParseError error = assertThrows(blossom.exception.ParseError.class,
                () -> Interpreter.forProject(tmp.resolve("app")));
        assertTrue(error.getMessage().contains("two different origins"), error.getMessage());
    }

    @Test
    void anAppFromTheFutureIsRefusedBeforeReadingItsModules(@TempDir Path tmp) throws IOException
    {
        ApiLevel tooNew = new ApiLevel(ApiLevel.CURRENT.major(), ApiLevel.CURRENT.minor() + 1);
        writeProject(tmp.resolve("app"), "app", "0.1.0", ", \"api_level\": \"" + tooNew + "\"",
                "main.bs", "module main;\n\nquesto non e' nemmeno codice valido\n");

        CompatibilityError error = assertThrows(CompatibilityError.class, () -> Interpreter.forProject(tmp.resolve("app")));
        assertTrue(error.getMessage().contains("api_level"), error.getMessage());
    }

    @Test
    void aRegistryProvidesTheHighestCompatibleVersion(@TempDir Path tmp) throws IOException
    {
        Path published = tmp.resolve("registry");
        writeProject(published.resolve("vector-1.0.0"), "vector", "1.0.0", "",
                "main.bs", "module main;\n", "v.bs", "module v;\n\npub fn value() -> 100;\n");
        writeProject(published.resolve("vector-1.4.0"), "vector", "1.4.0", "",
                "main.bs", "module main;\n", "v.bs", "module v;\n\npub fn value() -> 140;\n");
        writeProject(published.resolve("vector-2.0.0"), "vector", "2.0.0", "",
                "main.bs", "module main;\n", "v.bs", "module v;\n\npub fn value() -> 200;\n");

        PackRegistry registry = new PackRegistry()
        {
            @Override
            public List<blossom.project.Version> availableVersions(String name)
            {
                return name.equals("vector")
                        ? List.of(blossom.project.Version.parse("1.0.0"), blossom.project.Version.parse("1.4.0"),
                                blossom.project.Version.parse("2.0.0"))
                        : List.of();
            }

            @Override
            public Path fetch(String name, blossom.project.Version version)
            {
                return published.resolve(name + "-" + version);
            }

            @Override
            public String describe()
            {
                return "il registry di prova";
            }
        };

        writeProject(tmp.resolve("app"), "app", "0.1.0",
                ", \"dependencies\": {\"vector\": {\"version\": \"^1.0.0\"}}",
                "main.bs", "module main;\n\nimport \"vector/v\" as v;\n");

        Interpreter interpreter = Interpreter.forProject(tmp.resolve("app"), java.util.function.UnaryOperator.identity(),
                blossom.interpreter.Context::new, new Installed(tmp.resolve("vuoto"), registry));
        assertEquals("140", interpreter.eval("import \"vector/v\" as v; v.value()").getString());
    }
}
