package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.BlossomServer;
import blossom.interpreter.Expression;
import blossom.interpreter.Interpreter;
import blossom.language.IoRoots;

/**
 * {@code core.io}: disk access outside the app's folder, within declared boundaries.
 *
 * <p>Half the tests cover what must <em>not</em> work, which is the whole point of the feature:
 * unbounded disk access is the thing nobody wants on a server.
 */
class IoTest
{
    private static final String IMPORT = "import \"core.io\" as io; ";

    private static Interpreter withRoots(Map<String, Path> roots, Set<String> writable)
    {
        Interpreter interpreter = Interpreter.builder().build();
        ((BlossomServer) interpreter.host().scriptServer()).setIoRoots(new IoRoots(roots, writable));
        return interpreter;
    }

    @Test
    void withoutRootsNothingOpens(@TempDir Path tmp)
    {
        Interpreter interpreter = Interpreter.builder().build();
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('qualsiasi', 'file.txt')"));
        assertTrue(error.getMessage().contains("No io root"), error.getMessage());
        assertEquals("[]", interpreter.eval(IMPORT + "io.roots()").getString());
    }

    @Test
    void readsAndWritesInsideAWritableRoot(@TempDir Path tmp) throws IOException
    {
        Path shared = Files.createDirectories(tmp.resolve("shared"));
        Interpreter interpreter = withRoots(Map.of("shared", shared), Set.of("shared"));

        interpreter.eval(IMPORT + "io.write_text('shared', 'notes/today.txt', 'hello')");
        assertEquals("hello", Files.readString(shared.resolve("notes/today.txt")));
        assertEquals("hello", interpreter.eval(IMPORT + "io.read_text('shared', 'notes/today.txt')").getString());

        interpreter.eval(IMPORT + "io.append_text('shared', 'notes/today.txt', ' world')");
        assertEquals("hello world", interpreter.eval(IMPORT + "io.read_text('shared', 'notes/today.txt')").getString());

        assertEquals("[notes]", interpreter.eval(IMPORT + "io.list('shared', '')").getString());
        assertEquals("true", interpreter.eval(IMPORT + "io.exists('shared', 'notes/today.txt')").getString());
        assertEquals("true", interpreter.eval(IMPORT + "io.delete_file('shared', 'notes/today.txt')").getString());
        assertEquals("false", interpreter.eval(IMPORT + "io.exists('shared', 'notes/today.txt')").getString());
    }

    @Test
    void aReadOnlyRootRefusesEveryWrite(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Files.writeString(data.resolve("read.txt"), "content");
        Interpreter interpreter = withRoots(Map.of("data", data), Set.of());

        assertEquals("content", interpreter.eval(IMPORT + "io.read_text('data', 'read.txt')").getString());
        assertEquals("false", interpreter.eval(IMPORT + "io.writable('data')").getString());
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.write_text('data', 'read.txt', 'no')"));
        assertTrue(error.getMessage().contains("read-only"), error.getMessage());
        assertEquals("content", Files.readString(data.resolve("read.txt")), "the file must not have been touched");
    }

    @Test
    void aPathCannotLeaveItsRoot(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Files.writeString(tmp.resolve("secret.txt"), "not readable");
        Interpreter interpreter = withRoots(Map.of("data", data), Set.of("data"));

        for (String attempt : new String[] {"../secret.txt", "sub/../../secret.txt", "/etc/passwd"})
        {
            BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                    () -> interpreter.eval(IMPORT + "io.read_text('data', '" + attempt + "')"),
                    "doveva rifiutare " + attempt);
            assertTrue(error.getMessage().contains("escapes root"), attempt + ": " + error.getMessage());
        }
    }

    @Test
    void aSymlinkOutOfTheRootIsRefusedToo(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Files.writeString(tmp.resolve("secret.txt"), "not readable");
        try
        {
            Files.createSymbolicLink(data.resolve("shortcut.txt"), tmp.resolve("secret.txt"));
        }
        catch (IOException | UnsupportedOperationException e)
        {
            return; // no symbolic links on this system: this case can't be tested here
        }
        Interpreter interpreter = withRoots(Map.of("data", data), Set.of("data"));

        // a textual comparison on the normalized path isn't enough: a link inside the root can
        // point outside without any '..'
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('data', 'shortcut.txt')"));
        assertTrue(error.getMessage().contains("symbolic link"), error.getMessage());
    }

    @Test
    void anUnknownRootSuggestsTheRightOne(@TempDir Path tmp) throws IOException
    {
        Path shared = Files.createDirectories(tmp.resolve("shared"));
        Interpreter interpreter = withRoots(Map.of("shared", shared), Set.of());

        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('shraed', 'x.txt')"));
        assertEquals("shared", error.suggestion());
    }

    @Test
    void ioErrorsAreCatchable(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Interpreter interpreter = withRoots(Map.of("data", data), Set.of("data"));

        // a missing file is something a script might reasonably want to handle
        assertEquals("absent", interpreter.eval(
                IMPORT + "try(io.read_text('data', 'missing.txt'), 'io_exception', 'absent')").getString());
    }

    @Test
    void jsonRoundTrips(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("data"));
        Interpreter interpreter = withRoots(Map.of("data", data), Set.of("data"));

        interpreter.eval(IMPORT + "io.write_json('data', 'c.json', {'a' -> 1, 'b' -> [1, 2]})");
        assertEquals("1", interpreter.eval(IMPORT + "io.read_json('data', 'c.json'):'a'").getString());
    }

    @Test
    void aRootOutsideTheConfinementOpensNothing(@TempDir Path tmp) throws IOException
    {
        // in the mod the boundary is the world's folder: a root declared elsewhere on the
        // server's disk still gets stopped here, which is the last line before the filesystem
        Path world = Files.createDirectories(tmp.resolve("world"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "not readable");
        Interpreter interpreter = Interpreter.builder().build();
        ((BlossomServer) interpreter.host().scriptServer())
                .setIoRoots(new IoRoots(Map.of("outside", outside), Set.of(), world));

        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('outside', 'secret.txt')"));
        assertTrue(error.getMessage().contains("escapes root"), error.getMessage());
    }

    @Test
    void insideTheConfinementEverythingWorksAsBefore(@TempDir Path tmp) throws IOException
    {
        Path world = Files.createDirectories(tmp.resolve("world"));
        Path data = Files.createDirectories(world.resolve("data"));
        Interpreter interpreter = Interpreter.builder().build();
        ((BlossomServer) interpreter.host().scriptServer())
                .setIoRoots(new IoRoots(Map.of("data", data), Set.of("data"), world));

        interpreter.eval(IMPORT + "io.write_text('data', 'note.txt', 'hello')");
        assertEquals("hello", interpreter.eval(IMPORT + "io.read_text('data', 'note.txt')").getString());
    }

    @Test
    void noBuiltinNameIsRegisteredTwice()
    {
        // core.io had to be called 'delete_file' and not 'delete' for exactly this reason: the
        // implementations live in a flat map, and the second registration would silently win
        Expression expression = new Expression("0");
        assertEquals(Set.of(), expression.redefinedBuiltins());
    }

    @Test
    void deleteOnCollectionsStillWorks()
    {
        assertEquals("[1, 3]", Interpreter.standalone()
                .eval("import \"core.list\" as list; l = [1, 2, 3]; list.remove(l, 1); l").getString());
    }
}
