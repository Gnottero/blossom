package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Double-quoted strings interpolate. */
class InterpolationTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void aStringWithoutPlaceholdersStaysTheSame()
    {
        assertEquals("hello", eval("\"hello\""));
    }

    @Test
    void aPlaceholderInsertsAVariable()
    {
        assertEquals("n=3", eval("a = 3; \"n=${a}\""));
    }

    @Test
    void aPlaceholderTakesAnExpression()
    {
        assertEquals("n=7", eval("a = 3; \"n=${a + 4}\""));
    }

    @Test
    void placeholdersAndTextAlternate()
    {
        assertEquals("1 and 2 make 3", eval("a = 1; b = 2; \"${a} and ${b} make ${a + b}\""));
    }

    @Test
    void placeholdersAtTheStartAndTheEnd()
    {
        assertEquals("12", eval("a = 1; b = 2; \"${a}${b}\""));
    }

    @Test
    void aCallInsideAPlaceholder()
    {
        assertEquals("double 6", eval("fn double(x) -> x * 2; \"double ${double(3)}\""));
    }

    @Test
    void aSingleQuoteInsideAPlaceholderClosesNothing()
    {
        assertEquals("v=1", eval("m = {'}' -> 1}; \"v=${m:'}'}\""));
    }

    @Test
    void singleQuotesStayLiteral()
    {
        assertEquals("n=${a}", eval("a = 3; 'n=${a}'"));
    }
}
