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

    // --- una chiamata scritta dopo un valore e' una chiamata, non una parentesi qualunque ---

    @Test
    void aCallWrittenAfterAValueIsACall()
    {
        // le parentesi dopo ')' erano un raggruppamento: la seconda chiamata spariva in silenzio
        assertEquals("5", eval("f = fn() -> fn() -> 5; f()()"));
        assertEquals("42", eval("(fn(x) -> x * 2)(21)"));
        assertEquals("7", eval("(fn() -> 7)()"));
    }

    @Test
    void aFunctionMayReturnAFunctionThatTakesParameters()
    {
        assertEquals("3", eval("add = fn(a) -> fn(b) -> a + b; add(1)(2)"));
        assertEquals("15", eval("fn add(a) -> fn(b) -> a + b; add(10)(5)"));
        assertEquals("6", eval("f = fn(a) -> fn(b) -> fn(c) -> a + b + c; f(1)(2)(3)"));
    }

    @Test
    void aCallOnSomethingThatIsNotAFunctionSaysSo()
    {
        BlossomError error = assertThrows(BlossomError.class, () -> eval("f = fn() -> 5; f()()"));
        assertTrue(error.getMessage().contains("5"), error.getMessage());
    }

    @Test
    void groupingParenthesesStillGroup()
    {
        assertEquals("9", eval("(1 + 2) * 3"));
        assertEquals("21", eval("(1 + 2) * (3 + 4)"));
    }

    // --- gli impliciti non valgono zero dove nessuno li ha legati ---

    @Test
    void anImplicitWithNoLoopAroundItIsAnError()
    {
        // 'fn() -> _ * 2' dava una lista di zeri: la lambda i parametri se li chiama per nome, e
        // gli impliciti li' non arrivano
        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class,
                () -> eval("import \"core.list\" as list; list.map([1, 2, 3], fn() -> _ * 2)"));
        assertTrue(error.getMessage().contains("'_' has no value here"), error.getMessage());
        assertThrows(UndefinedSymbolError.class, () -> eval("_i"));
        assertThrows(UndefinedSymbolError.class, () -> eval("_a"));
    }

    @Test
    void theImplicitsStillWorkWhereTheyAreBound()
    {
        String stdlib = "import \"core.list\" as list; ";
        assertEquals("[2, 4, 6]", eval(stdlib + "list.map([1, 2, 3], _ * 2)"));
        assertEquals("[0, 1]", eval(stdlib + "list.map([10, 20], _i)"));
        assertEquals("6", eval(stdlib + "list.reduce([1, 2, 3], _a + _, 0)"));
        assertEquals("[2, 4, 6]", eval(stdlib + "list.map([1, 2, 3], fn(x) -> x * 2)"));
        assertEquals("[0, 1]", eval(stdlib + "list.map([10, 20], fn(x, i) -> i)"));
    }

    // --- json scrive solo json ---

    @Test
    void jsonRefusesTheNumbersItCannotWrite()
    {
        String json = "import \"core.json\" as json; ";
        assertEquals("caught", eval(json + "try(json.encode(0/0), 'json_error', fn(e) -> 'caught')"));
        assertEquals("caught", eval(json + "try(json.encode(1/0), 'json_error', fn(e) -> 'caught')"));
        assertEquals("caught", eval(json + "try(json.encode([1, 0/0]), 'json_error', fn(e) -> 'caught')"));
        assertEquals("{\"a\":1,\"b\":2.5}", eval(json + "json.encode({'a' -> 1, 'b' -> 2.5})"));
    }

    // --- un segnaposto numerico vuole un numero ---

    @Test
    void aNumericPlaceholderSaysWhatItWasGiven()
    {
        String string = "import \"core.string\" as string; ";
        // 'str(\'%d\', \'x\')' rispondeva '0'
        TypeMismatchError error = assertThrows(TypeMismatchError.class,
                () -> eval(string + "string.str('%d', 'x')"));
        assertTrue(error.getMessage().contains("wants a number"), error.getMessage());
        assertThrows(TypeMismatchError.class, () -> eval(string + "string.str('%.2f', 'abc')"));
        // gli argomenti che avanzano non si buttano via in silenzio
        assertThrows(ArityError.class, () -> eval(string + "string.str('%d', 1, 2)"));
        // un testo scritto come un numero passa, come per gli operatori aritmetici
        assertEquals("5", eval(string + "string.str('%d', '5')"));
        assertEquals("3 apples", eval(string + "string.str('%d apples', 3)"));
    }

    // --- il tubo e la lambda scritta subito dopo ---

    @Test
    void aPipeIntoALambdaWrittenOnTheSpot()
    {
        // '|>' lega piu' stretto di '->': il soggetto finiva fra i parametri della firma
        assertEquals("10", eval("5 |> fn(x) -> x * 2"));
        assertEquals("6", eval("x = 5 |> fn(v) -> v + 1; x"));
        assertEquals("10", eval("5 |> (fn(x) -> x * 2)"));
        assertEquals("[3, 2, 1]", eval(
                "import \"core.list\" as list; [3, 1, 2] |> list.sort() |> list.reverse()"));
    }

    // --- un nome del linguaggio non diventa una variabile ---

    @Test
    void aLanguageNameCannotBeAVariable()
    {
        ParseError error = assertThrows(ParseError.class, () -> eval("print = 1; print"));
        assertTrue(error.getMessage().contains("cannot be the name of a variable"), error.getMessage());
        assertThrows(ParseError.class, () -> eval("if = 1"));
        assertThrows(ParseError.class, () -> eval("for += 1"));
        assertThrows(ParseError.class, () -> eval("fn f(if) -> if"));
        // 'l' e 'm' li costruisce il tokenizer, non li scrive nessuno: restano nomi liberi
        assertEquals("[1, 2]", eval("l = [1, 2]; l"));
        assertEquals("{a: 1}", eval("m = {'a' -> 1}; m"));
    }

    // --- gli spostamenti di bit contano i bit, non quelli che la JVM tiene ---

    @Test
    void aShiftBeyondTheWidthEmptiesTheNumber()
    {
        String math = "import \"core.math\" as math; ";
        // Java maschera l'ampiezza sui 6 bit bassi: '1 << 64' rispondeva 1, '1 << 200' rispondeva 256
        assertEquals("0", eval(math + "math.bitwise_shift_left(1, 64)"));
        assertEquals("0", eval(math + "math.bitwise_shift_left(1, 200)"));
        assertEquals("0", eval(math + "math.bitwise_shift_right(8, 64)"));
        assertEquals("-1", eval(math + "math.bitwise_arithmetic_shift_right(-8, 64)"));
        assertEquals("0", eval(math + "math.bitwise_arithmetic_shift_right(8, 64)"));
        assertThrows(BlossomRuntimeError.class, () -> eval(math + "math.bitwise_shift_left(1, -1)"));
        // dentro i 64 bit non cambia niente
        assertEquals("256", eval(math + "math.bitwise_shift_left(1, 8)"));
        assertEquals("2", eval(math + "math.bitwise_shift_right(8, 2)"));
        // una rotazione e' definita modulo la larghezza: quella resta com'era
        assertEquals("2", eval(math + "math.bitwise_roll_left(1, 65)"));
    }

    // --- quello che '==' chiama uguale e' la stessa chiave ---

    @Test
    void whatEqualityCallsEqualIsTheSameKey()
    {
        String map = "import \"core.map\" as map; ";
        assertEquals("true", eval("'1' == 1"));
        assertEquals("[true, a]", eval(map + "m = {1 -> 'a'}; [map.has(m, '1'), map.get(m, '1')]"));
        assertEquals("[true, a]", eval(map + "m = {'1' -> 'a'}; [map.has(m, 1), map.get(m, 1)]"));
        assertEquals("true", eval("true == 1"));
        assertEquals("[true, a]", eval(map + "m = {true -> 'a'}; [map.has(m, 1), map.get(m, 1)]"));
        assertEquals("1", eval(map + "m = {}; map.put(m, 1, 'a'); map.put(m, '1', 'b'); map.size(m)"));
        // e quello che uguale non e' resta una chiave diversa
        assertEquals("false", eval("'1.0' == 1"));
        assertEquals("2", eval(map + "m = {}; map.put(m, 1, 'a'); map.put(m, 'x', 'b'); map.size(m)"));
    }
}
