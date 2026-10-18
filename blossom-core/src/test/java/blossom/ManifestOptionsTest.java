package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.BlossomError;
import blossom.exception.ParseError;
import blossom.interpreter.Interpreter;
import blossom.project.ProjectManifest;

/** The manifest keys that came from Scarpet's {@code __config()}: strict, event_priority, requires. */
class ManifestOptionsTest
{
    private static Path project(Path tmp, String manifest, String main) throws IOException
    {
        Path root = tmp.resolve("app");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("blossom.json"), manifest);
        Files.writeString(root.resolve("src/main.bs"), "module main;\n" + main);
        return root;
    }

    @Test
    void strictTurnsAnUnassignedReadIntoAnError(@TempDir Path tmp) throws IOException
    {
        Path loose = project(tmp.resolve("a"), "{\"name\": \"app\"}", "1");
        assertEquals("null", Interpreter.forProject(loose).eval("misspelled").getString());

        Path strict = project(tmp.resolve("b"), "{\"name\": \"app\", \"strict\": true}", "1");
        BlossomError error = assertThrows(BlossomError.class, () -> Interpreter.forProject(strict).eval("misspelled + 1"));
        assertTrue(error.format().contains("misspelled"), error.format());
    }

    @Test
    void priorityAndRequiresAreRead(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, "{\"name\": \"app\", \"event_priority\": 5,"
                + " \"requires\": {\"minecraft\": \">=26.4\", \"carpet\": \"*\"}}", "1");
        ProjectManifest manifest = ProjectManifest.read(root);
        assertEquals(5.0, manifest.eventPriority());
        assertEquals(Map.of("minecraft", ">=26.4", "carpet", "*"), manifest.requires());
        assertTrue(!manifest.strict());
    }

    @Test
    void aMalformedOptionIsALoadError(@TempDir Path tmp) throws IOException
    {
        assertThrows(ParseError.class, () -> ProjectManifest.read(project(tmp.resolve("a"), "{\"name\": \"app\", \"strict\": \"yes\"}", "1")));
        assertThrows(ParseError.class, () -> ProjectManifest.read(project(tmp.resolve("b"), "{\"name\": \"app\", \"requires\": [\"x\"]}", "1")));
        assertThrows(ParseError.class, () -> ProjectManifest.read(project(tmp.resolve("c"), "{\"name\": \"app\", \"event_priority\": \"high\"}", "1")));
    }
}
