package com.gnottero.blossom.command;

import java.util.List;
import java.util.Locale;

import blossom.interpreter.Profiler;

/**
 * The report for {@code /blossom profile}, formatted to be readable in chat.
 *
 * <p>Two tables: first the modules, because the first question is "who is eating the tick", then
 * the most expensive functions, because the second question is "which line should I look at".
 * Each row shows both self time and total time: a function with a huge total but a tiny self time
 * is not slow, it is just the one sitting at the top of the stack, and optimizing it changes nothing.
 */
public final class ProfileReport
{
    /** How many functions to show: past about twenty, the chat becomes unreadable and nobody reads them. */
    private static final int TOP_FUNCTIONS = 15;

    private ProfileReport()
    {
    }

    public static String format(Profiler profiler, String what)
    {
        if (profiler.isEmpty())
        {
            return "No function call recorded for " + what
                    + ". The profiler measures functions defined in scripts: if the code has none,"
                    + " there is nothing to attribute.";
        }
        double totalMillis = profiler.elapsedNanos() / 1_000_000.0;
        StringBuilder out = new StringBuilder();
        out.append("Profile of ").append(what).append(" — ")
           .append(String.format(Locale.ROOT, "%.2f ms total", totalMillis));

        out.append("\n\nPer module (self time):");
        for (Profiler.Entry entry : profiler.byModule())
        {
            out.append(String.format(Locale.ROOT, "%n  %-28s %9.3f ms  %5.1f%%  %d calls",
                    entry.module(), millis(entry.selfNanos()), percent(entry.selfNanos(), profiler), entry.calls()));
        }

        List<Profiler.Entry> functions = profiler.bySelfTime();
        out.append("\n\nMost expensive functions (self / total):");
        for (Profiler.Entry entry : functions.subList(0, Math.min(TOP_FUNCTIONS, functions.size())))
        {
            out.append(String.format(Locale.ROOT, "%n  %-34s %9.3f / %9.3f ms  %d calls",
                    entry.module() + '.' + entry.function(),
                    millis(entry.selfNanos()), millis(entry.totalNanos()), entry.calls()));
        }
        if (functions.size() > TOP_FUNCTIONS)
        {
            out.append("\n  ... and ").append(functions.size() - TOP_FUNCTIONS).append(" more");
        }
        return out.toString();
    }

    private static double millis(long nanos)
    {
        return nanos / 1_000_000.0;
    }

    private static double percent(long nanos, Profiler profiler)
    {
        long elapsed = profiler.elapsedNanos();
        return elapsed <= 0 ? 0.0 : 100.0 * nanos / elapsed;
    }
}
