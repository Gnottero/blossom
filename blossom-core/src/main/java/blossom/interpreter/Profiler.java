package blossom.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where the time goes, per function and per module.
 *
 * <p>{@code profile_expr} measures a single expression and answers "how long does this piece take";
 * this answers "who eats the tick", which is the real question once an app has twenty handlers.
 *
 * <p>Two numbers are kept per function: the <em>total</em> includes the functions it called, the
 * <em>self</em> time is what remains once they are removed. The pair is needed, since a huge total
 * with a tiny self time only means that function sits on top, and optimising it changes nothing.
 *
 * <p>Off, {@link #OFF} does two returns: no clock, no maps, no allocations. On, it costs two
 * {@code System.nanoTime()} per call, which is why it is a command and not a permanent state.
 */
public final class Profiler
{
    /**
     * The profiler when off: a singleton that measures nothing.
     *
     * <p>It is a normal instance with the flag down and not a subclass, on purpose: with a hierarchy
     * the {@code enter} call from the hottest point of the interpreter becomes virtual, and the
     * benchmark showed it. This way it is one boolean field read and a branch the predictor always
     * gets right.
     */
    public static final Profiler OFF = new Profiler(false);

    private final boolean recording;

    /** The numbers of one function. Mutable with atomic counters: an app can run on many threads. */
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
     * The time of nested calls, per thread.
     *
     * <p>Every frame opened zeroes an accumulator; when it closes, it adds its own total time to it
     * and hands it to the frame above. The self time of a function is its total minus what the
     * children accumulated inside it. Per thread because two tasks running together have different
     * stacks and summing them would give meaningless numbers.
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

        /** @return the time the children of this frame accumulated */
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
     * Opens a frame.
     *
     * @return the start instant, to be handed back to {@link #exit}: keeping it on the caller's java
     *         stack avoids allocating a frame object per call
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
     * Closes the frame opened by {@link #enter} and accounts for the time.
     *
     * <p>The names arrive only now, and not at {@code enter}, because with the profiler off they
     * must not even be computed.
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

    /** Closes the measurement: from here on the reported duration stops growing. */
    public void stop()
    {
        if (stoppedAtNanos == 0L)
        {
            stoppedAtNanos = System.nanoTime();
        }
    }

    /** How long the measurement lasted, in nanoseconds. */
    public long elapsedNanos()
    {
        return (stoppedAtNanos == 0L ? System.nanoTime() : stoppedAtNanos) - startedAtNanos;
    }

    /** The measured functions, from the most expensive in self time to the least. */
    public List<Entry> bySelfTime()
    {
        List<Entry> sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator.comparingLong(Entry::selfNanos).reversed());
        return sorted;
    }

    /**
     * The self time grouped by module.
     *
     * <p>Self time is summed and not total time on purpose: summing totals would count every call a
     * module makes to itself twice.
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
