package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Functions written without a name. */
class LambdaTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void aLambdaIsCalledRightAway()
    {
        assertEquals("5", eval("f = fn(a, b) -> a + b; f(2, 3)"));
    }

    @Test
    void aLambdaCapturesTheFrameItIsBornIn()
    {
        assertEquals("13", eval("base = 10; f = fn(x) -> x + base; f(3)"));
    }

    @Test
    void aLambdaIsPassedAsAnArgument()
    {
        assertEquals("[2, 4, 6]", eval("import 'core.list' as list; list.map([1,2,3], fn(x) -> x * 2)"));
    }

    @Test
    void aLambdaDoesNotEnterTheSymbolTable()
    {
        // two lambdas with the same shape do not shadow each other
        assertEquals("9", eval("a = fn(x) -> x * 2; b = fn(x) -> x * 3; a(3) + b(1)"));
    }

    @Test
    void aLambdaAcceptsParameterTypes()
    {
        assertEquals("6", eval("f = fn(x: num) -> x * 2; f(3)"));
    }

    @Test
    void aLambdaFilters()
    {
        assertEquals("[2, 4]", eval("import 'core.list' as list; list.filter([1,2,3,4], fn(x) -> x % 2 == 0)"));
    }

    @Test
    void aLambdaReducesWithTheAccumulatorFirst()
    {
        assertEquals("10", eval("import 'core.list' as list; list.reduce([1,2,3,4], fn(a, x) -> a + x, 0)"));
    }

    @Test
    void aLambdaSeesTheIndexAsSecondArgument()
    {
        assertEquals("[0, 1, 2]", eval("import 'core.list' as list; list.map(['a','b','c'], fn(x, i) -> i)"));
    }

    @Test
    void theShortFormWithUnderscoreStays()
    {
        assertEquals("[2, 4, 6]", eval("import 'core.list' as list; list.map([1,2,3], _ * 2)"));
    }

    @Test
    void aCatchCanNameTheError()
    {
        assertEquals("caught: broken",
                eval("try(throw('broken'), fn(e) -> 'caught: ' + e)"));
    }

    @Test
    void aNamedCatchSurvivesALoopInside()
    {
        // with '_' the exception value disappears inside the for: with a name it does not
        assertEquals("[broken, broken]",
                eval("import 'core.list' as list; try(throw('broken'), fn(e) -> list.map([1,2], e))"));
    }

    @Test
    void aCatchWithTwoParametersGetsTheStackInTheSecond()
    {
        assertEquals("true",
                eval("try(throw('broken'), fn(e, tr) -> tr:'token' != null)"));
    }

    @Test
    void fnWithoutArrowIsAClearError()
    {
        Exception e = assertThrows(Exception.class, () -> eval("fn(a, b)"));
        assertEquals(true, e.getMessage().contains("only makes sense before"));
    }
}
