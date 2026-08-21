package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.ArityError;
import blossom.exception.BlossomError;
import blossom.exception.BlossomRuntimeError;
import blossom.exception.ImportError;
import blossom.exception.IndexOutOfBoundsError;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.TypeMismatchError;
import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Interpreter;

/**
 * Un test per ogni categoria di errore elencata in PIANO_LINGUAGGIO.md §4, più il formato del
 * messaggio.
 *
 * <p>Verificano due cose distinte: che la categoria sia quella giusta (perché è su quella che si
 * fanno i {@code catch} mirati) e che il messaggio dica abbastanza a un umano.
 */
class ErrorsTest
{
    private static BlossomError errorFrom(String code)
    {
        return assertThrows(BlossomError.class, () -> Interpreter.standalone().eval(code));
    }

    @Test
    void parseError()
    {
        ParseError error = assertThrows(ParseError.class, () -> Interpreter.standalone().eval("1 +"));
        assertTrue(error.position().isKnown(), "un errore di sintassi conosce sempre la sua posizione");
    }

    @Test
    void arityError()
    {
        ArityError error = assertThrows(ArityError.class, () -> Interpreter.standalone().eval("f(a, b) -> a + b; f(1)"));
        assertTrue(error.getMessage().contains("2"), error.getMessage());
        assertTrue(error.getMessage().contains("arguments"), error.getMessage());
    }

    @Test
    void typeMismatchError()
    {
        TypeMismatchError error = assertThrows(TypeMismatchError.class,
                () -> Interpreter.standalone().eval("import \"core.math\" as math; math.sqrt('non un numero')"));
        assertTrue(error.getMessage().toLowerCase().contains("numeric"), error.getMessage());
    }

    @Test
    void indexOutOfBoundsError()
    {
        assertThrows(IndexOutOfBoundsError.class, () -> Interpreter.standalone().eval("import \"core.list\" as list; l = [1, 2]; list.remove(l, 7)"));
    }

    @Test
    void undefinedSymbolError(@TempDir Path tmp) throws IOException
    {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("blossom.json"), "{\"name\": \"t\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\"}");
        Files.writeString(tmp.resolve("src/lib.bs"), "module lib;\n\npub fn add(a, b) -> a + b;\n");
        Files.writeString(tmp.resolve("src/main.bs"), "module main;\n\nimport \"lib\" as lib;\n\npub let v = lib.ad(1, 2);\n");

        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class, () -> Interpreter.forProject(tmp));
        assertEquals("add", error.suggestion());
    }

    @Test
    void importError(@TempDir Path tmp) throws IOException
    {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("blossom.json"), "{\"name\": \"t\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\"}");
        Files.writeString(tmp.resolve("src/main.bs"), "module main;\n\nimport \"non_esiste\" as x;\n");

        ImportError error = assertThrows(ImportError.class, () -> Interpreter.forProject(tmp));
        assertTrue(error.getMessage().contains("non_esiste"), error.getMessage());
    }

    @Test
    void genericRuntimeErrorCarriesASubcode()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> Interpreter.standalone().eval("break()"));
        assertEquals("control_flow", error.subcode());
        assertTrue(error.kind().contains("control_flow"), error.kind());
    }

    // --- formato del messaggio ---

    @Test
    void formatsLikeThePlanRequires()
    {
        BlossomError error = new UndefinedSymbolError(
                new SourcePosition("src/math/vector.bs", 4, 12),
                "Simbolo 'ad' non trovato nel modulo 'math.vector'.",
                "add");
        error.pushFrame("math.vector.add", new SourcePosition("src/math/vector.bs", 4, 12));
        error.pushFrame("main", new SourcePosition("src/main.bs", 8, 1));

        String formatted = error.format();
        assertTrue(formatted.startsWith("UndefinedSymbolError"), formatted);
        assertTrue(formatted.contains("in math.vector.add  (src/math/vector.bs:4:12)"), formatted);
        assertTrue(formatted.contains("called from main  (src/main.bs:8:1)"), formatted);
        assertTrue(formatted.contains("Simbolo 'ad' non trovato"), formatted);
        assertTrue(formatted.endsWith("Did you mean: add?"), formatted);
    }

    @Test
    void anErrorRaisedWhileRunningKnowsWhereItHappened()
    {
        BlossomError error = errorFrom("f(a, b) -> a + b; f(1)");
        assertTrue(error.frames().size() >= 1, "l'errore deve avere almeno un frame: " + error.format());
        assertTrue(error.frames().getFirst().position().isKnown(), error.format());
    }

    // --- il suggeritore ---

    @Test
    void usingALibraryFunctionWithoutImportingItSaysWhereItLives()
    {
        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class,
                () -> Interpreter.standalone().eval("sqrt(4)"));
        assertTrue(error.getMessage().contains("core.math"), error.getMessage());
        assertTrue(error.getMessage().contains("import"), error.getMessage());
    }

    @Test
    void suggestsOnlyWhenTheNamesAreCloseEnough()
    {
        assertEquals("add", Suggestions.closestTo("ad", java.util.List.of("add", "sub", "length")));
        assertEquals("length", Suggestions.closestTo("lenght", java.util.List.of("add", "sub", "length")));
        assertEquals(null, Suggestions.closestTo("qwertyuiop", java.util.List.of("add", "sub", "length")));
    }
}
