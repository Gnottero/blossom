package blossom.interpreter;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.value.ThreadValue;

import java.util.Set;

public class Context
{
    public enum Type
    {
        NONE, VOID, BOOLEAN, NUMBER, STRING, LIST, ITERATOR, SIGNATURE, LOCALIZATION, LVALUE, MAPDEF, MATCH
    }

    public static final Type MATCH = Type.MATCH;
    public static final Type NONE = Type.NONE;
    public static final Type VOID = Type.VOID;
    public static final Type BOOLEAN = Type.BOOLEAN;
    public static final Type NUMBER = Type.NUMBER;
    public static final Type STRING = Type.STRING;
    public static final Type LIST = Type.LIST;
    public static final Type ITERATOR = Type.ITERATOR;
    public static final Type SIGNATURE = Type.SIGNATURE;
    public static final Type LOCALIZATION = Type.LOCALIZATION;
    public static final Type LVALUE = Type.LVALUE;
    public static final Type MAPDEF = Type.MAPDEF;

    /**
     * This frame's local variables. See {@link VariableFrame} for how they're kept and why it's
     * not a hash map.
     */
    VariableFrame variables = new VariableFrame();

    public final ScriptHost host;

    private ThreadValue threadContext = null;

    /**
     * This execution's watchdog.
     *
     * <p>Public and non-final because it's the field read most often of all (once per loop
     * iteration), and because a child frame must be able to inherit it without going through a
     * constructor that every subclass would have to reimplement. Never {@code null}: with no
     * limits configured it's the singleton that measures nothing.
     */
    public ExecutionBudget budget = ExecutionBudget.UNLIMITED_BUDGET;

    /**
     * This execution's profiler, or {@link Profiler#OFF} when nothing is being measured (the
     * normal case). Lives here next to the budget, for the same reasons.
     */
    public Profiler profiler = Profiler.OFF;

    public Context(ScriptHost host)
    {
        this.host = host;
    }

    /**
     * Starts a new budget on this context, using the app's policy.
     *
     * <p>Call this where an execution begins (a command, an event, a module's body) and never
     * again in the middle: a budget that restarts halfway through is a budget that stops nothing.
     */
    public Context beginExecution()
    {
        return beginExecution(host == null ? ExecutionBudget.Policy.UNLIMITED : host.watchdog());
    }

    /** Like {@link #beginExecution()}, but with a policy decided by the caller. */
    public Context beginExecution(ExecutionBudget.Policy policy)
    {
        this.budget = policy.start();
        // The profiler comes from the host, not the caller: turning it on must apply to
        // everything the app does, events included, and those arise from contexts nobody passes.
        this.profiler = host == null ? Profiler.OFF : host.profiler();
        return this;
    }

    /**
     * The three implicit variables of second-order functions: the current element, its index, and
     * the accumulator.
     *
     * <p>Fields rather than entries in the variable map because they're the most written and most
     * read of all: a {@code for} reassigns them on every iteration, and every {@code _} in the
     * body reads them back. In the map, every iteration cost a hash and an insertion; here it
     * costs a field assignment, and the compiled node for {@code _} reads the field without going
     * through any name lookup (see {@code Expression.variableNode}).
     *
     * <p>They remain reachable by name ({@code var('_')}, {@code vars('_')}) because the three
     * names are diverted here by {@link #getVariable}, {@link #setVariable} and
     * {@link #delVariable}: that's the cold path, and it can afford the comparison.
     */
    public LazyValue implicitValue = unbound(IMPLICIT_VALUE);
    public LazyValue implicitIndex = unbound(IMPLICIT_INDEX);
    public LazyValue implicitAccumulator = unbound(IMPLICIT_ACCUMULATOR);

    /**
     * The value of {@code _}, {@code _i} and {@code _a} where no loop bound them.
     *
     * <p>It used to be zero, which made {@code list.map(l, fn() -> _ * 2)} a list of zeros instead of
     * an error: inside a lambda the implicits do not arrive, because a lambda names its own
     * parameters. A silently wrong number is the worst way to fail, so here it says what is crooked
     * and where.
     */
    private static LazyValue unbound(String name)
    {
        return (c, t) ->
        {
            throw new blossom.exception.UndefinedSymbolError(
                    blossom.exception.SourcePosition.UNKNOWN,
                    "'" + name + "' has no value here: it only exists inside the body of a loop or"
                            + " of a second-order function, written as an expression.",
                    "a function written with 'fn' names its own parameters, so write"
                            + " fn(element, index) -> ... and use those names instead of "
                            + name + ".");
        };
    }

    // Reads go through a method rather than the field directly, so the compilation context can
    // reject them the way it rejects any other access to execution state
    public LazyValue implicitValue()
    {
        return implicitValue;
    }

    public LazyValue implicitIndex()
    {
        return implicitIndex;
    }

    public LazyValue implicitAccumulator()
    {
        return implicitAccumulator;
    }

