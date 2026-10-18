package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** C.3: named arguments and default values. */
class NamedArgumentsTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void unParametroPuoAvereUnValoreDiRiserva()
    {
        assertEquals("[1, true]", eval("fn f(a, b = true) -> [a, b]; f(1)"));
    }

    @Test
    void ilValoreDiRiservaSiPuoSovrascrivere()
    {
        assertEquals("[1, 9]", eval("fn f(a, b = 9) -> [a, b]; f(1, 2); f(1)"));
    }

    @Test
    void unArgomentoSiPuoPassarePerNome()
    {
        assertEquals("[1, 5]", eval("fn f(a, b = 9) -> [a, b]; f(1, b = 5)"));
    }

    @Test
    void gliArgomentiConNomePossonoAndareInOrdineDiverso()
    {
        assertEquals("[1, 2, 3]", eval("fn f(a, b, c) -> [a, b, c]; f(1, c = 3, b = 2)"));
    }

    @Test
    void ilValoreDiRiservaSiRivalutaAdOgniChiamata()
    {
        // if it were evaluated once, the two lists would be the same one
        assertEquals("[9]", eval("fn f(l = []) -> (l += 9; l); f(); f()"));
    }

    @Test
    void unNomeCheNonEsisteEUnErroreChiaro()
    {
        Exception e = assertThrows(Exception.class, () -> eval("fn f(a) -> a; f(nope = 1)"));
        assertEquals(true, e.getMessage().contains("no parameter named"));
    }

    @Test
    void unArgomentoSenzaNomeDopoUnoConNomeEUnErrore()
    {
        Exception e = assertThrows(Exception.class, () -> eval("fn f(a, b) -> a; f(a = 1, 2)"));
        assertEquals(true, e.getMessage().contains("named ones go last"), e.getMessage());
    }

    @Test
    void loStessoParametroDueVolteEUnErrore()
    {
        Exception e = assertThrows(Exception.class, () -> eval("fn f(a) -> a; f(1, a = 2)"));
        assertEquals(true, e.getMessage().contains("given twice"));
    }

    @Test
    void funzionaAncheSuUnaLambda()
    {
        assertEquals("[1, 7]", eval("f = fn(a, b = 7) -> [a, b]; f(1)"));
    }

    @Test
    void anAssignmentInBracketsIsPassedByPosition()
    {
        // without the brackets it would be a named argument followed by a positional one, which is
        // an error: the brackets are the way to say "this is an assignment"
        assertEquals("[[5, 2], 5]", Interpreter.standalone().eval("f(a, b) -> [a, b]; [f((x = 5), 2), x]").getString());
    }
}
