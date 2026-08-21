package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** C.4 - il costrutto match. */
class MatchTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void unaFormaLetteraleCombaciaPerUguaglianza()
    {
        assertEquals("uno", eval("match(1, 1 -> 'uno', 2 -> 'due', else -> 'altro')"));
        assertEquals("due", eval("match(2, 1 -> 'uno', 2 -> 'due', else -> 'altro')"));
    }

    @Test
    void elseEIlRamoFinale()
    {
        assertEquals("altro", eval("match(9, 1 -> 'uno', else -> 'altro')"));
    }

    @Test
    void senzaNessunBraccioCheCombaciaEnull()
    {
        assertEquals("null", eval("match(9, 1 -> 'uno')"));
    }

    @Test
    void unNomeCatturaIlSoggetto()
    {
        assertEquals("18", eval("match(9, 0 -> 'zero', d -> d * 2)"));
    }

    @Test
    void unNomeTipatoCombaciaSoloSuQuelTipo()
    {
        assertEquals("numero 5", eval("match(5, s: str -> 'testo ' + s, n: num -> 'numero ' + n)"));
        assertEquals("testo ciao", eval("match('ciao', s: str -> 'testo ' + s, n: num -> 'numero ' + n)"));
    }

    @Test
    void leStringheCombacianoComeIValori()
    {
        assertEquals("saluto", eval("match('ciao', 'ciao' -> 'saluto', else -> 'boh')"));
    }

    @Test
    void ilSoggettoSiValutaUnaVoltaSola()
    {
        // una lista passa per riferimento, quindi la funzione la vede davvero
        assertEquals("2", eval("import 'core.list' as list; "
                + "let visite = []; fn conta() -> (list.put(visite, null, 1); 5); "
                + "match(conta(), 5 -> conta(), else -> 0); list.length(visite)"));
    }

    @Test
    void iCorpiNonPresiNonSiValutano()
    {
        assertEquals("0", eval("n = 0; match(1, 1 -> 0, 2 -> (n += 1), else -> (n += 1)); n"));
    }

    @Test
    void unMatchStaDentroUnEspressione()
    {
        assertEquals("[uno, due]", eval("import 'core.list' as list; "
                + "list.map([1, 2], fn(x) -> match(x, 1 -> 'uno', 2 -> 'due', else -> '?'))"));
    }
}
