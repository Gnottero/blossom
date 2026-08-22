package blossom.interpreter;

import blossom.exception.ExecutionBudgetError;
import blossom.exception.SourcePosition;

/**
 * The watchdog: how long an execution may run before it gets stopped (PIANO_LINGUAGGIO.md §9).
 *
 * <p>Code runs inside the server's tick loop: a script that loops forever inside an event doesn't
 * just slow the app down, it stops the world.
 *
 * <p>The counter is only ticked where execution could repeat forever (loop iterations and user
 * function calls), because an expression with no loops and no calls is finite by construction.
 * The clock is read only once every {@link #CLOCK_CHECK_MASK}+1 ticks: inside the tight loop of a
 * tree-walking interpreter, {@code System.nanoTime()} shows up in the benchmark.
 *
 * <p>An instance measures a single execution (an event, a command, a load), lives on the
 * {@link Context}, and is inherited by child frames, otherwise splitting the loop across two
 * functions would be enough to dodge it. A {@code task()} instead gets its own budget from the
 * same policy: it runs on its own thread and doesn't occupy the tick.
 */
public final class ExecutionBudget
{
    /**
     * How many ticks between clock checks: 4096 ticks of an empty loop take on the order of tens
     * of microseconds, so the watchdog's resolution stays well under a tick.
     */
    private static final int CLOCK_CHECK_MASK = 0xFFF;

    /**
     * The limits, without the state: this is what an app declares in its manifest and what the
     * server keeps as a default. Immutable, so shareable across all of an app's executions.
     *
     * @param timeoutMillis maximum time for an execution, {@code 0} for no limit
     * @param maxDepth      nested function calls, {@code 0} for no limit
     */
    public record Policy(long timeoutMillis, int maxDepth)
    {
        /** No limit: the language's own tests and whoever embeds the core outside a server. */
        public static final Policy UNLIMITED = new Policy(0, 0);

        /**
         * The default inside the game.
         *
         * <p>Two seconds is a lot for a tick (which lasts 0.05) and very little for an infinite
         * loop: the threshold isn't meant to enforce the tick budget (that's the script author's
         * job), but to distinguish "slow" from "will never finish". The max depth kicks in before
         * the JVM's own {@code StackOverflowError}, so the error tells you where you are instead
         * of being a raw Java stack crash.
         */
        public static final Policy DEFAULT = new Policy(2000, 256);

        /**
         * The default for whatever the user launched by hand: a command or an app load may
         * legitimately grind on for a while, and whoever pressed enter is waiting.
         */
        public static final Policy INTERACTIVE = new Policy(15000, 256);

        /**
         * The limits of a {@code task()}.
         *
         * <p>No time limit, but the call depth still applies: a task runs on its own thread and
         * doesn't hold up the tick, and "runs for a long time" is exactly why one is launched.
         * Capping it at two seconds would defeat the purpose of the feature meant to work around
         * the problem the watchdog solves.
         */
        public Policy asBackground()
        {
            return new Policy(0, maxDepth);
        }

        public boolean isUnlimited()
        {
            return timeoutMillis <= 0 && maxDepth <= 0;
        }

        /** A fresh meter, with its clock starting now. */
        public ExecutionBudget start()
        {
            return isUnlimited() ? UNLIMITED_BUDGET : new ExecutionBudget(this);
        }
    }

    /**
     * The meter that measures nothing: a singleton, so a context with no watchdog allocates
     * nothing and its ticks are just a predictable branch.
     */
    public static final ExecutionBudget UNLIMITED_BUDGET = new ExecutionBudget(Policy.UNLIMITED);

    private final Policy policy;
    private final long deadlineNanos;
    private long ticks;
    private int depth;

    private ExecutionBudget(Policy policy)
    {
        this.policy = policy;
        this.deadlineNanos = policy.timeoutMillis() <= 0
                ? Long.MAX_VALUE
                : System.nanoTime() + policy.timeoutMillis() * 1_000_000L;
    }

    public Policy policy()
    {
        return policy;
    }

    /** How many iterations/calls have been counted so far: used by the profiler for its final report. */
    public long ticks()
    {
        return ticks;
    }

    /**
     * A tick: called by whoever is about to repeat something (a loop iteration) or about to enter
     * a function.
     *
     * <p>Deliberately kept short so the JVM inlines it away when the budget is unlimited.
     */
    public void tick()
    {
        if (deadlineNanos == Long.MAX_VALUE)
        {
            return;
        }
        if ((++ticks & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos)
        {
            throw timedOut();
        }
    }

    /** Enters a function call: counts as both a tick and a depth level. */
    public void enterCall(String function)
    {
        tick();
        if (policy.maxDepth() > 0 && ++depth > policy.maxDepth())
        {
            depth--; // The frame was never opened: it must not stay counted
            throw new ExecutionBudgetError(SourcePosition.UNKNOWN, ExecutionBudgetError.Limit.DEPTH,
                    "Too many nested calls (limit: " + policy.maxDepth() + ") when entering '" + function
                            + "'. If the recursion is intended, raise 'max_depth' in the manifest; if it is not,"
                            + " a base case is missing.");
        }
    }

    public void exitCall()
    {
        if (policy.maxDepth() > 0)
        {
            depth--;
        }
    }

    private ExecutionBudgetError timedOut()
    {
        return new ExecutionBudgetError(SourcePosition.UNKNOWN, ExecutionBudgetError.Limit.TIME,
                "Execution stopped after " + policy.timeoutMillis() + " ms: the script is running too long and the"
                        + " server is waiting for it. If it really needs all that time, move it to a task.start() or"
                        + " raise 'timeout_ms' in the manifest.");
    }
}
