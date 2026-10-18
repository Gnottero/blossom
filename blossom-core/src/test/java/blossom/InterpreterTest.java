package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import blossom.exception.BlossomError;
import blossom.exception.ParseError;
import blossom.interpreter.Interpreter;

/**
 * Tests for the extracted core: they run on the JVM alone, with no Minecraft and no downloads.
 *
 * <p>They cover the basics everything else stands on: parsing, arithmetic, functions,
 * closures, lists and maps, errors.
 */
class InterpreterTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    /** Like {@link #eval}, but with the library imported: the built-ins are no longer global. */
    private static String evalWithStdlib(String code)
    {
        return eval("""
                import "core.list" as list;
                import "core.map" as map;
                import "core.math" as math;
                import "core.string" as string;
                """ + code);
    }

    // --- parsing and arithmetic ---

    @Test
    void parsesAndEvaluatesArithmetic()
    {
        assertEquals("3", eval("1 + 2"));
        assertEquals("14", eval("2 + 3 * 4"));
        assertEquals("20", eval("(2 + 3) * 4"));
        assertEquals("8", eval("2 ^ 3"));
        assertEquals("1", eval("7 % 3"));
        assertEquals("2.5", eval("5 / 2"));
    }

    @Test
    void respectsUnaryMinusAndComparisons()
    {
        assertEquals("-5", eval("-5"));
        assertEquals("true", eval("1 < 2"));
        assertEquals("false", eval("2 <= 1"));
        assertEquals("true", eval("'a' == 'a'"));
    }

    @Test
    void evaluatesBooleanOperatorsLazily()
    {
        // the right-hand branch must not be evaluated: if it were, the division by zero would throw
        assertEquals("false", eval("false && (1/0)"));
        assertEquals("true", eval("true || (1/0)"));
    }

    // --- variables and functions ---

    @Test
    void keepsStateBetweenEvaluationsOfTheSameInterpreter()
    {
        Interpreter interpreter = Interpreter.standalone();
        interpreter.eval("global_counter = 0");
        interpreter.eval("global_counter += 5");
        assertEquals("5", interpreter.eval("global_counter").getString());
    }

    @Test
    void definesAndCallsFunctions()
    {
        assertEquals("25", eval("square(x) -> x * x; square(5)"));
        // 'fact' is already a built-in: a definition that shadows it is an error, not a redefinition
        assertEquals("120", eval("factorial(n) -> if(n < 2, 1, n * factorial(n - 1)); factorial(5)"));
    }

    @Test
    void passesScalarArgumentsByValue()
    {
        assertEquals("1", eval("bump(v) -> (v += 1; null); n = 1; bump(n); n"));
    }

    /**
     * The node that reads a variable knows its name, not the value: these cases pin down that
     * assigning to a parameter stays a matter for the frame that owns it alone.
     */
    @Test
    void assigningToAParameterDoesNotReachTheCaller()
    {
        // the parameter has the same name as the caller's variable: the case where a name mix-up
        // would show
        assertEquals("1", eval("bump(n) -> (n = 99; n); n = 1; bump(n); n"));
        assertEquals("99", eval("bump(n) -> (n = 99; n); n = 1; bump(n)"));
        // and with different names
        assertEquals("1", eval("bump(v) -> (v = 99; v); n = 1; bump(n); n"));
        // a container, instead, is passed by reference, and stays that way
        assertEquals("[1, 2, 3]", evalWithStdlib("push(l) -> list.put(l, null, 3); l = [1, 2]; push(l); l"));
        // swapping two parameters with each other must not mix up the names
        assertEquals("[2, 1]", eval("exchange(a, b) -> (a <> b; [a, b]); exchange(1, 2)"));
    }

    @Test
    void capturesOuterVariablesExplicitly()
    {
        // outer(...) is the only way to read a variable from the outer frame, and it doesn't accept globals
        assertEquals("7", eval("base = 7; read(outer(base)) -> base; read()"));
    }

    @Test
    void treatsFunctionsAsValues()
    {
        // a function is passed by name or as a function value: bare 'inc' would be an undefined variable
        assertEquals("9", eval("apply_twice(f, x) -> call(f, call(f, x)); inc(v) -> v + 3; apply_twice('inc', 3)"));
    }

    // --- collections ---

    @Test
    void buildsAndIndexesLists()
    {
        assertEquals("[1, 2, 3]", eval("[1, 2, 3]"));
        assertEquals("2", eval("l = [1, 2, 3]; l:1"));
        assertEquals("3", evalWithStdlib("list.size([1, 2, 3])"));
        assertEquals("[1, 2, 3, 4]", evalWithStdlib("l = [1, 2, 3]; list.put(l, null, 4); l"));
    }

    @Test
    void mapsFiltersAndReduces()
    {
        assertEquals("[2, 4, 6]", evalWithStdlib("list.map([1, 2, 3], _ * 2)"));
        assertEquals("[2, 4]", evalWithStdlib("list.filter([1, 2, 3, 4], _ % 2 == 0)"));
        assertEquals("10", evalWithStdlib("list.reduce([1, 2, 3, 4], _a + _, 0)"));
    }

    /**
     * The three implicit variables live in fields of the context, not in the variable map: these
     * cases pin down what must keep holding regardless.
     */
    @Test
    void implicitVariablesNestAndRestore()
    {
        // the index is there and counts from zero
        assertEquals("[0, 1, 2]", evalWithStdlib("list.map(['a', 'b', 'c'], _i)"));
        // a loop inside another: the inner one must not steal '_' from the outer one
        assertEquals("[3, 6, 9]", evalWithStdlib("list.map([1, 2, 3], (outer_ = _; list.reduce([1, 1, 1], _a + outer_, 0)))"));
        // once the loop is over '_' has no value again, instead of staying the last element: it is
        // the loop that binds it, so outside one reading it is a mistake and says so
        BlossomError leaked = assertThrows(BlossomError.class,
                () -> evalWithStdlib("list.map([1, 2], _ * 2); _"));
        assertTrue(leaked.getMessage().contains("'_' has no value here"), leaked.getMessage());
        BlossomError leakedIndex = assertThrows(BlossomError.class,
                () -> evalWithStdlib("for([1, 2, 3], _i); _i"));
        assertTrue(leakedIndex.getMessage().contains("'_i' has no value here"), leakedIndex.getMessage());
        // and they're also reachable by name, wherever the loop put them
        assertEquals("[2, 3]", eval("""
                import "core.list" as list;
                import "core.system" as system;
                list.map([1, 2], system.var('_') + 1)"""));
        assertEquals("[1, 2, 3]", evalWithStdlib("list.map([1, 2, 3], _i + 1)"));
    }

    @Test
    void loopsSeeTheirCounter()
    {
        assertEquals("6", evalWithStdlib("total = 0; loop(4, total += _); total"));
        assertEquals("3", evalWithStdlib("i = 0; while(i < 3, 10, i += 1); i"));
        assertEquals("[0, 1, 2]", evalWithStdlib("seen = []; for([9, 9, 9], list.put(seen, null, _i)); seen"));
    }

    @Test
    void buildsAndQueriesMaps()
    {
        assertEquals("1", eval("m = {'a' -> 1}; m:'a'"));
        assertEquals("true", evalWithStdlib("m = {'a' -> 1}; map.has(m, 'a')"));
        assertEquals("false", evalWithStdlib("m = {'a' -> 1}; map.remove(m, 'a'); map.has(m, 'a')"));
    }

    @Test
    void loopsAndControlFlow()
    {
        assertEquals("10", eval("sum = 0; loop(5, sum += _); sum"));
        assertEquals("6", eval("sum = 0; for([1, 2, 3], sum += _); sum"));
        assertEquals("3", eval("i = 0; while(i < 3, 10, i += 1); i"));
        assertEquals("yes", eval("if(1 > 0, 'yes', 'no')"));
    }

    // --- errors ---

    @Test
    void reportsSyntaxErrors()
    {
        ParseError e = assertThrows(ParseError.class, () -> eval("1 +"));
        assertTrue(e.getMessage().length() > 0, "a syntax error must have a message");
    }

    @Test
    void reportsUnknownFunctions()
    {
        BlossomError e = assertThrows(BlossomError.class, () -> eval("no_such_function(1)"));
        assertTrue(e.getMessage().contains("no_such_function"), "the message must name the symbol: " + e.getMessage());
    }

    @Test
    void reportsWrongArity()
    {
        BlossomError e = assertThrows(BlossomError.class, () -> eval("f(a, b) -> a + b; f(1)"));
        assertTrue(e.getMessage().toLowerCase().contains("arguments"), "the message must talk about arguments: " + e.getMessage());
    }

    @Test
    void catchesErrorsFromScript()
    {
        assertEquals("caught", eval("try(throw('boom'), 'caught')"));
    }
}
