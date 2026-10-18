package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Interpreter;

/** {@code /blossom script explain}: the tree the parser builds, written back as code. */
class ExplainTest
{
    private static String explain(String code, String style)
    {
        Context context = new Context(Interpreter.standalone().host());
        return new Expression("").explain(context, code, null, style).stream()
                .map(token -> token.display.isEmpty() ? token.surface : token.display)
                .collect(Collectors.joining());
    }

    @Test
    void theCanonicalFormKeepsTheBracketsThatChangeTheMeaning()
    {
        assertEquals("'sum ' + (a + b)", explain("'sum ' + (a + b)", "canonical"));
        // on the left the brackets of the same precedence say nothing, and go
        assertEquals("a - b - c", explain("(a - b) - c", "canonical"));
        assertEquals("a - (b - c)", explain("a - (b - c)", "canonical"));
        assertEquals("1 + 2 * 3", explain("1 + 2 * 3", "canonical"));
        assertEquals("(1 + 2) * 3", explain("(1 + 2) * 3", "canonical"));
    }

    @Test
    void listsShowTheFunctionTheyBecome()
    {
        assertEquals("a = l(1, 2)", explain("a = [1, 2]", "canonical"));
    }
}
