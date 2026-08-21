package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** C.1 - funzioni scritte senza nome. */
class LambdaTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void unaLambdaSiChiamaSubito()
    {
        assertEquals("5", eval("f = fn(a, b) -> a + b; f(2, 3)"));
    }

    @Test
    void unaLambdaCatturaIlFrameInCuiNasce()
    {
        assertEquals("13", eval("base = 10; f = fn(x) -> x + base; f(3)"));
    }

    @Test
    void unaLambdaPassaComeArgomento()
    {
        assertEquals("[2, 4, 6]", eval("import 'core.list' as list; list.map([1,2,3], fn(x) -> x * 2)"));
    }

    @Test
    void unaLambdaNonEntraNellaTabellaDeiSimboli()
    {
        // due lambda con la stessa forma non si mascherano a vicenda
        assertEquals("9", eval("a = fn(x) -> x * 2; b = fn(x) -> x * 3; a(3) + b(1)"));
    }

    @Test
    void unaLambdaAccettaITipiSuiParametri()
    {
        assertEquals("6", eval("f = fn(x: num) -> x * 2; f(3)"));
    }

    @Test
    void unaLambdaFiltra()
    {
        assertEquals("[2, 4]", eval("import 'core.list' as list; list.filter([1,2,3,4], fn(x) -> x % 2 == 0)"));
    }

    @Test
    void unaLambdaRiduceConLAccumulatorePrimo()
    {
        assertEquals("10", eval("import 'core.list' as list; list.reduce([1,2,3,4], fn(a, x) -> a + x, 0)"));
    }

    @Test
    void unaLambdaVedeIndiceComeSecondoArgomento()
    {
        assertEquals("[0, 1, 2]", eval("import 'core.list' as list; list.map(['a','b','c'], fn(x, i) -> i)"));
    }

    @Test
    void laFormaCortaConTrattinoBassoResta()
    {
        assertEquals("[2, 4, 6]", eval("import 'core.list' as list; list.map([1,2,3], _ * 2)"));
    }

    @Test
    void unCatchPuoDareUnNomeAllErrore()
    {
        assertEquals("preso: rotto",
                eval("try(throw('rotto'), fn(e) -> 'preso: ' + e)"));
    }

    @Test
    void unCatchConNomeSopravviveAUnCicloDentro()
    {
        // con '_' il valore dell'eccezione sparisce dentro il for: con un nome no
        assertEquals("[rotto, rotto]",
                eval("import 'core.list' as list; try(throw('rotto'), fn(e) -> list.map([1,2], e))"));
    }

    @Test
    void unCatchConDueParametriRiceveLoStackNelSecondo()
    {
        assertEquals("true",
                eval("try(throw('rotto'), fn(e, tr) -> tr:'token' != null)"));
    }

    @Test
    void fnSenzaFrecciaEUnErroreChiaro()
    {
        Exception e = assertThrows(Exception.class, () -> eval("fn(a, b)"));
        assertEquals(true, e.getMessage().contains("only makes sense before"));
    }
}
