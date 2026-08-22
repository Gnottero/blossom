package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import blossom.interpreter.Interpreter;
import blossom.interpreter.Profiler;

/**
 * The per function profiler (PIANO_LINGUAGGIO.md §9): what sits behind {@code /blossom profile}.
 *
 * <p>The tests check what the profiler <em>attributes</em>, not how many milliseconds it measures:
 * absolute times depend on the machine and a test checking them is a test that fails at random. The
 * only relation between times that always holds is that self time never exceeds total time.
 */
class ProfilerTest
{
    private static Optional<Profiler.Entry> entryFor(Profiler profiler, String function)
    {
        return profiler.bySelfTime().stream().filter(e -> e.function().equals(function)).findFirst();
    }

    @Test
    void countsCallsPerFunction()
    {
        Interpreter interpreter = Interpreter.standalone();
        Profiler profiler = interpreter.host().startProfiling();
        interpreter.eval("f(x) -> x * 2; g(x) -> f(x) + f(x); c_for(i = 0, i < 10, i += 1, g(i))");
        interpreter.host().stopProfiling();

        assertEquals(10, entryFor(profiler, "g").orElseThrow().calls());
        assertEquals(20, entryFor(profiler, "f").orElseThrow().calls());
    }

    @Test
    void selfTimeExcludesWhatTheCalleesTook()
    {
        Interpreter interpreter = Interpreter.standalone();
        Profiler profiler = interpreter.host().startProfiling();
        // 'wrapper' does nothing of its own: all of its time is 'work' time
        interpreter.eval("work() -> c_for(i = 0, i < 20000, i += 1, null);"
                + "wrapper() -> work();"
                + "wrapper()");
        interpreter.host().stopProfiling();

        Profiler.Entry wrapper = entryFor(profiler, "wrapper").orElseThrow();
        Profiler.Entry work = entryFor(profiler, "work").orElseThrow();
        assertTrue(wrapper.selfNanos() <= wrapper.totalNanos(), "il tempo proprio non puo' superare il totale");
        assertTrue(work.selfNanos() > wrapper.selfNanos(),
                "il lavoro va attribuito a chi lo fa, non a chi lo racchiude: "
                        + work.selfNanos() + " vs " + wrapper.selfNanos());
    }

    @Test
    void recursionDoesNotCountTheSameTimeTwice()
    {
        Interpreter interpreter = Interpreter.standalone();
        Profiler profiler = interpreter.host().startProfiling();
        interpreter.eval("f(n) -> if(n <= 0, 0, f(n - 1) + 1); f(50)");
        interpreter.host().stopProfiling();

        Profiler.Entry entry = entryFor(profiler, "f").orElseThrow();
        assertEquals(51, entry.calls());
        // the total of a recursive function sums nested frames, so it can exceed the measurement
        // duration; self time cannot, which is why the report is sorted on it
        assertTrue(entry.selfNanos() <= profiler.elapsedNanos(),
                entry.selfNanos() + " > " + profiler.elapsedNanos());
    }

    @Test
    void aggregatesPerModule()
    {
        Interpreter interpreter = Interpreter.standalone();
        Profiler profiler = interpreter.host().startProfiling();
        interpreter.eval("f(x) -> x + 1; f(1)");
        interpreter.host().stopProfiling();

        List<Profiler.Entry> modules = profiler.byModule();
        assertEquals(1, modules.size());
        assertEquals(1, modules.getFirst().calls());
    }

    @Test
    void isOffByDefaultAndGoesBackOffAfterwards()
    {
        Interpreter interpreter = Interpreter.standalone();
        assertSame(Profiler.OFF, interpreter.host().profiler());
        Profiler profiler = interpreter.host().startProfiling();
        assertTrue(profiler.isRecording());
        interpreter.host().stopProfiling();
        assertFalse(profiler.isRecording());
        assertSame(Profiler.OFF, interpreter.host().profiler());
    }

    @Test
    void whenOffNothingIsRecorded()
    {
        Interpreter interpreter = Interpreter.standalone();
        interpreter.eval("f(x) -> x + 1; f(1)");
        assertTrue(interpreter.host().profiler().isEmpty());
    }
}