    /** The names of the three implicit variables, for whoever looks them up by name. */
    public static final String IMPLICIT_VALUE = "_";
    public static final String IMPLICIT_INDEX = "_i";
    public static final String IMPLICIT_ACCUMULATOR = "_a";

    /** {@code true} if {@code name} is one of the three implicit variables. */
    public static boolean isImplicitName(String name)
    {
        return name.length() <= 2 && !name.isEmpty() && name.charAt(0) == '_'
                && (name.length() == 1 || name.charAt(1) == 'i' || name.charAt(1) == 'a');
    }

    public LazyValue getVariable(String name)
    {
        if (isImplicitName(name))
        {
            return switch (name)
            {
                case IMPLICIT_VALUE -> implicitValue;
                case IMPLICIT_INDEX -> implicitIndex;
                default -> implicitAccumulator;
            };
        }
        return variables.get(name);
    }

    /** Makes two contexts share the same local variables instead of copying them. */
    public void shareVariablesWith(Context other)
    {
        this.variables = other.variables;
    }

    /** Iterates over the frame's local variables, excluding the three implicit ones. */
    public void forEachVariable(java.util.function.BiConsumer<String, LazyValue> action)
    {
        variables.forEach(action);
    }

    public void setVariable(String name, LazyValue lv)
    {
        if (isImplicitName(name))
        {
            switch (name)
            {
                case IMPLICIT_VALUE -> implicitValue = lv;
                case IMPLICIT_INDEX -> implicitIndex = lv;
                default -> implicitAccumulator = lv;
            }
            return;
        }
        variables.put(name, lv);
    }

    public void delVariable(String variable)
    {
        if (isImplicitName(variable))
        {
            setVariable(variable, unbound(variable));
            return;
        }
        variables.remove(variable);
    }

    public void removeVariablesMatching(String varname)
    {
        variables.removeMatching(name -> name.startsWith(varname));
    }

    public Context with(String variable, LazyValue lv)
    {
        setVariable(variable, lv);
        return this;
    }

    public Set<String> getAllVariableNames()
    {
        Set<String> names = new java.util.LinkedHashSet<>();
        variables.forEach((name, value) -> names.add(name));
        names.add(IMPLICIT_VALUE);
        names.add(IMPLICIT_INDEX);
        names.add(IMPLICIT_ACCUMULATOR);
        return names;
    }

    public Context recreate()
    {
        Context ctx = duplicate();
        ctx.threadContext = threadContext;
        // The budget is inherited: if every call frame got a new one, splitting an infinite loop
        // across two functions would be enough to dodge the watchdog
        ctx.budget = budget;
        ctx.profiler = profiler;
        ctx.initialize();
        return ctx;
    }

    public void setThreadContext(ThreadValue callingThread)
    {
        this.threadContext = callingThread;
    }

    public ThreadValue getThreadContext()
    {
        return threadContext;
    }

    protected void initialize()
    {
        // The implicit variables are fields with a default: a new frame doesn't need to write them
    }

    public Context duplicate()
    {
        return new Context(this.host);
    }

    public ScriptHost.ErrorSnooper getErrorSnooper()
    {
        return host.errorSnooper;
    }

    public ScriptServer scriptServer()
    {
        return host.scriptServer();
    }

    /**
     * Immutable context used only to report access violations when evaluating expressions in
     * optimization mode, detecting any potential violations that may happen along the way.
     */
    public static class ContextForErrorReporting extends Context
    {
        public ScriptHost.ErrorSnooper optmizerEerrorSnooper;

        public ContextForErrorReporting(Context parent)
        {
            super(null);
            optmizerEerrorSnooper = parent.host.errorSnooper;
        }

        @Override
        public ScriptHost.ErrorSnooper getErrorSnooper()
        {
            return optmizerEerrorSnooper;
        }

        public void badProgrammer()
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "context",
                    "Attempting to access the execution context while compiling the code. This is not a problem with "
                            + "your script but a bug in Blossom's compilation step: please report it.");

        }

        @Override
        public LazyValue getVariable(String name)
        {
            badProgrammer();
            return null;
        }

        @Override
        public void setVariable(String name, LazyValue lv)
        {
            badProgrammer();
        }

        @Override
        public void delVariable(String variable)
        {
            badProgrammer();
        }

        @Override
        public void removeVariablesMatching(String varname)
        {
            badProgrammer();
        }

        @Override
        public Context with(String variable, LazyValue lv)
        {
            badProgrammer();
            return this;
        }

        @Override
        public Set<String> getAllVariableNames()
        {
            badProgrammer();
            return null;
        }

        @Override
        public Context recreate()
        {
            badProgrammer();
            return null;
        }

        @Override
        protected void initialize()
        {
            badProgrammer();
        }

        @Override
        public Context duplicate()
        {
            badProgrammer();
            return null;
        }

        @Override
        public LazyValue implicitValue()
        {
            badProgrammer();
            return null;
        }

        @Override
        public LazyValue implicitIndex()
        {
            badProgrammer();
            return null;
        }

        @Override
        public LazyValue implicitAccumulator()
        {
            badProgrammer();
            return null;
        }
    }
}
