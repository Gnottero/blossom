package blossom.interpreter;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.value.ThreadValue;
import blossom.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Context
{
    public enum Type
    {
        NONE, VOID, BOOLEAN, NUMBER, STRING, LIST, ITERATOR, SIGNATURE, LOCALIZATION, LVALUE, MAPDEF
    }

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

    public Map<String, LazyValue> variables = new HashMap<>();

    public final ScriptHost host;

    private ThreadValue threadContext = null;

    /**
     * Il watchdog di questa esecuzione (PIANO_LINGUAGGIO.md §9).
     *
     * <p>Pubblico e non finale perché è la cosa che viene letta più spesso di tutte - una volta per
     * iterazione di ogni loop - e perché un frame figlio deve poterlo ereditare senza passare da un
     * costruttore che ogni sottoclasse dovrebbe reimplementare. Non è mai {@code null}: senza
     * limiti configurati è il singleton che non misura niente.
     */
    public ExecutionBudget budget = ExecutionBudget.UNLIMITED_BUDGET;

    /**
     * Il profiler di questa esecuzione, o {@link Profiler#OFF} quando non si sta misurando (il caso
     * normale). Vive qui accanto al budget e per gli stessi motivi.
     */
    public Profiler profiler = Profiler.OFF;

    public Context(ScriptHost host)
    {
        this.host = host;
    }

    /**
     * Fa partire un budget nuovo su questo contesto, con la politica dell'app.
     *
     * <p>Da chiamare dove comincia un'esecuzione - un comando, un evento, il corpo di un modulo - e
     * mai più in mezzo: un budget che riparte a metà è un budget che non ferma niente.
     */
    public Context beginExecution()
    {
        return beginExecution(host == null ? ExecutionBudget.Policy.UNLIMITED : host.watchdog());
    }

    /** Come {@link #beginExecution()}, ma con una politica decisa dal chiamante. */
    public Context beginExecution(ExecutionBudget.Policy policy)
    {
        this.budget = policy.start();
        // il profiler si prende dall'host e non dal chiamante: accenderlo deve valere per tutto
        // quello che l'app fa, eventi compresi, e quelli nascono da contesti che nessuno passa
        this.profiler = host == null ? Profiler.OFF : host.profiler();
        return this;
    }

    public LazyValue getVariable(String name)
    {
        return variables.get(name);
    }

    public void setVariable(String name, LazyValue lv)
    {
        variables.put(name, lv);
    }

    public void delVariable(String variable)
    {
        variables.remove(variable);
    }

    public void removeVariablesMatching(String varname)
    {
        variables.entrySet().removeIf(e -> e.getKey().startsWith(varname));
    }

    public Context with(String variable, LazyValue lv)
    {
        variables.put(variable, lv);
        return this;
    }

    public Set<String> getAllVariableNames()
    {
        return variables.keySet();
    }

    public Context recreate()
    {
        Context ctx = duplicate();
        ctx.threadContext = threadContext;
        // il budget si eredita: se ogni frame di chiamata ne avesse uno nuovo, spezzare un ciclo
        // infinito in due funzioni basterebbe ad aggirare il watchdog
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
        //special variables for second order functions so we don't need to check them all the time
        variables.put("_", (c, t) -> Value.ZERO);
        variables.put("_i", (c, t) -> Value.ZERO);
        variables.put("_a", (c, t) -> Value.ZERO);
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
     * immutable context only for reason on reporting access violations in evaluating expressions in optimizization
     * mode detecting any potential violations that may happen on the way
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
    }
}
