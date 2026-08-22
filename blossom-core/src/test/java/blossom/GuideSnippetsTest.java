package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.ArityError;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Interpreter;

/**
 * The snippets from {@code docs/guide/} that don't need Minecraft, actually run.
 *
 * <p>A guide that lies is worse than a guide that's missing: every line the text presents as
 * "this gives that" is checked here, so if the language changes without the guide being updated
 * the build notices. Chapters about events, blocks, and tasks on the world are not covered: those
 * require a server, and verification for them is manual.
 */
class GuideSnippetsTest
{
    private static final String IMPORTS = """
            import "core.list" as list;
            import "core.map" as map;
            import "core.math" as math;
            import "core.string" as string;
            import "core.system" as system;
            import "core.json" as json;
            """;

    /** The repo root: tests run from the module's directory, and the examples live above it. */
    private static Path repoRoot()
    {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isDirectory(candidate.resolve("examples")))
        {
            candidate = candidate.getParent();
        }
        if (candidate == null)
        {
            throw new IllegalStateException("cartella 'examples' non trovata risalendo da " + Path.of("").toAbsolutePath());
        }
        return candidate;
    }

    private static String eval(String code)
    {
        return Interpreter.standalone().eval(IMPORTS + code).getString();
    }

    // --- chapter 2: values ---

    @Test
    void iTipiSiChiamanoComeScrittoNellaTabella()
    {
        assertEquals("number", eval("system.type(1)"));
        assertEquals("string", eval("system.type('a')"));
        assertEquals("bool", eval("system.type(true)"));
        assertEquals("list", eval("system.type([1, 2])"));
        assertEquals("map", eval("system.type({'a' -> 1})"));
        assertEquals("null", eval("system.type(null)"));
        assertEquals("function", eval("fn f() -> 1; system.type(f)"));
    }

    @Test
    void aritmetica()
    {
        assertEquals("3.5", eval("7 / 2"));
        assertEquals("2", eval("6 / 3"));
        assertEquals("2", eval("-7 % 3"));
        assertEquals("1024", eval("2 ^ 10"));
        assertEquals("4", eval("-2 ^ 2"));       // unary minus binds tighter than power
        assertEquals("256", eval("2 ^ 2 ^ 3"));  // '^' is right-associative
    }

    @Test
    void stringhe()
    {
        assertEquals("a'b", eval("'a\\'b'"));
        assertEquals("ababab", eval("'ab' * 3"));
        assertEquals("n=3", eval("'n=' + 3"));
        assertEquals("null", eval("system.type('abc':0)"));   // a string can't be indexed
        assertEquals("ada has 3 apples", eval("string.str('%s has %d apples', 'ada', 3)"));
    }

    @Test
    void liste()
    {
        assertEquals("10", eval("l = [10, 20, 30]; l:0"));
        assertEquals("20", eval("l = [10, 20, 30]; l:4"));            // indices wrap around
        assertEquals("30", eval("l = [10, 20, 30]; list.get(l, -1)"));
        assertEquals("30", eval("l = [10, 20, 30]; l:(-1)"));
        assertEquals("[9, 2]", eval("a = [1, 2]; b = a; list.put(b, 0, 9); a"));   // by reference
        assertEquals("[1, 2]", eval("a = [1, 2]; b = system.copy(a); list.put(b, 0, 9); a"));
    }

    @Test
    void scrivereInUnaLista()
    {
        assertEquals("[9, 2, 3]", eval("l = [1, 2, 3]; list.put(l, 0, 9); l"));
        assertEquals("[1, 5, 2, 3]", eval("l = [1, 2, 3]; list.put(l, 1, 5, 'insert'); l"));
        assertEquals("[1, 2, 4]", eval("l = [1, 2]; list.put(l, null, 4); l"));
        assertEquals("[1, 2, 7, 8]", eval("l = [1, 2]; list.put(l, null, [7, 8], 'extend'); l"));
        assertEquals("[9]", eval("l = [[1, 2]]; list.put(l:0, 9, 'replace'); l"));
        assertEquals("[[1, 9]]", eval("l = [[1, 2]]; list.put(l:0:1, 9); l"));
    }

    @Test
    void listePigre()
    {
        assertEquals("0,3,6,9", eval("list.join(',', list.range(0, 10, 3))"));
        assertEquals("[0, 1, 4]", eval("list.map(list.range(3), _ * _)"));
    }

    @Test
    void mappe()
    {
        assertEquals("1", eval("m = {'a' -> 1, 'b' -> 2}; m:'a'"));
        assertEquals("null", eval("m = {'a' -> 1}; m:'zz'"));
        assertEquals("[[a, 1]]", eval("map.pairs({'a' -> 1})"));
        assertEquals("3", eval("m = {'a' -> 1}; m:'c' = 3; m:'c'"));
        assertEquals("1", eval("m = {'a' -> {'b' -> 1}}; m:'a':'b'"));
    }

    @Test
    void verita()
    {
        assertEquals("true", eval("if('0', 'true', 'false')"));
        assertEquals("false", eval("if([], 'true', 'false')"));
        assertEquals("false", eval("if({}, 'true', 'false')"));
        assertEquals("false", eval("if(0, 'true', 'false')"));
        assertEquals("false", eval("if('', 'true', 'false')"));
        assertEquals("false", eval("if(null, 'true', 'false')"));
    }

    @Test
    void uguaglianza()
    {
        assertEquals("true", eval("[1, 2] == [1, 2]"));
        assertEquals("true", eval("{'a' -> 1} == {'a' -> 1}"));
        assertEquals("true", eval("'1' == 1"));
        assertEquals("false", eval("null == false"));
    }

    @Test
    void unaVariabileMaiAssegnataValeNull()
    {
        assertEquals("1", eval("mai_vista + 1"));
    }

    // --- chapter 3: operators ---

    @Test
    void sommaSecondoGliOperandi()
    {
        assertEquals("[11, 22]", eval("[1, 2] + [10, 20]"));
        assertEquals("[2, 3]", eval("[1, 2] + 1"));
        assertThrows(TypeMismatchError.class, () -> eval("a = [1, 2]; b = [1, 2, 3]; a + b"));
    }

    @Test
    void logicaPigraCheRestituisceUnValore()
    {
        assertEquals("0", eval("0 && 5"));
        assertEquals("5", eval("3 && 5"));
        assertEquals("5", eval("0 || 5"));
        assertEquals("false", eval("false && (1/0)"));
        assertEquals("[true, true, true, false, true]", eval("[!0, !'', !null, !1, ![]]"));
    }

    @Test
    void ricerca()
    {
        assertEquals("o w", eval("'hello world' ~ 'o w'"));
        assertEquals("[1, 2]", eval("'v1.2' ~ '([0-9])\\\\.([0-9])'"));
        assertEquals("2", eval("['a', 'b', 'c'] ~ 'c'"));
        assertEquals("null", eval("['a'] ~ 'z'"));
        assertEquals("a", eval("{'a' -> 1} ~ 'a'"));
    }

    @Test
    void appartenenzaSiChiedeConTildeNonConHas()
    {
        assertEquals("true", eval("l = ['a', 'b']; (l ~ 'a') != null"));
        assertEquals("false", eval("l = ['a', 'b']; (l ~ 'z') != null"));
        // 'has' on a list asks whether the index exists, not whether the value is inside
        assertEquals("true", eval("list.has([10, 20], 1)"));
        assertEquals("false", eval("list.has([10, 20], 5)"));
        // on a map, instead, it is what it looks like
        assertEquals("true", eval("map.has({'a' -> 1}, 'a')"));
    }

    @Test
    void assegnamentoEScambio()
    {
        assertEquals("[1, 2, 3]", eval("l = [1, 2]; l += 3; l"));
        assertEquals("ab", eval("s = 'a'; s += 'b'; s"));
        assertEquals("[2, 1]", eval("a = 1; b = 2; a <> b; [a, b]"));
        assertEquals("3", eval("(1; 2; 3)"));
    }

    @Test
    void spacchettare()
    {
        assertEquals("3", eval("f(a, b) -> a + b; call('f', ...[1, 2])"));
        assertEquals("[0, 1, 2]", eval("l = [1, 2]; [0, ...l]"));
        assertEquals("[1, [2, 3]]", eval("fn how_many(first, ...rest) -> [first, rest]; how_many(1, 2, 3)"));
        assertEquals("[1, []]", eval("fn how_many(first, ...rest) -> [first, rest]; how_many(1)"));
    }

    // --- chapter 4: functions ---

    @Test
    void bindingDiModuloEVariabiliDelCorpo()
    {
        assertEquals("2", eval("let mut contatore = 0; fn bump() -> contatore += 1; bump(); bump(); contatore"));
        // without 'let' it's not a module binding: the function writes to something else
        assertEquals("0", eval("contatore = 0; fn bump() -> contatore += 1; bump(); contatore"));
    }

    @Test
    void unLetImmutabileNonSiRiassegna()
    {
        TypeMismatchError error = assertThrows(TypeMismatchError.class, () -> eval("let soglia = 10; soglia = 11"));
        assertTrue(error.getMessage().contains("immutable"), error.getMessage());
        assertTrue(error.getMessage().contains("let mut"), error.getMessage());
    }

    @Test
    void outerLeggeIlFrameChiamante()
    {
        assertEquals("7", eval("base = 7; fn leggi(outer(base)) -> base; leggi()"));
    }

    @Test
    void scalariPerValoreListePerRiferimento()
    {
        assertEquals("1", eval("fn incrementa(v) -> (v += 1; null); n = 1; incrementa(n); n"));
        assertEquals("[1, 9]", eval("fn accoda(l) -> list.put(l, null, 9); lista = [1]; accoda(lista); lista"));
    }

    @Test
    void arietaSbagliata()
    {
        ArityError error = assertThrows(ArityError.class, () -> eval("fn f(a, b) -> a + b; f(1)"));
        assertTrue(error.getMessage().contains("2 arguments"), error.getMessage());
    }

    @Test
    void tipiSuiParametri()
    {
        assertEquals("x1", eval("fn f(a: num, b: str) -> b + a; f(1, 'x')"));
        TypeMismatchError error = assertThrows(TypeMismatchError.class, () -> eval("fn f(a: num) -> a; f('x')"));
        assertTrue(error.getMessage().contains("Parameter 'a'"), error.getMessage());
        assertTrue(error.getMessage().contains("wants a number"), error.getMessage());
    }

    @Test
    void leFunzioniSonoValori()
    {
        assertEquals("18", eval("fn triplica(x) -> x * 3; fn due_volte(f, x) -> call(f, call(f, x)); due_volte(triplica, 2)"));
        assertEquals("18", eval("fn triplica(x) -> x * 3; fn due_volte(f, x) -> call(f, call(f, x)); due_volte('triplica', 2)"));
    }

    @Test
    void ricorsioneEUscitaAnticipata()
    {
        assertEquals("55", eval("fn fib(n) -> if(n < 2, n, fib(n - 1) + fib(n - 2)); fib(10)"));
        assertEquals("negative", eval("""
                fn classify(x) -> (
                    if(x < 0, return('negative'));
                    if(x == 0, return('zero'));
                    'positive'
                );
                classify(-1)
                """));
    }

    // --- chapter 5: control flow ---

    @Test
    void ifACatena()
    {
        assertEquals("hurt", eval("health = 10; if(health < 5, 'critical', health < 15, 'hurt', 'healthy')"));
        assertEquals("null", eval("system.type(if(false, 'a'))"));
    }

    @Test
    void cicli()
    {
        assertEquals("10", eval("somma = 0; loop(5, somma += _); somma"));
        assertEquals("6", eval("somma = 0; for([1, 2, 3], somma += _); somma"));
        assertEquals("2", eval("for([1, 2, 3], _ > 1)"));      // 'for' counts the truthy ones
        assertEquals("3", eval("i = 0; while(i < 3, 1000, i += 1); i"));
        assertEquals("5", eval("i = 0; while(true, 5, i += 1); i"));   // the cap stops the loop
        assertEquals("20", eval("somma = 0; c_for(i = 0, i < 10, i += 2, somma += i); somma"));
    }

    @Test
    void breakEContinue()
    {
        assertEquals("3", eval("loop(10, if(_ == 3, break(_)); 0)"));
        assertEquals("4", eval("somma = 0; for([1, 2, 3], if(_ == 2, continue()); somma += _); somma"));
        assertEquals("3", eval("s = 0; loop(3, loop(3, if(_ == 1, break()); s += 1)); s"));
    }

    @Test
    void trasformazioni()
    {
        assertEquals("[2, 4, 6]", eval("list.map([1, 2, 3], _ * 2)"));
        assertEquals("[0, 1]", eval("list.map([10, 20], _i)"));
        assertEquals("[2, 4]", eval("list.filter([1, 2, 3, 4], _ % 2 == 0)"));
        assertEquals("10", eval("list.reduce([1, 2, 3, 4], _a + _, 0)"));
        assertEquals("2", eval("list.find([1, 2, 3], _ > 1)"));
        assertEquals("null", eval("system.type(list.find([1, 2], _ > 5))"));
        assertEquals("true", eval("list.all([], _ > 5)"));
        assertEquals("[1]", eval("list.map([1, 2, 3], if(_ == 2, break()); _)"));
        assertEquals("[3, 2, 1]", eval("list.reverse([1, 2, 3])"));
    }

    // --- chapter 7: errors ---

    @Test
    void tryCatturaLeEccezioniLanciate()
    {
        assertEquals("caught: boom", eval("try(throw('boom'), 'caught: ' + _)"));
        assertEquals("v", eval("try(throw('value_exception', 'v'), 'value_exception', _)"));
        assertEquals("caught: x", eval("fn boom() -> throw('io_exception', 'x'); try(boom(), 'io_exception', 'caught: ' + _)"));
        // the hierarchy: json_error sits under io_exception, which sits under exception
        assertEquals("caught", eval("bad = '{'; try(json.decode(bad), 'io_exception', 'caught')"));
        assertEquals("caught", eval("bad = '{'; try(json.decode(bad), 'exception', 'caught')"));
    }

    @Test
    void laFormaCortaDiTryPrendeSoloLeEccezioniUtente()
    {
        assertEquals("null", eval("system.type(try(throw('boom')))"));
        assertThrows(Throwable.class, () -> eval("bad = '{'; try(json.decode(bad))"));
    }

    @Test
    void tryNonCatturaGliErroriDelLinguaggio()
    {
        assertThrows(ArityError.class, () -> eval("fn f(a, b) -> a + b; fn g() -> f(1); try(g(), 'exception', 'caught')"));
        assertThrows(TypeMismatchError.class, () -> eval("s = 'x'; try(math.sqrt(s), 'exception', 'caught')"));
    }

    @Test
    void ilValoreLanciatoPuoEssereQualunqueCosa()
    {
        assertEquals("short by 40", eval("""
                fn withdraw(balance, amount) -> (
                    if(amount > balance, throw({'error' -> 'funds', 'short_by' -> amount - balance}));
                    balance - amount
                );
                try(withdraw(60, 100), 'short by ' + _:'short_by')
                """));
    }

    @Test
    void traceDiceDoveEraSuccesso()
    {
        String chiavi = eval("try(throw('x'), list.sort(map.keys(_trace)))");
        assertEquals("[locals, stack, token]", chiavi);
        assertEquals("1", eval("fn boom() -> throw('x'); try(boom(), list.size(_trace:'stack'))"));
        assertEquals("7", eval("fn boom(v) -> throw('x'); try(boom(7), (_trace:'locals'):'v')"));
    }

    // --- the examples: the pure-logic module of vena-viva actually runs ---

    @Test
    void ilModuloGeologiaDellEsempioVenaVivaFunziona(@TempDir Path tmp) throws IOException
    {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(tmp.resolve("blossom.json"), """
                {"name": "vena-viva-test", "version": "1.0.0", "entrypoint": "src/main.bs", "api_level": "2.0"}
                """);
        Files.copy(repoRoot().resolve("examples/vena-viva/src/geologia.bs"), src.resolve("geologia.bs"));
        Files.writeString(src.resolve("main.bs"), """
                module main;

                import "geologia" as geologia;

                let LETTURE = [
                    [[0, 0, 0], 'minecraft:diamond_ore'],
                    [[1, 0, 0], 'minecraft:stone'],
                    [[2, 0, 0], 'minecraft:diamond_ore'],
                    [[2, 3, 4], 'minecraft:diamond_ore']
                ];

                pub let nome = geologia.nome_corto('minecraft:diamond_ore');
                pub let minerale = geologia.e_minerale('minecraft:diamond_ore');
                pub let pietra = geologia.e_minerale('minecraft:stone');
                pub let quanti = geologia.conta(LETTURE, 'diamond_ore');
                pub let dove = geologia.posizioni(LETTURE, 'diamond_ore');
                pub let angoli = geologia.riquadro(dove);
                pub let vuoto = geologia.riquadro([]);
                pub let parola = geologia.giudizio(0);
                """);

        Interpreter interpreter = Interpreter.forProject(tmp);
        assertEquals("diamond_ore", interpreter.eval("nome").getString());
        assertEquals("true", interpreter.eval("minerale").getString());
        assertEquals("false", interpreter.eval("pietra").getString());
        assertEquals("3", interpreter.eval("quanti").getString());
        assertEquals("[[0, 0, 0], [2, 3, 4]]", interpreter.eval("angoli").getString());
        assertEquals("null", interpreter.eval("vuoto").getString());
        assertEquals("esaurita", interpreter.eval("parola").getString());
    }
}
