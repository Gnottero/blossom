package blossom.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dove va il tempo, per funzione e per modulo (PIANO_LINGUAGGIO.md §9).
 *
 * <p>{@code profile_expr} misura una singola espressione e risponde a "quanto ci mette questo
 * pezzo"; questo risponde a "chi si mangia il tick", che e' la domanda vera quando un'app ha venti
 * handler.
 *
 * <p>Di ogni funzione si tengono due numeri: il <em>totale</em> comprende le funzioni chiamate, il
 * <em>proprio</em> e' quello che resta togliendole. Serve la coppia - un totale enorme con un
 * proprio minuscolo vuol solo dire che quella funzione sta in cima, e ottimizzarla non cambia
 * niente.
 *
 * <p>Spento, {@link #OFF} fa due return: niente orologio, niente mappe, niente allocazioni. Acceso
 * costa due {@code System.nanoTime()} per chiamata, ed e' per questo che e' un comando e non uno
 * stato permanente.
 */
public final class Profiler
{
    /**
     * Il profiler spento: un singleton che non misura.
     *
     * <p>È un'istanza normale con il flag abbassato e non una sottoclasse apposta: con una gerarchia
     * la chiamata a {@code enter} dal punto più caldo dell'interprete diventa virtuale, e nel
     * benchmark si vedeva. Così è un campo booleano letto e un salto che il predittore azzecca
     * sempre.
     */
    public static final Profiler OFF = new Profiler(false);

    private final boolean recording;

    /** I numeri di una funzione. Mutabile e con contatori atomici: un'app può girare su più thread. */
    public static final class Entry
    {
        private final String module;
        private final String function;
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong selfNanos = new AtomicLong();

        Entry(String module, String function)
        {
            this.module = module;
            this.function = function;
        }

        public String module()
        {
            return module;
        }

        public String function()
        {
            return function;
        }

        public long calls()
        {
            return calls.get();
        }

        public long totalNanos()
        {
            return totalNanos.get();
        }

        public long selfNanos()
        {
            return selfNanos.get();
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final long startedAtNanos = System.nanoTime();
    private volatile long stoppedAtNanos = 0L;

    /**
     * Il tempo delle chiamate annidate, per thread.
     *
     * <p>Ogni frame aperto mette a zero un accumulatore; quando si chiude, ci somma il proprio tempo
     * totale e lo consegna al frame di sopra. Il tempo proprio di una funzione è il suo totale meno
     * quello che i figli hanno accumulato lì dentro. Per thread perché due task che girano insieme
     * hanno stack diversi e sommarli darebbe numeri senza senso.
     */
    private static final class Stack
    {
        private long[] childNanos = new long[64];
        private int depth = 0;

        void push()
        {
            if (depth == childNanos.length)
            {
                long[] grown = new long[childNanos.length * 2];
                System.arraycopy(childNanos, 0, grown, 0, childNanos.length);
                childNanos = grown;
            }
            childNanos[depth++] = 0L;
        }

        /** @return il tempo che i figli di questo frame hanno accumulato */
        long pop(long elapsed)
        {
            long children = childNanos[--depth];
            if (depth > 0)
            {
                childNanos[depth - 1] += elapsed;
            }
            return children;
        }
    }

    private final ThreadLocal<Stack> stacks = ThreadLocal.withInitial(Stack::new);

    /**
     * Apre un frame.
     *
     * @return l'istante di partenza, da restituire a {@link #exit}: tenerlo sullo stack java del
     *         chiamante evita di dover allocare un oggetto frame per ogni chiamata
     */
    public long enter()
    {
        if (!recording)
        {
            return 0L;
        }
        stacks.get().push();
        return System.nanoTime();
    }

    /**
     * Chiude il frame aperto da {@link #enter} e contabilizza il tempo.
     *
     * <p>I nomi arrivano solo adesso, e non a {@code enter}, perché a profiler spento non devono
     * nemmeno essere calcolati.
     */
    public void exit(String module, String function, long startedAt)
    {
        if (!recording)
        {
            return;
        }
        long elapsed = System.nanoTime() - startedAt;
        long ownChildren = stacks.get().pop(elapsed);
        Entry entry = entries.computeIfAbsent(module + '.' + function, key -> new Entry(module, function));
        entry.calls.incrementAndGet();
        entry.totalNanos.addAndGet(elapsed);
        entry.selfNanos.addAndGet(Math.max(0L, elapsed - ownChildren));
    }

    public Profiler()
    {
        this(true);
    }

    private Profiler(boolean recording)
    {
        this.recording = recording;
    }

    public boolean isRecording()
    {
        return recording && stoppedAtNanos == 0L;
    }

    /** Chiude la misura: da qui in poi la durata riportata non cresce più. */
    public void stop()
    {
        if (stoppedAtNanos == 0L)
        {
            stoppedAtNanos = System.nanoTime();
        }
    }

    /** Quanto è durata la misura, in nanosecondi. */
    public long elapsedNanos()
    {
        return (stoppedAtNanos == 0L ? System.nanoTime() : stoppedAtNanos) - startedAtNanos;
    }

    /** Le funzioni misurate, dalla più costosa in tempo proprio alla meno. */
    public List<Entry> bySelfTime()
    {
        List<Entry> sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator.comparingLong(Entry::selfNanos).reversed());
        return sorted;
    }

    /**
     * Il tempo proprio raggruppato per modulo.
     *
     * <p>Si somma il tempo proprio e non il totale apposta: sommare i totali conterebbe due volte
     * ogni chiamata che un modulo fa a se stesso.
     */
    public List<Entry> byModule()
    {
        Map<String, Entry> perModule = new ConcurrentHashMap<>();
        for (Entry entry : entries.values())
        {
            Entry aggregate = perModule.computeIfAbsent(entry.module(), name -> new Entry(name, "*"));
            aggregate.calls.addAndGet(entry.calls());
            aggregate.selfNanos.addAndGet(entry.selfNanos());
            aggregate.totalNanos.addAndGet(entry.selfNanos());
        }
        List<Entry> sorted = new ArrayList<>(perModule.values());
        sorted.sort(Comparator.comparingLong(Entry::selfNanos).reversed());
        return sorted;
    }

    public boolean isEmpty()
    {
        return entries.isEmpty();
    }
}
