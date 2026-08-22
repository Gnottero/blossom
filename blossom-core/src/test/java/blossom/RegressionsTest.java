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
import blossom.exception.BlossomRuntimeError;
import blossom.exception.ParseError;
import blossom.exception.TypeMismatchError;
import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Interpreter;
import blossom.project.ApiLevel;

/**
 * I casi che una volta sbagliavano.
 *
 * <p>Ognuno e' un bug trovato provando il linguaggio a mano, non una funzione nuova: stanno insieme
 * perche' quello che li lega e' proprio quello - la suite era verde mentre tutti e dodici erano
 * rotti, e senza questi test tornerebbero senza che nessuno se ne accorga.
 */
class RegressionsTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    // --- i nomi delle funzioni dell'utente non sono quelli delle built-in ---

    @Test
    void aUserFunctionMayCarryTheNameOfALibrarySymbol()
    {
        // 'sum', 'map', 'get', 'size' sono nomi dell'elenco piatto delle implementazioni: la
        // libreria si raggiunge per alias, quindi qui non mascherano niente
        assertEquals("3", eval("fn sum(a, b) -> a + b; sum(1, 2)"));
        assertEquals("42", eval("fn get(a) -> a * 2; get(21)"));
        assertEquals("7", eval("fn size(a) -> 7; size(1)"));
        assertEquals("mine", eval("""
                import "core.list" as list;
                fn map(x) -> 'mine';
                map(1)"""));
    }

    @Test
    void theUserNameAndTheLibraryNameLiveTogether()
    {
        assertEquals("[mine, [2, 4]]", eval("""
                import "core.list" as list;
                fn map(x) -> 'mine';
                [map(1), list.map([1, 2], _ * 2)]"""));
    }

    @Test
    void anUnimportedLibrarySymbolStillSaysWhichModuleToImport()
    {
        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class, () -> eval("sqrt(4)"));
        assertTrue(error.getMessage().contains("core.math"), error.getMessage());
    }

    @Test
    void thePreludeStaysReserved()
    {
        ParseError error = assertThrows(ParseError.class, () -> eval("fn print(a) -> a; 1"));
        assertTrue(error.getMessage().contains("part of the language"), error.getMessage());
    }

    // --- il tokenizer non esce dalla stringa ---

    @Test
    void aPlaceholderMayEndWithWhitespaceOrHoldAMap()
    {
        assertEquals("1", eval("\"${1 }\""));
        assertEquals("brace {k: 1}", eval("\"brace ${ {'k' -> 1} }\""));
        assertEquals("nested in2ner", eval("\"nested ${ \"in${1 + 1}ner\" }\""));
    }

    @Test
    void anEscapedPlaceholderIsLiteralText()
    {
        assertEquals("esc ${x}", eval("\"esc \\${x}\""));
    }

    // --- la costante piegata a parse time non salta il try ---

    @Test
    void aFailingCallWithConstantArgumentsFailsWhereItIsWritten()
    {
        assertEquals("caught", eval("""
                import "core.json" as json;
                try(json.decode('{bad'), 'json_error', 'caught')"""));
    }

    @Test
    void deadCodeDoesNotFailAtLoad()
    {
        assertEquals("safe", eval("""
                import "core.json" as json;
                if(false, json.decode('{bad'), 'safe')"""));
        assertEquals("42", eval("""
                import "core.json" as json;
                fn boom() -> json.decode('{bad');
                42"""));
    }

    @Test
    void aNumberJsonCannotHoldIsAJsonError()
    {
        assertEquals("caught", eval("""
                import "core.json" as json;
                s = '1e999999';
                try(json.decode(s), 'json_error', 'caught')"""));
    }

    // --- l'eccezione di un task arriva a chi lo aspetta ---

    @Test
    void waitingForAFailedTaskGetsItsException()
    {
        assertEquals("caught: inside", eval("""
                import "core.task" as task;
                fn boom() -> throw('inside');
                t = task.start('boom');
                try(task.wait(t), 'exception', fn(e) -> 'caught: ' + e)"""));
    }

    // --- firme ---

    @Test
    void varargsMustBeTheLastParameter()
    {
        ParseError error = assertThrows(ParseError.class, () -> eval("fn f(a, ...rest, b) -> a; f(1, 2)"));
        assertTrue(error.getMessage().contains("last parameter"), error.getMessage());
        assertEquals("[1, [2, 3]]", eval("fn f(a, ...rest) -> [a, rest]; f(1, 2, 3)"));
    }

    @Test
    void aParameterCannotBeDeclaredTwice()
    {
        assertThrows(ParseError.class, () -> eval("fn f(a, a) -> a; f(1, 2)"));
        assertThrows(ParseError.class, () -> eval("g = fn(x, x) -> x; 1"));
        assertThrows(ParseError.class, () -> eval("x = 5; fn h(x, outer(x)) -> x; h(1)"));
    }

    @Test
    void aLambdaInATransformationTakesTheParametersItIsGiven()
    {
        assertThrows(ArityError.class, () -> eval("""
                import "core.list" as list;
                list.map([1, 2], fn(x, i, extra) -> x)"""));
        ArityError error = assertThrows(ArityError.class, () -> eval("""
                import "core.list" as list;
                list.reduce([1, 2], fn(a) -> a, 0)"""));
        assertTrue(error.getMessage().contains("never see the element"), error.getMessage());
        // le forme giuste continuano a passare
        assertEquals("[2, 4]", eval("import \"core.list\" as list; list.map([1, 2], fn(x) -> x * 2)"));
        assertEquals("[0, 1]", eval("import \"core.list\" as list; list.map(['a', 'b'], fn(x, i) -> i)"));
        assertEquals("6", eval("import \"core.list\" as list; list.reduce([1, 2, 3], fn(a, x) -> a + x, 0)"));
    }

    // --- 'size' misura quello che ha una dimensione ---

    @Test
    void sizeRefusesWhatHasNoSize()
    {
        assertEquals("3", eval("import \"core.list\" as list; list.size([1, 2, 3])"));
        assertEquals("5", eval("import \"core.list\" as list; list.size('hello')"));
        assertEquals("0", eval("import \"core.list\" as list; list.size(null)"));
        assertThrows(TypeMismatchError.class, () -> eval("import \"core.list\" as list; list.size(12345)"));
        TypeMismatchError error = assertThrows(TypeMismatchError.class,
                () -> eval("import \"core.list\" as list; list.size(list.range(5))"));
        assertTrue(error.getMessage().contains("lazy list"), error.getMessage());
        assertEquals("5", eval("import \"core.list\" as list; list.size(list.slice(list.range(5), 0, 5))"));
    }

    // --- niente errori interni al posto di errori del linguaggio ---

    @Test
    void swappingTwoPositionsInAListSaysWhatIsWrong()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> eval("import \"core.list\" as list; l = [1, 2]; l:0 <> l:1"));
        assertTrue(error.getMessage().contains("swaps two variables"), error.getMessage());
        assertEquals("[2, 1]", eval("a = 1; b = 2; a <> b; [a, b]"));
    }

    @Test
    void taskErrorsUseTheNameThatIsWritten()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> eval("import \"core.task\" as task; task.wait(5)"));
        assertTrue(error.getMessage().contains("task.wait"), error.getMessage());
    }

    // --- il livello di API di un manifesto che non lo dichiara ---

    @Test
    void theDefaultApiLevelIsTheOneThisRuntimeProvides()
    {
        assertEquals(ApiLevel.CURRENT.major(), ApiLevel.DEFAULT.major());
        assertTrue(ApiLevel.DEFAULT.isSupportedBy(ApiLevel.CURRENT));
    }

    @Test
    void aManifestWithoutApiLevelLoads(@TempDir Path tmp) throws IOException
    {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("blossom.json"), """
                {
                  "name": "no-api-level",
                  "version": "0.1.0",
                  "entrypoint": "src/main.bs"
                }
                """);
        Files.writeString(tmp.resolve("src/main.bs"), "module main;\n1 + 1;\n");
        assertEquals("4", Interpreter.forProject(tmp).eval("2 + 2").getString());
    }
}
