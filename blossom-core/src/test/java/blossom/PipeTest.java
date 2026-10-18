package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The |> operator. */
class PipeTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void theSubjectBecomesTheFirstArgument()
    {
        assertEquals("[2, 4, 6]",
                eval("import 'core.list' as list; [1,2,3] |> list.map(fn(x) -> x * 2)"));
    }

    @Test
    void threeTransformationsInARow()
    {
        // note: today 'join' is join(separator, list), so the subject is not the first argument and
        // does not chain. Here the chain is
        // measured with the functions that already take the subject first
        assertEquals("[4, 8]",
                eval("import 'core.list' as list; "
                        + "[1,2,3,4] |> list.filter(fn(x) -> x % 2 == 0) |> list.map(fn(x) -> x * 2)"));
    }

    @Test
    void worksWithAUserFunction()
    {
        assertEquals("6", eval("fn double(x) -> x * 2; 3 |> double()"));
    }

    @Test
    void worksWithExtraArguments()
    {
        assertEquals("9", eval("fn sum(a, b) -> a + b; 4 |> sum(5)"));
    }

    @Test
    void aFunctionValueOnTheRightIsCalledAnyway()
    {
        assertEquals("6", eval("f = fn(x) -> x * 2; 3 |> f"));
    }

    @Test
    void bindsTighterThanAssignment()
    {
        assertEquals("6", eval("fn double(x) -> x * 2; y = 3 |> double(); y"));
    }

    @Test
    void bindsLooserThanDisjunction()
    {
        assertEquals("2", eval("fn double(x) -> x * 2; (0 || 1) |> double()"));
    }
}
