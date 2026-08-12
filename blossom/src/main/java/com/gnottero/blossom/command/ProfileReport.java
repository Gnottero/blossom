package com.gnottero.blossom.command;

import java.util.List;
import java.util.Locale;

import blossom.interpreter.Profiler;

/**
 * Il rapporto di {@code /blossom profile}, in una forma leggibile in chat.
 *
 * <p>Due tabelle: prima i moduli, perché la prima domanda è "chi si mangia il tick", poi le funzioni
 * più costose, perché la seconda è "quale riga vado a guardare". Di ogni riga si mostrano tempo
 * proprio e tempo totale: una funzione con il totale enorme e il proprio minuscolo non è lenta, è
 * solo quella in cima allo stack, e ottimizzarla non cambia niente.
 */
public final class ProfileReport
{
    /** quante funzioni mostrare: oltre la ventina la chat diventa illeggibile e nessuno le legge */
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
