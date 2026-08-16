package blossom.interpreter;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.value.ThreadValue;
import blossom.value.Value;

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

    /**
     * Le variabili locali di questo frame. Vedi {@link VariableFrame} per come sono tenute e
     * perché non è una mappa a hash.
     */
    VariableFrame variables = new VariableFrame();

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

    /**
     * Le tre variabili implicite delle funzioni di secondo ordine: l'elemento corrente, il suo
     * indice e l'accumulatore.
     *
     * <p>Campi e non voci della mappa delle variabili perché sono le più scritte e le più lette di
     * tutte: un {@code for} le riassegna ad ogni iterazione, e ogni {@code _} nel corpo le rilegge.
     * Nella mappa, ogni iterazione costava un hash e un inserimento; qui costa un'assegnazione di
     * campo, e il nodo compilato per {@code _} legge il campo senza passare da nessun nome (vedi
     * {@code Expression.variableNode}).
     *
     * <p>Restano raggiungibili per nome - {@code var('_')}, {@code vars('_')} - perché i tre nomi
     * sono deviati qui da {@link #getVariable}, {@link #setVariable} e {@link #delVariable}: quella
     * è la strada fredda, e può permettersi il confronto.
     */
    public LazyValue implicitValue = IMPLICIT_DEFAULT;
    public LazyValue implicitIndex = IMPLICIT_DEFAULT;
    public LazyValue implicitAccumulator = IMPLICIT_DEFAULT;

    private static final LazyValue IMPLICIT_DEFAULT = (c, t) -> Value.ZERO;

    // le letture passano da un metodo e non dal campo, così il contesto della compilazione può
    // rifiutarle come rifiuta ogni altro accesso allo stato di esecuzione
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

    /** I nomi delle tre variabili implicite, per chi le cerca per nome. */
    public static final String IMPLICIT_VALUE = "_";
    public static final String IMPLICIT_INDEX = "_i";
    public static final String IMPLICIT_ACCUMULATOR = "_a";

    /** {@code true} se {@code name} è una delle tre variabili implicite. */
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

    /** Fa condividere a due contesti le stesse variabili locali, invece di copiarle. */
    public void shareVariablesWith(Context other)
    {
        this.variables = other.variables;
    }

    /** Percorre le variabili locali del frame, le tre implicite escluse. */
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
            setVariable(variable, IMPLICIT_DEFAULT);
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
        // le variabili implicite sono campi con un default: un frame nuovo non deve scriverle
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
