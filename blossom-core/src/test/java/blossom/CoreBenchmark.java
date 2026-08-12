package blossom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import blossom.interpreter.Interpreter;
import blossom.value.Value;

/**
 * Micro-benchmark di baseline del core, richiesto da PIANO_LINGUAGGIO.md §9.
 *
 * <p>Misura le due cose che un interprete tree-walking paga di più dentro un tick loop: il costo
 * per iterazione di un loop aritmetico e il costo di una chiamata a funzione definita dall'utente.
 * Non usa JMH di proposito - una dipendenza in più e un modulo separato per misurare due numeri che
 * ci servono solo come termine di paragone tra la baseline della Fase 1 e il risultato della Fase 8.
 * In compenso fa quello che JMH farebbe a mano: riscaldamento, ripetizioni, e riporta la mediana.
 */
public final class CoreBenchmark
{
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 15;

    /** il preambolo dei casi che usano la libreria: dalla fase 5 le built-in non sono piu' globali */
    private static final String IMPORTS = "import \"core.list\" as list;\n";

    /**
     * @param watchdog i limiti sotto cui girare il caso: serve a misurare quanto costa il watchdog,
     *                 che è acceso in gioco e spento nel resto delle misure
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
                new Case("accesso a lista", IMPORTS + "l = list.map(list.range(1000), _); sum = 0; loop(200000, sum += l:(_ % 1000)); sum", 200_000),
                new Case("costruzione di mappe", "loop(50000, m = {'a' -> _, 'b' -> _ * 2}; m:'b'); 0", 50_000),
                new Case("parsing", null, 1),
                // gli stessi due casi con il watchdog acceso: e' la configurazione che gira dentro
                // il gioco, e la differenza col caso corrispondente qui sopra e' il suo costo
                new Case("loop, watchdog acceso", "sum = 0; loop(1000000, sum += _ * 2 - 1); sum", 1_000_000,
                        new blossom.interpreter.ExecutionBudget.Policy(600_000, 256)),
                new Case("chiamate, watchdog acceso", "f(x) -> x * 2 - 1; sum = 0; loop(200000, sum += f(_)); sum", 200_000,
                        new blossom.interpreter.ExecutionBudget.Policy(600_000, 256)),
                // gli stessi due casi senza il passo di compilazione (piegatura delle costanti e
                // compattazione dell'albero): la differenza e' quello che quel passo gia' guadagna,
                // ed e' il termine di paragone per decidere se serve un livello piu' aggressivo
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
     * Mediana e minimo.
     *
     * <p>Il minimo perché è la statistica che regge il rumore: una macchina occupata può solo
     * rallentare una misura, mai accelerarla, quindi il campione più veloce è quello meno disturbato.
     * La mediana resta perché se le due divergono molto vuol dire che la macchina era occupata e la
     * misura va rifatta.
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
            // impedisce alla jvm di eliminare la valutazione come codice morto
            if (result == null)
            {
                throw new AssertionError();
            }
            return elapsed;
        });
    }

    /**
     * Il solo parsing, senza valutazione: un modulo di taglia realistica ricostruito ogni volta, che
     * è quello che il caricamento di un'app paga.
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
