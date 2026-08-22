package blossom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import blossom.interpreter.Interpreter;
import blossom.value.Value;

/**
 * Baseline micro-benchmark for the core, required by PIANO_LINGUAGGIO.md §9.
 *
 * <p>Measures the two things a tree-walking interpreter pays most for inside a tick loop: the
 * per-iteration cost of an arithmetic loop and the cost of a call to a user-defined function.
 * Deliberately avoids JMH, an extra dependency and a separate module to measure two numbers that
 * only serve as a comparison between the Phase 1 baseline and the Phase 8 result.
 * It does what JMH would do by hand instead: warmup, repetitions, and reports the median.
 */
public final class CoreBenchmark
{
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 15;

    /** Preamble for cases that use the library: since Phase 5 the built-ins are no longer global. */
    private static final String IMPORTS = "import \"core.list\" as list;\n";

    /**
     * @param watchdog the limits to run the case under: used to measure how much the watchdog
     *                 costs, since it is on in-game and off for the rest of the measurements
     */
    private record Case(String name, String code, int operations, blossom.interpreter.ExecutionBudget.Policy watchdog,
            blossom.interpreter.Expression.LoadOverride compilation)
    {
        Case(String name, String code, int operations)
        {
            this(name, code, operations, blossom.interpreter.ExecutionBudget.Policy.UNLIMITED,
                    blossom.interpreter.Expression.LoadOverride.DEFAULT);
        }

        Case(String name, String code, int operations, blossom.interpreter.ExecutionBudget.Policy watchdog)
        {
            this(name, code, operations, watchdog, blossom.interpreter.Expression.LoadOverride.DEFAULT);
        }
    }

    public static void main(String[] args)
    {
        List<Case> cases = List.of(
                new Case("loop aritmetico", "sum = 0; loop(1000000, sum += _ * 2 - 1); sum", 1_000_000),
                new Case("chiamate a funzione", "f(x) -> x * 2 - 1; sum = 0; loop(200000, sum += f(_)); sum", 200_000),
                // a call with more arguments: the frame cost shows up here, where passing one
                // extra parameter is the only thing that changes compared to the case above
                new Case("chiamate a quattro argomenti", "g(a, b, c, d) -> a * b - c + d; sum = 0; loop(200000, sum += g(_, 2, 1, _)); sum", 200_000),
                new Case("accesso a lista", IMPORTS + "l = list.map(list.range(1000), _); sum = 0; loop(200000, sum += l:(_ % 1000)); sum", 200_000),
                new Case("costruzione di mappe", "loop(50000, m = {'a' -> _, 'b' -> _ * 2}; m:'b'); 0", 50_000),
                new Case("parsing", null, 1),
                // the same two cases with the watchdog on: this is the configuration that runs
                // in-game, and the difference from the corresponding case above is its cost
                new Case("loop, watchdog acceso", "sum = 0; loop(1000000, sum += _ * 2 - 1); sum", 1_000_000,
                        new blossom.interpreter.ExecutionBudget.Policy(600_000, 256)),
                new Case("chiamate, watchdog acceso", "f(x) -> x * 2 - 1; sum = 0; loop(200000, sum += f(_)); sum", 200_000,
                        new blossom.interpreter.ExecutionBudget.Policy(600_000, 256)),
                // the same two cases without the compilation step (constant folding and tree
                // compaction): the difference is what that step already gains, and is the
                // comparison term for deciding whether a more aggressive level is needed
                new Case("loop, senza compilazione", "sum = 0; loop(1000000, sum += _ * 2 - 1); sum", 1_000_000,
                        blossom.interpreter.ExecutionBudget.Policy.UNLIMITED,
                        blossom.interpreter.Expression.LoadOverride.CANONICAL),
                new Case("chiamate, senza compilazione", "f(x) -> x * 2 - 1; sum = 0; loop(200000, sum += f(_)); sum", 200_000,
                        blossom.interpreter.ExecutionBudget.Policy.UNLIMITED,
                        blossom.interpreter.Expression.LoadOverride.CANONICAL)
        );

        System.out.printf(Locale.ROOT, "%-26s %12s %10s %14s%n", "caso", "mediana ms", "minimo ms", "ns/operazione");
        System.out.println("-".repeat(66));
        for (Case testCase : cases)
        {
            Result result = testCase.code() == null
                    ? measureParsing()
                    : measure(testCase.code(), testCase.watchdog(), testCase.compilation());
            double perOperation = result.min() * 1_000_000.0 / testCase.operations();
            System.out.printf(Locale.ROOT, "%-26s %12.2f %10.2f %14.1f%n",
                    testCase.name(), result.median(), result.min(), perOperation);
        }
    }

    /**
     * Median and minimum.
     *
     * <p>The minimum because it is the statistic that holds up against noise: a busy machine can
     * only slow a measurement down, never speed it up, so the fastest sample is the least disturbed
     * one. The median stays because if the two diverge a lot it means the machine was busy and the
     * measurement should be redone.
     */
    private record Result(double median, double min)
    {
    }

    private static Result measure(String code, blossom.interpreter.ExecutionBudget.Policy watchdog,
            blossom.interpreter.Expression.LoadOverride compilation)
    {
        return medianOf(() ->
        {
            Interpreter interpreter = Interpreter.standalone();
            interpreter.host().setWatchdog(watchdog);
            interpreter.host().loadOverrides = compilation;
            long start = System.nanoTime();
            Value result = interpreter.eval(code);
            long elapsed = System.nanoTime() - start;
            // prevents the JVM from eliminating the evaluation as dead code
            if (result == null)
            {
                throw new AssertionError();
            }
            return elapsed;
        });
    }

    /**
     * Parsing alone, without evaluation: a realistically-sized module rebuilt every time, which is
     * what loading an app pays for.
     */
    private static Result measureParsing()
    {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 200; i++)
        {
            source.append("fun_").append(i).append("(a, b) -> if(a > b, a * 2 + b, b * 2 + a);\n");
        }
        source.append("fun_0(1, 2)");
        String code = source.toString();
        return medianOf(() ->
        {
            long start = System.nanoTime();
            Interpreter.standalone().eval(code);
            return System.nanoTime() - start;
        });
    }

    private static Result medianOf(java.util.function.LongSupplier run)
    {
        for (int i = 0; i < WARMUP_RUNS; i++)
        {
            run.getAsLong();
        }
        List<Long> samples = new ArrayList<>(MEASURED_RUNS);
        for (int i = 0; i < MEASURED_RUNS; i++)
        {
            samples.add(run.getAsLong());
        }
        samples.sort(Long::compare);
        return new Result(samples.get(samples.size() / 2) / 1_000_000.0, samples.getFirst() / 1_000_000.0);
    }

    private CoreBenchmark()
    {
    }
}
