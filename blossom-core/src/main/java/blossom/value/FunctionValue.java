package blossom.value;

import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Blossom;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Callables;
import blossom.interpreter.LazyValue;
import blossom.interpreter.Module;
import blossom.interpreter.Token;
import blossom.exception.BreakStatement;
import blossom.exception.ContinueStatement;
import blossom.exception.ExpressionException;
import blossom.exception.ArityError;
import blossom.exception.InternalExpressionException;
import blossom.exception.SourcePosition;
import blossom.exception.ReturnStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


import org.jspecify.annotations.Nullable;

public class FunctionValue extends Value implements Callables.ILazyFunction
{
    private final Expression expression;
    private final Token token;
    private final String name;
    private final LazyValue body;
    private Map<String, LazyValue> outerState;
    private final List<String> args;
    /**
     * The canonical type declared for each parameter, or {@code null} if the signature declares
     * none. In the normal case (no types) the runtime check is just a comparison with null.
     */
    private final @Nullable List<String> argumentTypes;
    /**
     * The fallback value for each parameter, {@code null} where there isn't one, and the whole
     * list {@code null} when no parameter has one. In the normal case the cost is just a
     * comparison with null.
     */
    private final @Nullable List<LazyValue> defaults;
    private final String varArgs;
    /**
     * The module name, cached on first request because the profiler asks for it on every call.
     * Not computed in the constructor: the expression may not yet know which module it belongs to.
     */
    private String moduleName = null;
    /**
     * closures are created from any thread, and a plain increment would give two of them the same
     * variant, which is part of how a function is identified
     */
    private static final AtomicLong variantCounter = new AtomicLong(1);
    private long variant;

    private FunctionValue(Expression expression, Token token, String name, LazyValue body, List<String> args,
            @Nullable List<String> argumentTypes, String varArgs)
    {
        this.expression = expression;
        this.token = token;
        this.name = name;
        this.body = body;
        this.args = args;
        this.argumentTypes = argumentTypes;
        this.defaults = null;
        this.varArgs = varArgs;
        this.outerState = null;
        variant = 0L;
    }

    public FunctionValue(Expression expression, Token token, String name, LazyValue body, List<String> args, String varArgs, Map<String, LazyValue> outerState)
    {
        this(expression, token, name, body, args, null, varArgs, outerState);
    }

    public FunctionValue(Expression expression, Token token, String name, LazyValue body, List<String> args,
            @Nullable List<String> argumentTypes, String varArgs, Map<String, LazyValue> outerState)
    {
        this(expression, token, name, body, args, argumentTypes, null, varArgs, outerState);
    }

    public FunctionValue(Expression expression, Token token, String name, LazyValue body, List<String> args,
            @Nullable List<String> argumentTypes, @Nullable List<LazyValue> defaults, String varArgs,
            Map<String, LazyValue> outerState)
    {
        this.defaults = defaults;
        this.expression = expression;
        this.token = token;
        this.name = name;
        this.body = body;
        this.args = args;
        this.argumentTypes = argumentTypes;
        this.varArgs = varArgs;
        this.outerState = outerState;
        variant = variantCounter.getAndIncrement();
    }

    @Override
    public String getString()
    {
        return name;
    }

    public Module getModule()
    {
        return expression.module;
    }

