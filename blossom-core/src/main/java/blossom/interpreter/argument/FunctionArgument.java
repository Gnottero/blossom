package blossom.interpreter.argument;

import blossom.exception.SourcePosition;
import blossom.exception.NullReferenceError;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Context;
import blossom.interpreter.LazyValue;
import blossom.interpreter.Module;
import blossom.interpreter.ScriptHost;
import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.value.FunctionValue;
import blossom.value.NamedArgumentValue;
import blossom.value.Value;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FunctionArgument extends Argument
{
    public final FunctionValue function;
    public final List<Value> args;

    private FunctionArgument(@Nullable FunctionValue function, int offset, List<Value> args)
    {
        super(offset);
        this.function = function;
        this.args = args;
    }

    /**
     * @param c         context
     * @param module    module
     * @param params    list of params
     * @param offset    offset where to start looking for functional argument
     * @param allowNone none indicates no function present, otherwise it will croak
     * @param checkArgs whether the caller expects trailing parameters to fully resolve function argument list
     *                  if not - argument count check will not be performed and its up to the caller to verify
     *                  if the number of supplied arguments is right
     * @return argument data
     */
    public static FunctionArgument findIn(
            Context c,
            Module module,
            List<Value> params,
            int offset,
            boolean allowNone,
            boolean checkArgs)
    {
        Value functionValue = params.get(offset);
        if (functionValue.isNull())
        {
            if (allowNone)
            {
                return new FunctionArgument(null, offset + 1, Collections.emptyList());
            }
            throw new NullReferenceError(SourcePosition.UNKNOWN,"function argument cannot be null");
        }
        if (!(functionValue instanceof FunctionValue))
        {
            // only a name can be looked up among the functions: a number or a list never were one,
            // and looking them up anyway said "Function '5' is not defined yet", which sends people
            // hunting for a missing definition instead of the value that was not a function
            if (!(functionValue instanceof blossom.value.StringValue))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN, functionValue.getTypeString()
                        + " (" + functionValue.getString() + ") is not a function and cannot be called.");
            }
            functionValue = resolveByName(c, module, functionValue.getString());
        }
        FunctionValue fun = (FunctionValue) functionValue;
        int argsize = fun.getArguments().size();
        // With named arguments the count here says nothing useful: two arguments for one
        // parameter might mean "the same one twice", not "one too many", and only the function's
        // own signature (which has the names) can tell precisely.
        boolean named = !params.isEmpty() && params.get(params.size() - 1) instanceof NamedArgumentValue;
        if (checkArgs && !named)
        {
            // The minimum is the parameters without a default value: the ones that have one
            // may be omitted by the caller, and 'execute' fills them in.
            int required = fun.requiredArguments();
            int extraargs = params.size() - required - offset - 1;
            if (extraargs < 0)
            {
                throw new ArityError(SourcePosition.UNKNOWN, "Function " + fun.getPrettyString() + " wants at least " + required + " arguments.");
            }
            if (params.size() - offset - 1 > argsize && fun.getVarArgs() == null)
            {
                throw new ArityError(SourcePosition.UNKNOWN, "Function " + fun.getPrettyString() + " wants " + argsize + " arguments.");
            }
        }
        List<Value> lvargs = new ArrayList<>();
        for (int i = offset + 1, mx = params.size(); i < mx; i++)
        {
            lvargs.add(params.get(i));
        }
        return new FunctionArgument(fun, offset + 1 + argsize, lvargs);
    }

    /**
     * Resolves the function that name refers to: first a declared function, then a variable
     * holding one.
     *
     * <p>The order matters and is what keeps the cost where it belongs: a call to a declared
     * function (the normal case) resolves on the first try and pays nothing extra. Reading the
     * variable is a cost paid only by whoever actually calls a function value, i.e. writes
     * {@code f(x)} with {@code f} bound to a lambda.
     *
     * <p>A qualified name ({@code vec.add}) skips the second step: a variable name never
     * contains a dot, so there is nothing to look up there.
     */
    private static FunctionValue resolveByName(Context c, Module module, String name)
    {
        if (ScriptHost.isQualified(name))
        {
            return c.host.getAssertFunction(module, name);
        }
        FunctionValue declared = c.host.getFunction(module, name);
        if (declared != null)
        {
            return declared;
        }
        LazyValue bound = c.getVariable(name);
        if (bound != null && bound.evalValue(c, Context.NONE) instanceof FunctionValue held)
        {
            return held;
        }
        // Neither a function nor a variable: the host already knows how to build the right error
        return c.host.getAssertFunction(module, name);
    }

    public List<Value> checkedArgs()
    {
        function.checkArgs(args.size());
        return args;
    }
}
