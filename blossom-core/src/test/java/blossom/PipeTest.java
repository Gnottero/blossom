package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** C.5: the |> operator. */
class PipeTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void ilSoggettoDiventaIlPrimoArgomento()
    {
        assertEquals("[2, 4, 6]",
                eval("import 'core.list' as list; [1,2,3] |> list.map(fn(x) -> x * 2)"));
    }

    @Test
    void treTrasformazioniInFila()
    {
        // note: today 'join' is join(separator, list), so the subject is not the first argument and
        // does not chain. It is exactly the case rule R2 of the renaming fixes; here the chain is
        // measured with the functions that already take the subject first
        assertEquals("[4, 8]",
                eval("import 'core.list' as list; "
                        + "[1,2,3,4] |> list.filter(fn(x) -> x % 2 == 0) |> list.map(fn(x) -> x * 2)"));
    }

    @Test
    void funzionaConUnaFunzioneUtente()
    {
        assertEquals("6", eval("fn doppio(x) -> x * 2; 3 |> doppio()"));
    }

    @Test
    void funzionaConArgomentiInPiu()
    {
        assertEquals("9", eval("fn somma(a, b) -> a + b; 4 |> somma(5)"));
    }

    @Test
    void unValoreFunzioneADestraSiChiamaLoStesso()
    {
        assertEquals("6", eval("f = fn(x) -> x * 2; 3 |> f"));
    }

    @Test
    void legaPiuDellAssegnamento()
    {
        assertEquals("6", eval("fn doppio(x) -> x * 2; y = 3 |> doppio(); y"));
    }

    @Test
    void legaMenoDellaDisgiunzione()
    {
        assertEquals("2", eval("fn doppio(x) -> x * 2; (0 || 1) |> doppio()"));
    }
}