    @Override
    public String getPrettyString()
    {
        List<String> stringArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++)
        {
            String declared = argumentTypes == null ? null : argumentTypes.get(i);
            stringArgs.add(declared == null ? args.get(i) : args.get(i) + ": " + declared);
        }
        if (outerState != null)
        {
            stringArgs.addAll(outerState.entrySet().stream().map(e ->
                    "outer(" + e.getKey() + ") = " + e.getValue().evalValue(null).getPrettyString()).toList());
        }
        return (name.equals("_") ? "<lambda>" : name) + "(" + String.join(", ", stringArgs) + ")";
    }

    /**
     * The module the function is defined in, for the profiler; {@code "<code>"} outside a
     * module. Computed once because it's read by the hottest point of the interpreter.
     */
    public String moduleName()
    {
        String known = moduleName;
        if (known == null)
        {
            known = expression.module == null ? "<code>" : expression.module.name();
            moduleName = known;
        }
        return known;
    }

    public String fullName()
    {
        return (name.equals("_") ? "<lambda>" : name) + (expression.module == null ? "" : "[" + expression.module.name() + "]");
    }

    @Override
    public boolean getBoolean()
    {
        return true;
    }

    @Override
    protected Value clone()
    {
        FunctionValue ret = new FunctionValue(expression, token, name, body, args, argumentTypes, varArgs);
        ret.outerState = this.outerState;
        ret.variant = this.variant;
        return ret;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode() + (int) variant;
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof FunctionValue fv && name.equals(fv.name) && variant == fv.variant;
    }

    @Override
    public int compareTo(Value o)
    {
        if (o instanceof FunctionValue fv)
        {
            int nameSame = this.name.compareTo(fv.name);
            return nameSame != 0 ? nameSame : (int) (variant - fv.variant);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public double readDoubleNumber()
    {
        return getNumParams();
    }

    @Override
    public String getTypeString()
    {
        return "function";
    }

    @Override
    public Value slice(long from, Long to)
    {
        throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot slice a function");
    }

    @Override
    public int getNumParams()
    {
        return args.size();
    }

    @Override
    public boolean numParamsVaries()
    {
        return varArgs != null;
    }

    public LazyValue callInContext(Context c, Context.Type type, List<Value> params)
    {
        try
        {
            return execute(c, type, expression, token, params, null);
        }
        catch (ExpressionException exc)
        {
            exc.stack.add(this);
            throw exc;
        }
        catch (final blossom.exception.BlossomError typed)
        {
            // A typed error propagates with its own frame: wrapping it in an ExpressionException
            // here would erase category, position, and suggestion, i.e. everything §4 of the
            // plan exists for
            throw typed.pushFrame(fullName(), blossom.exception.BlossomError.positionOf(expression, token));
        }
        catch (InternalExpressionException exc)
        {
            exc.stack.add(this);
            throw new ExpressionException(c, expression, token, exc.getMessage(), exc.stack);
        }
        catch (ArithmeticException exc)
        {
            throw new ExpressionException(c, expression, token, "Your math is wrong, " + exc.getMessage(), Collections.singletonList(this));
        }
    }

    /**
     * The arguments in parameter order, with names resolved and defaults filled in.
     *
     * <p>Callers using neither feature pay only a null comparison and an {@code instanceof} check
     * on the last argument: named arguments go last, as in every language that has them, so
     * looking at the last one is enough to know whether there are any.
     */
    private List<Value> resolveArguments(Context c, List<Value> params, Expression e, Token t)
    {
        boolean named = !params.isEmpty() && params.get(params.size() - 1) instanceof NamedArgumentValue;
        if (!named && defaults == null)
        {
            return params;
        }
        Value[] resolved = new Value[args.size()];
        List<Value> extra = new ArrayList<>();
        boolean seenNamed = false;
        for (Value param : params)
        {
            if (param instanceof final NamedArgumentValue argument)
            {
                seenNamed = true;
                int slot = args.indexOf(argument.name());
                if (slot < 0)
                {
                    throw ArityError.at(e, t, "Function " + name + " has no parameter named '"
                            + argument.name() + "'. It wants " + args + ".");
                }
                if (resolved[slot] != null)
                {
                    throw ArityError.at(e, t, "Parameter '" + argument.name() + "' of " + name
                            + " is given twice.");
                }
                resolved[slot] = argument.value().evalValue(c, Context.NONE);
                continue;
            }
            if (seenNamed)
            {
                throw ArityError.at(e, t, "In a call to " + name + ", an argument without a name "
                        + "comes after one with a name: the named ones go last.");
            }
            int next = firstEmpty(resolved);
            if (next < 0)
            {
                extra.add(param);
            }
            else
            {
                resolved[next] = param;
            }
        }
        if (defaults != null)
        {
            for (int i = 0; i < resolved.length; i++)
            {
                if (resolved[i] == null && defaults.get(i) != null)
                {
                    resolved[i] = defaults.get(i).evalValue(c, Context.NONE);
                }
            }
        }
        List<Value> result = new ArrayList<>(resolved.length + extra.size());
        for (Value value : resolved)
        {
            if (value == null)
            {
                break;   // genuinely missing: assertArgsOk reports it, with the message it already knows how to give
            }
            result.add(value);
        }
        result.addAll(extra);
        return result;
    }

    private static int firstEmpty(Value[] slots)
    {
        for (int i = 0; i < slots.length; i++)
        {
            if (slots[i] == null)
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * How many arguments must be passed no matter what: the parameters, minus the ones that have
     * a fallback value.
     */
    public int requiredArguments()
    {
        if (defaults == null)
        {
            return args.size();
        }
        int required = args.size();
        for (LazyValue fallback : defaults)
        {
            if (fallback != null)
            {
                required--;
            }
        }
        return required;
    }

    public void checkArgs(int candidates)
    {
        int actual = getArguments().size();
        int required = requiredArguments();

        if (candidates < required)
        {
            throw new ArityError(SourcePosition.UNKNOWN, "Function " + getPrettyString() + " wants at least " + required + " arguments, got " + candidates + ".");
        }
        if (candidates > actual && getVarArgs() == null)
        {
            throw new ArityError(SourcePosition.UNKNOWN, "Function " + getPrettyString() + " wants " + actual + " arguments, got " + candidates + ".");
        }
    }

    public static List<Value> unpackArgs(List<LazyValue> lazyParams, Context c)
    {
        // TODO we shoudn't need that if all fuctions are not lazy really
        List<Value> params = new ArrayList<>();
        for (LazyValue lv : lazyParams)
        {
            Value param = lv.evalValue(c, Context.NONE);
            if (param instanceof FunctionUnpackedArgumentsValue)
            {
                Blossom.LOG.error("How did we get here?");
                params.addAll(((ListValue) param).getItems());
            }
            else
            {
                params.add(param);
            }
        }
        return params;
    }

    @Override
    public LazyValue lazyEval(Context c, Context.Type type, Expression e, Token t, List<LazyValue> lazyParams)
    {
        List<Value> resolvedParams = unpackArgs(lazyParams, c);
        return execute(c, type, e, t, resolvedParams, null);
    }

    public LazyValue execute(Context c, Context.Type type, Expression e, Token t, List<Value> params, @Nullable ThreadValue freshNewCallingThread)
    {
        final List<Value> arguments = resolveArguments(c, params, e, t);
        assertArgsOk(arguments, fixedArgs -> {
            if (fixedArgs)  // wrong number of args for fixed args
            {
                throw ArityError.at(e, t, "Function " + name + " wants " + args.size()
                        + " arguments " + args + ", got " + arguments.size() + ".");
            }
            // too few args for varargs

            List<String> argList = new ArrayList<>(args);
            argList.add("... " + varArgs);
            throw ArityError.at(e, t, "Function " + name + " wants at least " + args.size()
                    + " arguments " + argList + ", got " + arguments.size() + ".");
        });
        Context newFrame = c.recreate();
        if (freshNewCallingThread != null)
        {
            newFrame.setThreadContext(freshNewCallingThread);
        }

        if (outerState != null)
        {
            outerState.forEach(newFrame::setVariable);
        }
        for (int i = 0; i < args.size(); i++)
        {
            String arg = args.get(i);
            // No copy: the variable name no longer lives on the value, so binding an argument to
            // a parameter can no longer rename it out from under the caller. See D-093
            Value val = arguments.get(i);
            if (argumentTypes != null)
            {
                checkParameterType(i, arg, val, e, t);
            }
            newFrame.setVariable(arg, val);
        }
        if (varArgs != null)
        {
            List<Value> extraParams = new ArrayList<>();
            for (int i = args.size(), mx = arguments.size(); i < mx; i++)
            {
                extraParams.add(arguments.get(i));
            }
            newFrame.setVariable(varArgs, ListValue.wrap(extraParams));
        }
        Value retVal;
        // The watchdog counts the call before opening it and closes it in every case, even when
        // the function exits via exception: a frame left counted would make the next call fail
        newFrame.budget.enterCall(name);
        blossom.interpreter.Profiler profiler = newFrame.profiler;
        long profileStart = profiler.enter(); // with the profiler off this is just a field read and a return
        try
        {
            retVal = body.evalValue(newFrame, type); // todo not sure if we need to propagete type / consider boolean context in defined functions - answer seems ye
        }
        catch (BreakStatement | ContinueStatement exc)
        {
            throw new ExpressionException(c, e, t, "'continue' and 'break' can only be called inside loop function bodies");
        }
        catch (ReturnStatement returnStatement)
        {
            retVal = returnStatement.retval;
        }
        finally
        {
            profiler.exit(moduleName(), name, profileStart);
            newFrame.budget.exitCall();
        }
        return retVal;
    }

    /**
     * Checks a parameter's declared type.
     *
     * <p>The check happens at call time, not at load time: without inference over the body
     * there's no way to know statically what will arrive. What's gained is still the bulk of the
     * benefit: the error fires at the function boundary, saying which parameter and what arrived,
     * instead of surfacing three lines into the body on an unrelated operation.
     */
    private void checkParameterType(int index, String parameter, Value value, Expression e, Token t)
    {
        String declared = argumentTypes.get(index);
        if (declared == null || ValueTypes.matches(declared, value))
        {
            return;
        }
        throw new TypeMismatchError(blossom.exception.BlossomError.positionOf(e, t),
                "Parameter '" + parameter + "' of " + name + " wants a " + declared
                        + ", but got a " + value.getTypeString() + " (" + value.getPrettyString() + ").");
    }

    public Token getToken()
    {
        return token;
    }

    public List<String> getArguments()
    {
        return args;
    }

    public String getVarArgs()
    {
        return varArgs;
    }


    public void assertArgsOk(List<?> list, Consumer<Boolean> feedback)
    {
        int size = list.size();
        if (varArgs == null && args.size() != size) // wrong number of args for fixed args
        {
            feedback.accept(true);
        }
        else if (varArgs != null && args.size() > size) // too few args for varargs
        {
            feedback.accept(false);
        }
    }

    @Override
    public boolean pure()
    {
        return false;
    }

    @Override
    public boolean transitive()
    {
        return false;
    }
}
