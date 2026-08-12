package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Interpreter;
import blossom.language.StdLib;

/**
 * La libreria standard a pacchetti (PIANO_LINGUAGGIO.md §5): ogni funzione vive in un modulo, e
 * l'unica cosa disponibile senza import è il prelude.
 */
class StdLibTest
{
    private static String eval(String code)
    {
        return Interpreter.standalone().eval(code).getString();
    }

    @Test
    void everyModuleIsReachableThroughItsAlias()
    {
        assertEquals("4", eval("import \"core.math\" as math; math.sqrt(16)"));
        assertEquals("CIAO", eval("import \"core.string\" as string; string.upper('ciao')"));
        assertEquals("[2, 4, 6]", eval("import \"core.list\" as list; list.map([1, 2, 3], _ * 2)"));
        assertEquals("[a, b]", eval("import \"core.map\" as map; map.keys({'a' -> 1, 'b' -> 2})"));
        assertEquals("[1, 2]", eval("import \"core.json\" as json; json.decode_json('[1, 2]')"));
        assertEquals("list", eval("import \"core.system\" as system; system.type([1])"));
    }

    @Test
    void theAliasIsWhateverTheImporterChooses()
    {
        assertEquals("4", eval("import \"core.math\" as m2; m2.sqrt(16)"));
    }

    @Test
    void thePreludeNeedsNoImport()
    {
        assertEquals("ok", eval("if(true, 'ok', 'ko')"));
        assertEquals("10", eval("sum = 0; loop(5, sum += _); sum"));
        assertEquals("preso", eval("try(throw('x'), 'preso')"));
    }

    @Test
    void aLibraryFunctionWithoutItsImportIsAnError()
    {
        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class, () -> eval("sqrt(4)"));
        assertTrue(error.getMessage().contains("core.math"), error.getMessage());
    }

    @Test
    void aModuleDoesNotExportWhatIsNotItsOwn()
    {
        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class,
                () -> eval("import \"core.math\" as math; math.upper('ciao')"));
        assertTrue(error.getMessage().contains("core.math"), error.getMessage());
    }

    @Test
    void everyDeclaredSymbolActuallyExists()
    {
        // il catalogo dei moduli e le funzioni registrate devono restare allineati: un simbolo
        // dichiarato ma inesistente sarebbe documentazione che mente
        for (String module : StdLib.moduleNames())
        {
            for (String symbol : StdLib.symbolsOf(module))
            {
                String alias = module.substring(module.lastIndexOf('.') + 1);
                assertTrue(Interpreter.standalone().host().symbolsOf(null) != null);
                assertThrowsNothingButArity(module, alias, symbol);
            }
        }
    }

    /**
     * Chiama il simbolo senza argomenti: qualunque errore va bene tranne "non esiste", perché è
     * quello che stiamo verificando.
     */
    private static void assertThrowsNothingButArity(String module, String alias, String symbol)
    {
        try
        {
            eval("import \"" + module + "\" as " + alias + "; " + alias + "." + symbol + "()");
        }
        catch (UndefinedSymbolError e)
        {
            throw new AssertionError(module + " dichiara '" + symbol + "' ma non esiste: " + e.getMessage());
        }
        catch (RuntimeException | StackOverflowError expected)
        {
            // arieta' sbagliata, tipo sbagliato, valore mancante: tutto normale
        }
    }
}
