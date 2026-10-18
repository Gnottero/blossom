package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Calling a function that arrives as the value of a variable. */
class CallValueTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void aDeclaredFunctionIsCalledAsBefore()
    {
        assertEquals("6", eval("fn triple(x) -> x * 3; triple(2)"));
    }

    @Test
    void aVariableHoldingAFunctionIsCalledDirectly()
    {
        assertEquals("6", eval("fn triple(x) -> x * 3; g = triple; g(2)"));
    }

    @Test
    void aFunctionParameterIsCalledDirectly()
    {
        assertEquals("18", eval("fn triple(x) -> x * 3; fn twice(f, x) -> f(f(x)); twice(triple, 2)"));
    }

    @Test
    void theDeclaredFunctionWinsOverTheVariable()
    {
        assertEquals("6", eval("fn f(x) -> x * 3; f = 'nothing'; f(2)"));
    }
}
