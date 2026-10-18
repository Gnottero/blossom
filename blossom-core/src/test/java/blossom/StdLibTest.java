package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Interpreter;
import blossom.language.StdLib;

/**
 * The packaged standard library: every function lives in a module, and the
 * only thing available without an import is the prelude.
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
        assertEquals("HELLO", eval("import \"core.string\" as string; string.upper('hello')"));
        assertEquals("[2, 4, 6]", eval("import \"core.list\" as list; list.map([1, 2, 3], _ * 2)"));
        assertEquals("[a, b]", eval("import \"core.map\" as map; map.keys({'a' -> 1, 'b' -> 2})"));
        assertEquals("[1, 2]", eval("import \"core.json\" as json; json.decode('[1, 2]')"));
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
                () -> eval("import \"core.math\" as math; math.upper('hello')"));
        assertTrue(error.getMessage().contains("core.math"), error.getMessage());
    }

    @Test
    void everyDeclaredSymbolActuallyExists()
    {
        // the module catalog and the registered functions have to stay aligned: a declared symbol
        // that does not exist would be documentation that lies
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
     * Calls the symbol with no arguments: any error is fine except "does not exist", which is what
     * is being checked.
     */
    private static void assertThrowsNothingButArity(String module, String alias, String symbol)
    {
        try
        {
            eval("import \"" + module + "\" as " + alias + "; " + alias + "." + symbol + "()");
        }
        catch (UndefinedSymbolError e)
        {
            throw new AssertionError(module + " declares '" + symbol + "' but it does not exist: " + e.getMessage());
        }
        catch (RuntimeException | StackOverflowError expected)
        {
            // wrong arity, wrong type, missing value: all normal
        }
    }

    /**
     * No symbol is exported by two different modules.
     *
     * <p>It is the net that keeps 'join'/'split' or 'task_join' from living in two places again:
     * whoever reads a call has to be able to trace the module back from the alias name, and two
     * modules exporting the same name turn that trace into a question instead of a reading.
     *
     * <p>The exceptions are declared here one by one, with their reason, instead of being tolerated
     * silently.
     */
    @Test
    void noSymbolIsExportedByTwoModules()
    {
        // 'core.list' and 'core.map' share indexed access: there the first argument is genuinely
        // polymorphic, and it is the very same implementation
        java.util.Set<String> shared = java.util.Set.of("get", "put", "has", "remove", "size");

        java.util.Map<String, java.util.List<String>> homes = new java.util.TreeMap<>();
        for (String module : StdLib.moduleNames())
        {
            for (String symbol : StdLib.symbolsOf(module))
            {
                homes.computeIfAbsent(symbol, k -> new java.util.ArrayList<>()).add(module);
            }
        }
        java.util.List<String> shouted = new java.util.ArrayList<>();
        homes.forEach((symbol, modules) ->
        {
            if (modules.size() > 1 && !shared.contains(symbol))
            {
                shouted.add(symbol + " -> " + modules);
            }
        });
        assertEquals(java.util.List.of(), shouted, "symbols exported by more than one module");
    }
}
