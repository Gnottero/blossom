package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** C.0 - calling a function that arrives as the value of a variable. */
class CallValueTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void unaFunzioneDichiarataSiChiamaComePrima()
    {
        assertEquals("6", eval("fn triplica(x) -> x * 3; triplica(2)"));
    }

    @Test
    void unaVariabileCheContieneUnaFunzioneSiChiamaDirettamente()
    {
        assertEquals("6", eval("fn triplica(x) -> x * 3; g = triplica; g(2)"));
    }

    @Test
    void ilParametroDiUnaFunzioneSiChiamaDirettamente()
    {
        assertEquals("18", eval("fn triplica(x) -> x * 3; fn due_volte(f, x) -> f(f(x)); due_volte(triplica, 2)"));
    }

    @Test
    void laFunzioneDichiarataVinceSullaVariabile()
    {
        assertEquals("6", eval("fn f(x) -> x * 3; f = 'niente'; f(2)"));
    }
}
