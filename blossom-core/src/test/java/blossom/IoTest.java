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
 * {@code core.io}: il disco fuori dalla cartella dell'app, dentro confini dichiarati.
 *
 * <p>Metà dei test riguarda quello che <em>non</em> deve funzionare, che è il punto della feature:
 * l'accesso al disco senza confini è la cosa che nessuno vuole su un server.
 */
class IoTest
{
    private static final String IMPORT = "import \"core.io\" as io; ";

    private static Interpreter withRoots(Path storage, Map<String, Path> roots, Set<String> writable)
    {
        Interpreter interpreter = Interpreter.builder().storage(storage).build();
        ((BlossomServer) interpreter.host().scriptServer()).setIoRoots(new IoRoots(roots, writable));
        return interpreter;
    }

    @Test
    void withoutRootsNothingOpens(@TempDir Path tmp)
    {
        Interpreter interpreter = Interpreter.builder().storage(tmp).build();
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('qualsiasi', 'file.txt')"));
        assertTrue(error.getMessage().contains("No io root"), error.getMessage());
        assertEquals("[]", interpreter.eval(IMPORT + "io.roots()").getString());
    }

    @Test
    void readsAndWritesInsideAWritableRoot(@TempDir Path tmp) throws IOException
    {
        Path shared = Files.createDirectories(tmp.resolve("condiviso"));
        Interpreter interpreter = withRoots(tmp, Map.of("condiviso", shared), Set.of("condiviso"));

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
        Interpreter interpreter = withRoots(tmp, Map.of("dati", data), Set.of());

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
        Interpreter interpreter = withRoots(tmp, Map.of("dati", data), Set.of("dati"));

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
            return; // niente link simbolici su questo sistema: il caso non si puo' provare qui
        }
        Interpreter interpreter = withRoots(tmp, Map.of("dati", data), Set.of("dati"));

        // il confronto testuale sul percorso normalizzato non basta: un link dentro la radice punta
        // fuori senza nessun '..'
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('dati', 'scorciatoia.txt')"));
        assertTrue(error.getMessage().contains("symbolic link"), error.getMessage());
    }

    @Test
    void anUnknownRootSuggestsTheRightOne(@TempDir Path tmp) throws IOException
    {
        Path shared = Files.createDirectories(tmp.resolve("condiviso"));
        Interpreter interpreter = withRoots(tmp, Map.of("condiviso", shared), Set.of());

        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "io.read_text('condivisi', 'x.txt')"));
        assertEquals("condiviso", error.suggestion());
    }

    @Test
    void ioErrorsAreCatchable(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Interpreter interpreter = withRoots(tmp, Map.of("dati", data), Set.of("dati"));

        // un file che non c'e' e' una cosa che uno script puo' ragionevolmente voler gestire
        assertEquals("assente", interpreter.eval(
                IMPORT + "try(io.read_text('dati', 'manca.txt'), 'io_exception', 'assente')").getString());
    }

    @Test
    void jsonRoundTrips(@TempDir Path tmp) throws IOException
    {
        Path data = Files.createDirectories(tmp.resolve("dati"));
        Interpreter interpreter = withRoots(tmp, Map.of("dati", data), Set.of("dati"));

        interpreter.eval(IMPORT + "io.write_json('dati', 'c.json', {'a' -> 1, 'b' -> [1, 2]})");
        assertEquals("1", interpreter.eval(IMPORT + "io.read_json('dati', 'c.json'):'a'").getString());
    }

    @Test
    void aRootOutsideTheConfinementOpensNothing(@TempDir Path tmp) throws IOException
    {
        // sul mod il confine e' la cartella del mondo: una radice dichiarata altrove nel disco del
        // server viene comunque fermata qui, che e' l'ultima linea prima del filesystem
        Path world = Files.createDirectories(tmp.resolve("mondo"));
        Path outside = Files.createDirectories(tmp.resolve("fuori"));
        Files.writeString(outside.resolve("segreto.txt"), "non si legge");
        Interpreter interpreter = Interpreter.builder().storage(world).build();
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
        Interpreter interpreter = Interpreter.builder().storage(world).build();
        ((BlossomServer) interpreter.host().scriptServer())
                .setIoRoots(new IoRoots(Map.of("dati", data), Set.of("dati"), world));

        interpreter.eval(IMPORT + "io.write_text('dati', 'nota.txt', 'ciao')");
        assertEquals("ciao", interpreter.eval(IMPORT + "io.read_text('dati', 'nota.txt')").getString());
    }

    @Test
    void noBuiltinNameIsRegisteredTwice()
    {
        // core.io ha dovuto chiamarsi 'delete_file' e non 'delete' proprio per questo: le
        // implementazioni stanno in una mappa piatta, e la seconda registrazione vincerebbe in
        // silenzio
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
