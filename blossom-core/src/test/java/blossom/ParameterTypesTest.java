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
 * <p>They really are optional: a signature without annotations has to keep behaving exactly as
 * before, and the tests check that along with the new cases.
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
        assertEquals("hello", eval("f(s: str) -> s; f('hello')"));
    }

    @Test
    void aWrongTypeIsRefusedOnTheBoundaryOfTheFunction()
    {
        TypeMismatchError error = assertThrows(TypeMismatchError.class, () -> eval("f(a: num) -> a; f('two')"));
        assertTrue(error.getMessage().contains("'a'"), error.getMessage());
        assertTrue(error.getMessage().contains("number"), error.getMessage());
        assertTrue(error.getMessage().contains("string"), error.getMessage());
        assertTrue(error.position().isKnown(), "the error must say where: " + error.format());
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
        assertEquals("hello", eval("f(a: any) -> a; f('hello')"));
    }

    @Test
    void aMisspelledTypeIsCaughtWhenTheFunctionIsDefined()
    {
        // not on the first call: it is a typo, and it has to be said right away
        ParseError error = assertThrows(ParseError.class, () -> eval("f(a: nmu) -> a"));
        assertEquals("num", error.suggestion());
    }

    @Test
    void theDeclaredTypesShowUpWhenTheSignatureIsPrinted()
    {
        // the declared type is part of the signature, so it has to show wherever the signature
        // shows: otherwise an arity error would say less than the code says
        blossom.exception.ArityError error = assertThrows(blossom.exception.ArityError.class,
                () -> eval("f(a: num, b) -> a; g(fun) -> call(fun, 1); g(f)"));
        assertTrue(error.getMessage().contains("a: number"), error.getMessage());
    }

    @Test
    void elementAccessKeepsWorkingOutsideSignatures()
    {
        // ':' stays element access everywhere except in a signature
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
