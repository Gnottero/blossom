package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import blossom.exception.ParseError;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Interpreter;

/**
 * Tipi opzionali sui parametri: {@code fn f(a: num, b: list) -> ...}.
 *
 * <p>Sono facoltativi davvero: una firma senza annotazioni deve continuare a comportarsi esattamente
 * come prima, e i test lo verificano insieme ai casi nuovi.
 */
class ParameterTypesTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void aDeclaredTypeIsAccepted()
    {
        assertEquals("6", eval("f(a: num, b: num) -> a * b; f(2, 3)"));
        assertEquals("3", eval("import \"core.list\" as list; f(l: list) -> list.size(l); f([1, 2, 3])"));
        assertEquals("ciao", eval("f(s: str) -> s; f('ciao')"));
    }

    @Test
    void aWrongTypeIsRefusedOnTheBoundaryOfTheFunction()
    {
        TypeMismatchError error = assertThrows(TypeMismatchError.class, () -> eval("f(a: num) -> a; f('due')"));
        assertTrue(error.getMessage().contains("'a'"), error.getMessage());
        assertTrue(error.getMessage().contains("number"), error.getMessage());
        assertTrue(error.getMessage().contains("string"), error.getMessage());
        assertTrue(error.position().isKnown(), "l'errore deve dire dove: " + error.format());
    }

    @Test
    void onlySomeParametersNeedAType()
    {
        assertEquals("7", eval("f(a: num, b) -> a + b; f(3, 4)"));
        assertThrows(TypeMismatchError.class, () -> eval("f(a: num, b) -> a + b; f('x', 4)"));
        assertEquals("x4", eval("f(a, b: num) -> a + b; f('x', 4)"));
    }

    @Test
    void anyAcceptsEverythingAndDocumentsTheIntent()
    {
        assertEquals("1", eval("f(a: any) -> a; f(1)"));
        assertEquals("ciao", eval("f(a: any) -> a; f('ciao')"));
    }

    @Test
    void aMisspelledTypeIsCaughtWhenTheFunctionIsDefined()
    {
        // non alla prima chiamata: e' un errore di battitura, e va detto subito
        ParseError error = assertThrows(ParseError.class, () -> eval("f(a: nmu) -> a"));
        assertEquals("num", error.suggestion());
    }

    @Test
    void theDeclaredTypesShowUpWhenTheSignatureIsPrinted()
    {
        // il tipo dichiarato fa parte della firma, quindi deve comparire dove la firma compare:
        // altrimenti un errore di arieta' direbbe meno di quello che il codice dice
        blossom.exception.ArityError error = assertThrows(blossom.exception.ArityError.class,
                () -> eval("f(a: num, b) -> a; g(fun) -> call(fun, 1); g(f)"));
        assertTrue(error.getMessage().contains("a: number"), error.getMessage());
    }

    @Test
    void elementAccessKeepsWorkingOutsideSignatures()
    {
        // ':' resta l'accesso a un elemento dappertutto tranne che in una firma
        assertEquals("20", eval("l = [10, 20, 30]; l:1"));
        assertEquals("2", eval("m = {'a' -> 1, 'b' -> 2}; m:'b'"));
        assertEquals("20", eval("f(l) -> l:1; f([10, 20, 30])"));
    }

    @Test
    void aTypedParameterInsideALambdaWorksToo()
    {
        assertEquals("[2, 4, 6]", eval("import \"core.list\" as list; list.map([1, 2, 3], _ * 2)"));
        assertEquals("4", eval("f(g: func, x: num) -> call(g, x); double(n: num) -> n * 2; f(double, 2)"));
    }

    @Test
    void varargsAndOuterStillWork()
    {
        assertEquals("[2, 3]", eval("f(a: num, ... rest) -> rest; f(1, 2, 3)"));
        assertEquals("10", eval("x = 7; f(a: num, outer(x)) -> a + x; f(3)"));
    }
}
