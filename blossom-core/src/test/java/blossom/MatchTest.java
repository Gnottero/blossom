package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The match construct. */
class MatchTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void aLiteralPatternMatchesByEquality()
    {
        assertEquals("one", eval("match(1, 1 -> 'one', 2 -> 'two', else -> 'other')"));
        assertEquals("two", eval("match(2, 1 -> 'one', 2 -> 'two', else -> 'other')"));
    }

    @Test
    void elseIsTheFinalArm()
    {
        assertEquals("other", eval("match(9, 1 -> 'one', else -> 'other')"));
    }

    @Test
    void withNoMatchingArmItIsNull()
    {
        assertEquals("null", eval("match(9, 1 -> 'one')"));
    }

    @Test
    void aNameCapturesTheSubject()
    {
        assertEquals("18", eval("match(9, 0 -> 'zero', d -> d * 2)"));
    }

    @Test
    void aTypedNameMatchesOnlyThatType()
    {
        assertEquals("number 5", eval("match(5, s: str -> 'text ' + s, n: num -> 'number ' + n)"));
        assertEquals("text hello", eval("match('hello', s: str -> 'text ' + s, n: num -> 'number ' + n)"));
    }

    @Test
    void stringsMatchLikeValues()
    {
        assertEquals("greeting", eval("match('hello', 'hello' -> 'greeting', else -> 'what')"));
    }

    @Test
    void theSubjectIsEvaluatedOnce()
    {
        // a list is passed by reference, so the function really sees it
        assertEquals("2", eval("import 'core.list' as list; "
                + "let visits = []; fn count() -> (list.put(visits, null, 1); 5); "
                + "match(count(), 5 -> count(), else -> 0); list.size(visits)"));
    }

    @Test
    void armsNotTakenAreNotEvaluated()
    {
        assertEquals("0", eval("n = 0; match(1, 1 -> 0, 2 -> (n += 1), else -> (n += 1)); n"));
    }

    @Test
    void aMatchFitsInsideAnExpression()
    {
        assertEquals("[one, two]", eval("import 'core.list' as list; "
                + "list.map([1, 2], fn(x) -> match(x, 1 -> 'one', 2 -> 'two', else -> '?'))"));
    }
}
