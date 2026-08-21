package blossom;

import blossom.interpreter.Interpreter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** C.2 - le stringhe fra doppi apici interpolano. */
class InterpolationTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void unaStringaSenzaSegnapostiRestaSeStessa()
    {
        assertEquals("ciao", eval("\"ciao\""));
    }

    @Test
    void unSegnapostoInserisceUnaVariabile()
    {
        assertEquals("n=3", eval("a = 3; \"n=${a}\""));
    }

    @Test
    void unSegnapostoAccettaUnEspressione()
    {
        assertEquals("n=7", eval("a = 3; \"n=${a + 4}\""));
    }

    @Test
    void piuSegnapostiEPezziAlternati()
    {
        assertEquals("1 e 2 fa 3", eval("a = 1; b = 2; \"${a} e ${b} fa ${a + b}\""));
    }

    @Test
    void unSegnapostoAllInizioEAllaFine()
    {
        assertEquals("12", eval("a = 1; b = 2; \"${a}${b}\""));
    }

    @Test
    void unaChiamataDentroUnSegnaposto()
    {
        assertEquals("doppio 6", eval("fn doppio(x) -> x * 2; \"doppio ${doppio(3)}\""));
    }

    @Test
    void unApiceSingoloDentroUnSegnapostoNonChiudeNiente()
    {
        assertEquals("v=1", eval("m = {'}' -> 1}; \"v=${m:'}'}\""));
    }

    @Test
    void lApiceSingoloResteLetterale()
    {
        assertEquals("n=${a}", eval("a = 3; 'n=${a}'"));
    }
}
