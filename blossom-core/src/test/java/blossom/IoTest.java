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
        Path shared = Files.createDirectories(tmp.resolve("condiviso"));
        Interpreter interpreter = withRoots(Map.of("condiviso", shared), Set.of("condiviso"));

        interpreter.eval(IMPORT + "io.write_text('condiviso', 'note/oggi.txt', 'ciao')");
        assertEquals("ciao", Files.readString(shared.resolve("note/oggi.txt")));
        assertEquals("ciao", interpreter.eval(IMPORT + "io.read_text('condiviso', 'note/oggi.txt')").getString());

        interpreter.eval(IMPORT + "io.append_text('condiviso', 'note/oggi.txt', ' mondo')");
        assertEquals("ciao mondo", interpreter.eval(IMPORT + "io.read_text('condiviso', 'note/oggi.txt')").getString());

        assertEquals("[note]", interpreter.eval(IMPORT + "io.list('condiviso', '')").getString());
        assertEquals("true", interpreter.eval(IMPORT + "io.exists('condiviso', 'note/oggi.txt')").getString());
        assertEquals("true", interpreter.eval(IMPORT + "io.delete_file('condiviso', 'note/oggi.txt')").getString());
        assertEquals("false", interpreter.eval(IMPORT + "io.exists('condiviso', 'note/oggi.txt')").getString());
    }

    @Test
    void aReadOnlyRootRefusesEveryWrite(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Files.writeString(data.resolve("letto.txt"), "contenuto");
        Interpreter interpreter = withRoots(Map.of("dati", data), Set.of());

        assertEquals("contenuto", interpreter.eval(IMPORT + "io.read_text('dati', 'letto.txt')").getString());
        assertEquals("false", interpreter.eval(IMPORT + "io.writable('dati')").getString());
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.write_text('dati', 'letto.txt', 'no')"));
        assertTrue(error.getMessage().contains("read-only"), error.getMessage());
        assertEquals("contenuto", Files.readString(data.resolve("letto.txt")), "il file non deve essere stato toccato");
    }

    @Test
    void aPathCannotLeaveItsRoot(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Files.writeString(tmp.resolve("segreto.txt"), "non si legge");
        Interpreter interpreter = withRoots(Map.of("dati", data), Set.of("dati"));

        for (String attempt : new String[] {"../segreto.txt", "sub/../../segreto.txt", "/etc/passwd"})
        {
            BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                    () -> interpreter.eval(IMPORT + "io.read_text('dati', '" + attempt + "')"),
                    "doveva rifiutare " + attempt);
            assertTrue(error.getMessage().contains("escapes root"), attempt + ": " + error.getMessage());
        }
    }

    @Test
    void aSymlinkOutOfTheRootIsRefusedToo(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Files.writeString(tmp.resolve("segreto.txt"), "non si legge");
        try
        {
            Files.createSymbolicLink(data.resolve("scorciatoia.txt"), tmp.resolve("segreto.txt"));
        }
        catch (IOException | UnsupportedOperationException e)
        {
            return; // no symbolic links on this system: this case can't be tested here
        }
        Interpreter interpreter = withRoots(Map.of("dati", data), Set.of("dati"));

        // a textual comparison on the normalized path isn't enough: a link inside the root can
        // point outside without any '..'
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('dati', 'scorciatoia.txt')"));
        assertTrue(error.getMessage().contains("symbolic link"), error.getMessage());
    }

    @Test
    void anUnknownRootSuggestsTheRightOne(@TempDir Path tmp) throws IOException
    {
        Path shared = Files.createDirectories(tmp.resolve("condiviso"));
        Interpreter interpreter = withRoots(Map.of("condiviso", shared), Set.of());

        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('condivisi', 'x.txt')"));
        assertEquals("condiviso", error.suggestion());
    }

    @Test
    void ioErrorsAreCatchable(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Interpreter interpreter = withRoots(Map.of("dati", data), Set.of("dati"));

        // a missing file is something a script might reasonably want to handle
        assertEquals("assente", interpreter.eval(
                IMPORT + "try(io.read_text('dati', 'manca.txt'), 'io_exception', 'assente')").getString());
    }

    @Test
    void jsonRoundTrips(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Interpreter interpreter = withRoots(Map.of("dati", data), Set.of("dati"));

        interpreter.eval(IMPORT + "io.write_json('dati', 'c.json', {'a' -> 1, 'b' -> [1, 2]})");
        assertEquals("1", interpreter.eval(IMPORT + "io.read_json('dati', 'c.json'):'a'").getString());
    }

    @Test
    void aRootOutsideTheConfinementOpensNothing(@TempDir Path tmp) throws IOException
    {
        // in the mod the boundary is the world's folder: a root declared elsewhere on the
        // server's disk still gets stopped here, which is the last line before the filesystem
        Path world = Files.createDirectories(tmp.resolve("mondo"));
        Path outside = Files.createDirectories(tmp.resolve("fuori"));
        Files.writeString(outside.resolve("segreto.txt"), "non si legge");
        Interpreter interpreter = Interpreter.builder().build();
        ((BlossomServer) interpreter.host().scriptServer())
                .setIoRoots(new IoRoots(Map.of("fuori", outside), Set.of(), world));

        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('fuori', 'segreto.txt')"));
        assertTrue(error.getMessage().contains("escapes root"), error.getMessage());
    }

    @Test
    void insideTheConfinementEverythingWorksAsBefore(@TempDir Path tmp) throws IOException
    {
        Path world = Files.createDirectories(tmp.resolve("mondo"));
        Path data = Files.createDirectories(world.resolve("dati"));
        Interpreter interpreter = Interpreter.builder().build();
        ((BlossomServer) interpreter.host().scriptServer())
                .setIoRoots(new IoRoots(Map.of("dati", data), Set.of("dati"), world));

        interpreter.eval(IMPORT + "io.write_text('dati', 'nota.txt', 'ciao')");
        assertEquals("ciao", interpreter.eval(IMPORT + "io.read_text('dati', 'nota.txt')").getString());
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
