package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.ExecutionBudgetError;
import blossom.interpreter.Context;
import blossom.interpreter.ExecutionBudget;
import blossom.interpreter.Interpreter;

/**
 * Il watchdog (PIANO_LINGUAGGIO.md §9): uno script che non finisce non deve poter tenere fermo il
 * server.
 *
 * <p>Ogni test ha un timeout JUnit più largo di quello dello script: se il watchdog non scatta, il
 * test fallisce per timeout invece di appendere la suite - che è esattamente il modo in cui questo
 * test può rompersi.
 */
class WatchdogTest
{
    /** un budget stretto, così i test durano poco davvero */
    private static final ExecutionBudget.Policy TIGHT = new ExecutionBudget.Policy(300, 32);

    private static Interpreter withPolicy(ExecutionBudget.Policy policy)
    {
        Interpreter interpreter = Interpreter.standalone();
        interpreter.host().setWatchdog(policy);
        return interpreter;
    }

    @Test
    @Timeout(30)
    void anInfiniteWhileIsStopped()
    {
        ExecutionBudgetError error = assertThrows(ExecutionBudgetError.class,
                () -> withPolicy(TIGHT).eval("while(true, 100000000, null)"));
        assertEquals(ExecutionBudgetError.Limit.TIME, error.limit());
        assertTrue(error.getMessage().contains("300 ms"), error.getMessage());
    }

    @Test
    @Timeout(30)
    void anInfiniteLoopInsideAFunctionIsStopped()
    {
        assertThrows(ExecutionBudgetError.class,
                () -> withPolicy(TIGHT).eval("f() -> c_for(i = 0, true, i += 1, null); f()"));
    }

    @Test
    @Timeout(30)
    void splittingTheLoopAcrossFunctionsDoesNotResetTheBudget()
    {
        // il budget si eredita nei frame figli: se ogni chiamata ne aprisse uno nuovo, questo
        // programma girerebbe per sempre
        assertThrows(ExecutionBudgetError.class, () -> withPolicy(TIGHT).eval(
                "step() -> c_for(i = 0, i < 1000, i += 1, null);"
                        + "forever() -> c_for(j = 0, true, j += 1, step());"
                        + "forever()"));
    }

    @Test
    @Timeout(30)
    void runawayRecursionReportsTheDepthAndNotAStackOverflow()
    {
        ExecutionBudgetError error = assertThrows(ExecutionBudgetError.class,
                () -> withPolicy(TIGHT).eval("f(n) -> f(n + 1); f(0)"));
        assertEquals(ExecutionBudgetError.Limit.DEPTH, error.limit());
        assertTrue(error.getMessage().contains("32"), error.getMessage());
    }

    @Test
    void recursionWellWithinTheLimitStillWorks()
    {
        assertEquals("120", withPolicy(TIGHT).eval("f(n) -> if(n <= 1, 1, n * f(n - 1)); f(5)").getString());
    }

    @Test
    void aFunctionCalledManyTimesInSequenceDoesNotFillTheDepth()
    {
        // ogni chiamata chiude il proprio frame: contarne 200 in fila non deve avvicinarsi al
        // limite di profondità, che riguarda l'annidamento e non il totale
        assertEquals("200", withPolicy(TIGHT).eval("f(x) -> x + 1; c_for(i = 0, i < 200, i += 1, f(i))").getString());
    }

    @Test
    @Timeout(30)
    void tryCannotSwallowTheWatchdog()
    {
        // se lo script potesse catturarlo, un ciclo infinito dentro un try continuerebbe a girare
        assertThrows(ExecutionBudgetError.class,
                () -> withPolicy(TIGHT).eval("try(while(true, 100000000, null), null)"));
    }

    @Test
    void noPolicyMeansNoWatchdog()
    {
        assertEquals(ExecutionBudget.Policy.UNLIMITED, Interpreter.standalone().host().watchdog());
        assertEquals("100000", Interpreter.standalone().eval("c_for(i = 0, i < 100000, i += 1, null)").getString());
    }

    @Test
    @Timeout(30)
    void aManifestCanDeclareItsOwnLimits(@TempDir Path tmp) throws IOException
    {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("blossom.json"),
                "{\"name\": \"t\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\", \"watchdog\": {\"timeout_ms\": 200}}");
        Files.writeString(tmp.resolve("src/main.bs"), "module main;\n\nwhile(true, 100000000, null);\n");

        assertThrows(ExecutionBudgetError.class, () -> Interpreter.forProject(tmp));
    }

    @Test
    void aManifestCanTurnItOff(@TempDir Path tmp) throws IOException
    {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("blossom.json"),
                "{\"name\": \"t\", \"entrypoint\": \"src/main.bs\", \"api_level\": \"2.0\", \"watchdog\": false}");
        Files.writeString(tmp.resolve("src/main.bs"), "module main;\n\npub let v = 1;\n");

        Interpreter interpreter = Interpreter.forProject(tmp);
        assertEquals(ExecutionBudget.Policy.UNLIMITED, interpreter.host().watchdog());
    }

    @Test
    @Timeout(30)
    void aTaskIsNotKilledByTheDeadlineOfWhoStartedIt()
    {
        // un task gira su un thread suo e non tiene fermo il tick: "gira a lungo" e' il motivo per
        // cui uno lo lancia
        Interpreter interpreter = withPolicy(new ExecutionBudget.Policy(100, 64));
        String code = "import \"core.task\" as task;"
                + "slow() -> c_for(i = 0, i < 300000, i += 1, null);"
                + "t = task.start('slow'); task.wait(t)";
        assertEquals("300000", interpreter.eval(code).getString());
    }

    @Test
    void theUnlimitedBudgetDoesNotAllocate()
    {
        // il contesto senza limiti deve condividere il singleton: e' il caso di gran lunga piu'
        // frequente ed e' quello su cui il costo del watchdog dev'essere zero
        Context context = new Context(Interpreter.standalone().host());
        context.beginExecution(ExecutionBudget.Policy.UNLIMITED);
        assertEquals(ExecutionBudget.UNLIMITED_BUDGET, context.budget);
    }
}
