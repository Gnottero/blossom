package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import blossom.exception.BlossomError;
import blossom.exception.ParseError;
import blossom.interpreter.Interpreter;

/**
 * Test del core estratto: girano sulla sola JVM, senza Minecraft e senza scaricare nulla.
 *
 * <p>Coprono quello che la Fase 1 deve garantire prima di toccare la sintassi: parsing, aritmetica,
 * funzioni, closure, liste e mappe, errori.
 */
class InterpreterTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    /** Come {@link #eval}, ma con la libreria importata: le built-in non sono piu' globali. */
    private static String evalWithStdlib(String code)
    {
        return eval("""
                import "core.list" as list;
                import "core.map" as map;
                import "core.math" as math;
                import "core.string" as string;
                """ + code);
    }

    // --- parsing e aritmetica ---

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
        // il ramo destro non deve essere valutato: se lo fosse, la divisione per zero croakerebbe
        assertEquals("false", eval("false && (1/0)"));
        assertEquals("true", eval("true || (1/0)"));
    }

    // --- variabili e funzioni ---

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
        // 'fact' e' gia' una built-in: una definizione che la calpesta e' un errore, non una ridefinizione
        assertEquals("120", eval("factorial(n) -> if(n < 2, 1, n * factorial(n - 1)); factorial(5)"));
    }

    @Test
    void passesScalarArgumentsByValue()
    {
        assertEquals("1", eval("bump(v) -> (v += 1; null); n = 1; bump(n); n"));
    }

    /**
     * Il nome di una variabile lo sa il nodo che la legge, non il valore: questi casi fissano che
     * assegnare a un parametro resti una faccenda del solo frame che lo ha.
     */
    @Test
    void assigningToAParameterDoesNotReachTheCaller()
    {
        // il parametro ha lo stesso nome della variabile del chiamante: il caso in cui uno scambio
        // di nomi si vedrebbe
        assertEquals("1", eval("bump(n) -> (n = 99; n); n = 1; bump(n); n"));
        assertEquals("99", eval("bump(n) -> (n = 99; n); n = 1; bump(n)"));
        // e con nomi diversi
        assertEquals("1", eval("bump(v) -> (v = 99; v); n = 1; bump(n); n"));
        // un contenitore invece si passa per riferimento, e resta cosi'
        assertEquals("[1, 2, 3]", evalWithStdlib("push(l) -> list.put(l, null, 3); l = [1, 2]; push(l); l"));
        // scambiare due parametri fra loro non deve confondere i nomi
        assertEquals("[2, 1]", eval("exchange(a, b) -> (a <> b; [a, b]); exchange(1, 2)"));
    }

    @Test
    void capturesOuterVariablesExplicitly()
    {
        // outer(...) e' l'unica via per leggere una variabile del frame esterno, e non accetta i global
        assertEquals("7", eval("base = 7; read(outer(base)) -> base; read()"));
    }

    @Test
    void treatsFunctionsAsValues()
    {
        // una funzione si passa per nome o come valore di funzione: 'inc' nudo sarebbe una variabile non definita
        assertEquals("9", eval("apply_twice(f, x) -> call(f, call(f, x)); inc(v) -> v + 3; apply_twice('inc', 3)"));
    }

    // --- collezioni ---

    @Test
    void buildsAndIndexesLists()
    {
        assertEquals("[1, 2, 3]", eval("[1, 2, 3]"));
        assertEquals("2", eval("l = [1, 2, 3]; l:1"));
        assertEquals("3", evalWithStdlib("list.length([1, 2, 3])"));
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
     * Le tre variabili implicite vivono in campi del contesto e non nella mappa delle variabili:
     * questi casi fissano quello che deve continuare a valere comunque.
     */
    @Test
    void implicitVariablesNestAndRestore()
    {
        // l'indice c'e' e conta da zero
        assertEquals("[0, 1, 2]", evalWithStdlib("list.map(['a', 'b', 'c'], _i)"));
        // un ciclo dentro l'altro: quello interno non deve rubare '_' a quello esterno
        assertEquals("[3, 6, 9]", evalWithStdlib("list.map([1, 2, 3], (outer_ = _; list.reduce([1, 1, 1], _a + outer_, 0)))"));
        // finito il ciclo, '_' torna il valore di partenza invece di restare l'ultimo elemento
        assertEquals("0", evalWithStdlib("list.map([1, 2], _ * 2); _"));
        assertEquals("0", evalWithStdlib("for([1, 2, 3], _i); _i"));
        // e sono raggiungibili anche per nome, dove il ciclo le ha messe
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
        assertEquals("false", evalWithStdlib("m = {'a' -> 1}; map.delete(m, 'a'); map.has(m, 'a')"));
    }

    @Test
    void loopsAndControlFlow()
    {
        assertEquals("10", eval("sum = 0; loop(5, sum += _); sum"));
        assertEquals("6", eval("sum = 0; for([1, 2, 3], sum += _); sum"));
        assertEquals("3", eval("i = 0; while(i < 3, 10, i += 1); i"));
        assertEquals("yes", eval("if(1 > 0, 'yes', 'no')"));
    }

    // --- errori ---

    @Test
    void reportsSyntaxErrors()
    {
        ParseError e = assertThrows(ParseError.class, () -> eval("1 +"));
        assertTrue(e.getMessage().length() > 0, "un errore di sintassi deve avere un messaggio");
    }

    @Test
    void reportsUnknownFunctions()
    {
        BlossomError e = assertThrows(BlossomError.class, () -> eval("no_such_function(1)"));
        assertTrue(e.getMessage().contains("no_such_function"), "il messaggio deve nominare il simbolo: " + e.getMessage());
    }

    @Test
    void reportsWrongArity()
    {
        BlossomError e = assertThrows(BlossomError.class, () -> eval("f(a, b) -> a + b; f(1)"));
        assertTrue(e.getMessage().toLowerCase().contains("arguments"), "il messaggio deve parlare di argomenti: " + e.getMessage());
    }

    @Test
    void catchesErrorsFromScript()
    {
        assertEquals("caught", eval("try(throw('boom'), 'caught')"));
    }
}
