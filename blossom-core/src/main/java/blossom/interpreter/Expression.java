package blossom.interpreter;

import blossom.interpreter.Callables.AbstractFunction;
import blossom.interpreter.Callables.AbstractLazyFunction;
import blossom.interpreter.Callables.AbstractLazyOperator;
import blossom.interpreter.Callables.AbstractOperator;
import blossom.interpreter.Callables.AbstractUnaryOperator;
import blossom.interpreter.Callables.ILazyFunction;
import blossom.interpreter.Callables.ILazyOperator;
import blossom.interpreter.Callables.QuadFunction;
import blossom.interpreter.Callables.QuinnFunction;
import blossom.interpreter.Callables.SexFunction;
import blossom.interpreter.Callables.TriFunction;
import blossom.exception.BreakStatement;
import blossom.exception.ContinueStatement;
import blossom.exception.ExitStatement;
import blossom.exception.ExpressionException;
import blossom.exception.IntegrityException;
import blossom.exception.BlossomError;
import blossom.exception.Suggestions;
import blossom.exception.BlossomRuntimeError;
import blossom.exception.ImportError;
import blossom.exception.InternalExpressionException;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.exception.UndefinedSymbolError;
import blossom.exception.ResolvedException;
import blossom.exception.ReturnStatement;
import blossom.language.Arithmetic;
import blossom.language.ControlFlow;
import blossom.language.DataStructures;
import blossom.language.Http;
import blossom.language.Io;
import blossom.language.Functions;
import blossom.language.Loops;
import blossom.language.Modules;
import blossom.language.StdLib;
import blossom.language.Operators;
import blossom.language.Sys;
import blossom.language.Threading;
import blossom.value.FunctionValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.UndefValue;
import blossom.value.Value;
import blossom.value.VariableReferenceValue;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import org.jspecify.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Expression
{
    /** The current infix expression. */
    private String expression;

    String getCodeString()
    {
        return expression;
    }

    private boolean allowNewlineSubstitutions = true;
    private boolean allowComments = false;

    @Nullable
    public Module module = null;

    public String getModuleName()
    {
        return module == null ? "system chat" : module.name();
    }

    public void asATextSource()
    {
        allowNewlineSubstitutions = false;
        allowComments = true;
    }

    public void asAModule(@Nullable Module mi)
    {
        module = mi;
    }

    /** Cached AST (Abstract Syntax Tree) root of the expression. */
    @Nullable
    private LazyValue ast = null;

    @Nullable
    private ExpressionNode root = null;

    /** Script-specific operators and built-in functions. */
    private final Map<String, ILazyOperator> operators = new Object2ObjectOpenHashMap<>();

    public boolean isAnOperator(String opname)
    {
        return operators.containsKey(opname) || operators.containsKey(opname + "u");
    }

    /**
     * The built-in functions, in a flat map: it's *visibility* that goes through modules, not
     * calls.
     *
     * <p>The map annotates names that get registered twice. That's not a bookkeeping detail: two
     * different implementations under the same name would mean the last one registered wins, and
     * the first silently disappears until someone calls the wrong function. With the annotation,
     * a test catches it.
     */
    private final Map<String, ILazyFunction> functions = new Object2ObjectOpenHashMap<>()
    {
        @Override
        public ILazyFunction put(String name, ILazyFunction function)
        {
            ILazyFunction previous = super.put(name, function);
            if (previous != null)
            {
                redefinedBuiltins.add(name);
            }
            return previous;
        }
    };

    /** The names of built-ins registered more than once on this expression. */
    private final java.util.Set<String> redefinedBuiltins = new java.util.TreeSet<>();

    /**
     * The names of built-in functions that have been registered more than once, i.e. those where
     * one registration silently replaced the previous one.
     */
    public java.util.Set<String> redefinedBuiltins()
    {
        return java.util.Collections.unmodifiableSet(redefinedBuiltins);
    }

    public Set<String> getFunctionNames()
    {
        return functions.keySet();
    }

    private final Map<String, String> functionalEquivalence = new Object2ObjectOpenHashMap<>();
    private final Map<String, String> functionalAliases = new Object2ObjectOpenHashMap<>();

    private void addFunctionalEquivalence(String operator, String function)
    {
        assert operators.containsKey(operator);
        assert functions.containsKey(function);
        functionalEquivalence.put(operator, function);
        functionalAliases.put(operator, function);
    }
    private void addFunctionalAlias(String operator, String function)
    {
        assert operators.containsKey(operator);
        assert functions.containsKey(function);
        functionalAliases.put(operator, function);
    }

    private final Map<String, Value> constants = Map.of(
            "euler", Arithmetic.euler,
            "pi", Arithmetic.PI,
            "null", Value.NULL,
            "true", Value.TRUE,
            "false", Value.FALSE
    );

    protected Value getConstantFor(String surface)
    {
        return constants.get(surface);
    }

    public List<String> getExpressionSnippet(Token token)
    {
        String code = this.getCodeString();
        List<String> output = new ArrayList<>(getExpressionSnippetLeftContext(token, code, 1));
        List<String> context = getExpressionSnippetContext(token, code);
        output.add(context.get(0) + " HERE>> " + context.get(1));
        output.addAll(getExpressionSnippetRightContext(token, code, 1));
        return output;
    }

    private static List<String> getExpressionSnippetLeftContext(Token token, String expr, int contextsize)
    {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length == 1)
        {
            return output;
        }
        for (int lno = token.lineno - 1; lno >= 0 && output.size() < contextsize; lno--)
        {
            output.add(lines[lno]);
        }
        Collections.reverse(output);
        return output;
    }

    private static List<String> getExpressionSnippetContext(Token token, String expr)
    {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length > 1)
        {
            output.add(lines[token.lineno].substring(0, token.linepos));
            output.add(lines[token.lineno].substring(token.linepos));
        }
        else
        {
            output.add(expr.substring(Math.max(0, token.pos - 40), token.pos));
            output.add(expr.substring(token.pos, Math.min(token.pos + 1 + 40, expr.length())));
        }
        return output;
    }

    private static List<String> getExpressionSnippetRightContext(Token token, String expr, int contextsize)
    {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length == 1)
        {
            return output;
        }
        for (int lno = token.lineno + 1; lno < lines.length && output.size() < contextsize; lno++)
        {
            output.add(lines[lno]);
        }
        return output;
    }


    public void addLazyUnaryOperator(String surface, String function, int precedence, boolean leftAssoc, boolean pure, Function<Context.Type, Context.Type> staticTyper,
                                     TriFunction<Context, Context.Type, LazyValue, LazyValue> lazyfun)
    {
        operators.put(surface + "u", new AbstractLazyOperator(precedence, leftAssoc)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return staticTyper.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, LazyValue v, LazyValue v2)
            {
                try
                {
                    return lazyfun.apply(c, t, v);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        functions.put(function, new AbstractLazyFunction(1, function)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return staticTyper.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, List<LazyValue> v)
            {
                try
                {
                    return lazyfun.apply(c, t, v.get(0));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });
        addFunctionalAlias(surface + "u", function);
    }


    public void addLazyBinaryOperatorWithDelegation(String surface, String function, int precedence, boolean leftAssoc, boolean pure,
                                                    SexFunction<Context, Context.Type, Expression, Token, LazyValue, LazyValue, LazyValue> lazyfun)
    {
        operators.put(surface, new AbstractLazyOperator(precedence, leftAssoc)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type type, Expression e, Token t, LazyValue v1, LazyValue v2)
            {
                try
                {
                    return lazyfun.apply(c, type, e, t, v1, v2);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });

        functions.put(function, new AbstractLazyFunction(2, function)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type type, Expression e, Token t, List<LazyValue> v)
            {
                try
                {
                    return lazyfun.apply(c, type, e, t, v.get(0), v.get(1));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });

        addFunctionalAlias(surface, function);
    }

    public void addCustomFunction(String name, ILazyFunction fun)
    {
        functions.put(name, fun);
    }

    public void addLazyFunctionWithDelegation(String name, int numpar, boolean pure, boolean transitive,
                                              QuinnFunction<Context, Context.Type, Expression, Token, List<LazyValue>, LazyValue> lazyfun)
    {
        functions.put(name, new AbstractLazyFunction(numpar, name)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return transitive;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type type, Expression e, Token t, List<LazyValue> lv)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return lazyfun.apply(c, type, e, t, lv);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public void addFunctionWithDelegation(String name, int numpar, boolean pure, boolean transitive,
                                          QuinnFunction<Context, Context.Type, Expression, Token, List<Value>, Value> fun)
    {
        functions.put(name, new AbstractLazyFunction(numpar, name)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return transitive;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type type, Expression e, Token t, List<LazyValue> lv)
            {
                try
                {
                    return fun.apply(c, type, e, t, unpackArgs(lv, c, Context.NONE));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public void addLazyBinaryOperator(String surface, String function, int precedence, boolean leftAssoc, boolean pure, Function<Context.Type, Context.Type> typer,
                                      QuadFunction<Context, Context.Type, LazyValue, LazyValue, LazyValue> lazyfun, TriFunction<Context, Context.Type, List<LazyValue>, LazyValue> multiFun)
    {
        operators.put(surface, new AbstractLazyOperator(precedence, leftAssoc)
        {

            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return typer.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, LazyValue v1, LazyValue v2)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return lazyfun.apply(c, t, v1, v2);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        functions.put(function, new AbstractLazyFunction(-1, function)
        {
            @Override
            public boolean pure()
            {
                return true;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return typer.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type i, Expression e, Token t, List<LazyValue> lazyParams)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return multiFun.apply(c, i, lazyParams);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });

        addFunctionalEquivalence(surface, function);
    }

    public void addLazyBinaryOperator(String surface, String function, int precedence, boolean leftAssoc, boolean pure, Function<Context.Type, Context.Type> typer,
                                      QuadFunction<Context, Context.Type, LazyValue, LazyValue, LazyValue> lazyfun)
    {
        operators.put(surface, new AbstractLazyOperator(precedence, leftAssoc)
        {

            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return typer.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, LazyValue v1, LazyValue v2)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return lazyfun.apply(c, t, v1, v2);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        functions.put(function, new AbstractLazyFunction(2, function)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return typer.apply(outerType);
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, List<LazyValue> v)
            {
                try
                {
                    return lazyfun.apply(c, t, v.get(0), v.get(1));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        addFunctionalAlias(surface, function);
    }

    public void addBinaryContextOperator(String surface, String function, int precedence, boolean leftAssoc, boolean pure, boolean transitive,
                                         QuadFunction<Context, Context.Type, Value, Value, Value> fun)
    {
        operators.put(surface, new AbstractLazyOperator(precedence, leftAssoc)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return transitive;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, LazyValue v1, LazyValue v2)
            {
                try
                {
                    // Operands inherit the context only when a signature is being built: there an
                    // unknown identifier is the name of a parameter (or a type), not a variable
                    // to look up
                    Context.Type operands = t == Context.LOCALIZATION ? Context.LOCALIZATION : Context.NONE;
                    return fun.apply(c, t, v1.evalValue(c, operands), v2.evalValue(c, operands));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        functions.put(function, new AbstractLazyFunction(2, function)
        {
            @Override
            public boolean pure()
            {
                return pure;
            }

            @Override
            public boolean transitive()
            {
                return transitive;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression e, Token token, List<LazyValue> v)
            {
                try
                {
                    Context.Type operands = t == Context.LOCALIZATION ? Context.LOCALIZATION : Context.NONE;
                    return fun.apply(c, t, v.get(0).evalValue(c, operands), v.get(1).evalValue(c, operands));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, token);
                }
            }
        });

        addFunctionalAlias(surface, function);
    }

    public static RuntimeException handleCodeException(Context c, RuntimeException exc, Expression e, Token token)
    {
        if (exc instanceof ExitStatement)
        {
            return exc;
        }
        if (exc instanceof IntegrityException)
        {
            return exc;
        }
        if (exc instanceof final BlossomError typed)
        {
            // A typed error already knows where it is and what to suggest: wrapping it in an
            // ExpressionException would lose the category and suggestion, which are the whole point of §4
            return typed.pushFrame(e.module == null ? "<code>" : e.module.name(),
                    new SourcePosition(e.getModuleName(), token.lineno + 1, token.linepos + 1));
        }
        if (exc instanceof final InternalExpressionException iee)
        {
            return iee.promote(c, e, token);
        }
        if (exc instanceof ArithmeticException)
        {
            return new ExpressionException(c, e, token, "Your math is wrong, " + exc.getMessage());
        }
        if (exc instanceof ResolvedException)
        {
            return exc;
        }
        // unexpected really - should be caught earlier and converted to InternalExpressionException
        Blossom.LOG.error("Unexpected exception while running blossom code", exc);
        return new ExpressionException(c, e, token, "Internal error (please report this issue to Blossom) while evaluating: " + exc);
    }

    public void addUnaryOperator(String surface, String function, boolean leftAssoc, Function<Value, Value> fun)
    {
        operators.put(surface + "u", new AbstractUnaryOperator(Operators.precedence.get("unary+-!..."), leftAssoc)
        {
            @Override
            public Value evalUnary(Value v1)
            {
                return fun.apply(v1);
            }
        });
        functions.put(function, new AbstractFunction(1, function)
        {
            @Override
            public Value eval(List<Value> v1)
            {
                return fun.apply(v1.get(0));
            }
        });
        addFunctionalAlias(surface + "u", function);
    }

    public void addBinaryOperator(String surface, String function, int precedence, boolean leftAssoc, BiFunction<Value, Value, Value> fun, Function<List<Value>, Value> multiFun)
    {
        operators.put(surface, new AbstractOperator(precedence, leftAssoc)
        {
            @Override
            public Value eval(Value v1, Value v2) {
                return fun.apply(v1, v2);
            }
        });

        functions.put(function, new AbstractFunction(-1, function)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return multiFun.apply(parameters);
            }
        });
        addFunctionalEquivalence(surface, function);
    }

    public void addBinaryOperator(String surface, String name, int precedence, boolean leftAssoc, BiFunction<Value, Value, Value> fun)
    {
        operators.put(surface, new AbstractOperator(precedence, leftAssoc)
        {
            @Override
            public Value eval(Value v1, Value v2) {
                return fun.apply(v1, v2);
            }
        });
        functions.put(name, new AbstractFunction(2, name)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters.get(0), parameters.get(1));
            }
        });

        addFunctionalAlias(surface, name);
    }


    public void addUnaryFunction(String name, Function<Value, Value> fun)
    {
        functions.put(name, new AbstractFunction(1, name)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters.get(0));
            }
        });
    }

    public void addImpureUnaryFunction(String name, Function<Value, Value> fun)
    {
        functions.put(name, new AbstractFunction(1, name)
        {
            @Override
            public boolean pure()
            {
                return false;
            }

            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters.get(0));
            }
        });
    }

    public void addBinaryFunction(String name, BiFunction<Value, Value, Value> fun)
    {
        functions.put(name, new AbstractFunction(2, name)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters.get(0), parameters.get(1));
            }
        });
    }

    public void addFunction(String name, Function<List<Value>, Value> fun)
    {
        functions.put(name, new AbstractFunction(-1, name)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters);
            }
        });
    }

    public void addImpureFunction(String name, Function<List<Value>, Value> fun)
    {
        functions.put(name, new AbstractFunction(-1, name)
        {
            @Override
            public boolean pure()
            {
                return false;
            }

            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(parameters);
            }
        });
    }

    public void addMathematicalUnaryFunction(String name, DoubleUnaryOperator fun)
    {
        addUnaryFunction(name, (v) -> new NumericValue(fun.applyAsDouble(NumericValue.asNumber(v).getDouble())));
    }

    public void addMathematicalUnaryIntFunction(String name, DoubleToLongFunction fun)
    {
        addUnaryFunction(name, (v) -> new NumericValue(fun.applyAsLong(NumericValue.asNumber(v).getDouble())));
    }

    public void addMathematicalBinaryIntFunction(String name, LongBinaryOperator fun)
    {
        addBinaryFunction(name, (w, v) ->
                new NumericValue(fun.applyAsLong(NumericValue.asNumber(w).getLong(), NumericValue.asNumber(v).getLong())));
    }

    public void addMathematicalBinaryFunction(String name, DoubleBinaryOperator fun)
    {
        addBinaryFunction(name, (w, v) ->
                new NumericValue(fun.applyAsDouble(NumericValue.asNumber(w).getDouble(), NumericValue.asNumber(v).getDouble())));
    }


    public void addLazyFunction(String name, int numParams, TriFunction<Context, Context.Type, List<LazyValue>, LazyValue> fun)
    {
        functions.put(name, new AbstractLazyFunction(numParams, name)
        {
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

            @Override
            public LazyValue lazyEval(Context c, Context.Type i, Expression e, Token t, List<LazyValue> lazyParams)
            {
                ILazyFunction.checkInterrupts();
                if (numParams >= 0 && lazyParams.size() != numParams)
                {
                    String error = "Function '" + name + "' requires " + numParams + " arguments, got " + lazyParams.size() + ". ";
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "evaluation", error + (fun instanceof Callables.UsageProvider up ? up.getUsage() : ""));
                }

                try
                {
                    return fun.apply(c, i, lazyParams);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public void addLazyFunction(String name, TriFunction<Context, Context.Type, List<LazyValue>, LazyValue> fun)
    {
        functions.put(name, new AbstractLazyFunction(-1, name)
        {
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

            @Override
            public LazyValue lazyEval(Context c, Context.Type i, Expression e, Token t, List<LazyValue> lazyParams)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return fun.apply(c, i, lazyParams);
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public void addContextFunction(String name, int num_params, TriFunction<Context, Context.Type, List<Value>, Value> fun)
    {
        functions.put(name, new AbstractLazyFunction(num_params, name)
        {
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

            @Override
            public LazyValue lazyEval(Context c, Context.Type i, Expression e, Token t, List<LazyValue> lazyParams)
            {
                ILazyFunction.checkInterrupts();
                try
                {
                    return fun.apply(c, i, unpackArgs(lazyParams, c, Context.NONE));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public void addTypedContextFunction(String name, int num_params, Context.Type reqType, TriFunction<Context, Context.Type, List<Value>, Value> fun)
    {
        functions.put(name, new AbstractLazyFunction(num_params, name)
        {
            @Override
            public boolean pure()
            {
                return true;
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return reqType;
            }

            @Override
            public LazyValue lazyEval(Context c, Context.Type i, Expression e, Token t, List<LazyValue> lazyParams)
            {
                try
                {
                    return fun.apply(c, i, unpackArgs(lazyParams, c, reqType));
                }
                catch (RuntimeException exc)
                {
                    throw handleCodeException(c, exc, e, t);
                }
            }
        });
    }

    public FunctionValue createUserDefinedFunction(Context context, String name, Expression expr, Token token, List<String> arguments, String varArgs, List<String> outers, LazyValue code)
    {
        return createUserDefinedFunction(context, name, expr, token, arguments, null, varArgs, outers, code);
    }

    /**
     * @param argumentTypes the canonical declared type for each parameter, or {@code null} if none
     *                      has one (see {@code blossom.value.ValueTypes})
     */
    public FunctionValue createUserDefinedFunction(Context context, String name, Expression expr, Token token,
            List<String> arguments, @Nullable List<String> argumentTypes, String varArgs, List<String> outers, LazyValue code)
    {
        return createUserDefinedFunction(context, name, expr, token, arguments, argumentTypes, null, varArgs, outers, code);
    }

    /** @param defaults the default value for each parameter, or {@code null} if none has one */
    public FunctionValue createUserDefinedFunction(Context context, String name, Expression expr, Token token,
            List<String> arguments, @Nullable List<String> argumentTypes, @Nullable List<LazyValue> defaults,
            String varArgs, List<String> outers, LazyValue code)
    {
        // Only the prelude is reserved: it is written without an import, so a user function with
        // the same name would make the grammar ambiguous. The rest of the library is reached
        // through an alias ('list.map'), so 'fn map(...)' hides nothing and stays legal
        if (StdLib.isInPrelude(name))
        {
            throw new UndefinedSymbolError(BlossomError.positionOf(this, token),
                    "'" + name + "' is part of the language and cannot be the name of a function."
                            + " The reserved names are " + String.join(", ", new java.util.TreeSet<>(StdLib.PRELUDE)) + ".");
        }
        Map<String, LazyValue> contextValues = new HashMap<>();
        for (String outer : outers)
        {
            LazyValue lv = context.getVariable(outer);
            if (lv == null)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "evaluation", "Variable " + outer + " needs to be defined in outer scope to be used as outer parameter, and cannot be global");
            }
            else
            {
                contextValues.put(outer, lv);
            }
        }
        if (contextValues.isEmpty())
        {
            contextValues = null;
        }

        if (isAnonymousName(name))
        {
            // An anonymous lambda captures the frame it's born in: without capture it wouldn't see
            // anything around it, and a lambda that can't see its own context is useless. The
            // capture is the same as 'outer' applied to everything, and only whoever creates it
            // pays for it; a named function still captures nothing
            Map<String, LazyValue> captured = contextValues == null ? new HashMap<>() : contextValues;
            context.forEachVariable(captured::putIfAbsent);
            contextValues = captured.isEmpty() ? null : captured;
        }

        FunctionValue result = new FunctionValue(expr, token, name, code, arguments, argumentTypes, defaults, varArgs, contextValues);
        // do not store lambda definitions
        if (!name.equals("_") && !isAnonymousName(name))
        {
            context.host.addUserDefinedFunction(context, module, name, result);
        }
        return result;
    }

    /** The name prefix the parser gives to a function written without a name, {@code fn(a) -> ...}. */
    public static final String ANONYMOUS_PREFIX = "fn@";

    /**
     * The name of an anonymous lambda: it's not a valid identifier, so it can't collide with
     * anything anyone wrote, and it carries with it the point where the lambda was written, which
     * is what's needed when it shows up in an error stack.
     */
    public static String anonymousName(Token token)
    {
        return ANONYMOUS_PREFIX + token.lineno + ':' + token.linepos;
    }

    public static boolean isAnonymousName(String name)
    {
        return name.startsWith(ANONYMOUS_PREFIX);
    }

    public static final String GLOBAL_VARIABLE_PREFIX = "global_";

    /**
     * Whether {@code name} refers to a variable shared by the whole module - or the whole app, if it
     * opted into {@code 'global_scope' -> 'app'} - rather than to a variable of the current frame.
     */
    public static boolean isGlobalVariableName(String name)
    {
        return name.startsWith(GLOBAL_VARIABLE_PREFIX);
    }

    public void setAnyVariable(Context c, String name, LazyValue lv)
    {
        if (ScriptHost.isQualified(name))
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Cannot assign to '" + name + "': a module's state can only be changed from the module "
                            + "itself, by exporting a function that does it.");
        }
        ModuleSymbols symbols = c.host.symbolsOf(module);
        if (symbols.isBinding(name) && !symbols.isMutableBinding(name) && c.host.getGlobalVariable(module, name) != null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'" + name + "' is an immutable binding: declare it 'let mut " + name + "' if it has to change.");
        }
        if (isGlobalVariableName(name) || symbols.isBinding(name))
        {
            c.host.setGlobalVariable(module, name, lv);
        }
        else
        {
            c.setVariable(name, lv);
        }
    }

    public LazyValue getOrSetAnyVariable(Context c, String name)
    {
        if (Context.isImplicitName(name))
        {
            return c.getVariable(name);
        }
        if (ScriptHost.isQualified(name))
        {
            return qualifiedVariable(c, name);
        }
        if (isGlobalVariableName(name))
        {
            // creating the global is a single atomic step, so that one thread merely mentioning an
            // undefined global cannot overwrite the value another thread is assigning to it
            return c.host.getOrCreateGlobalVariable(module, name, () -> uninitializedVariable(name));
        }
        return localVariable(c, name);
    }

    private static LazyValue uninitializedVariable(String name)
    {
        // tolerates a null context: a global that was only mentioned holds this very placeholder,
        // and it is read without a context when the app's globals are inspected
        Value undefined = UndefValue.named(name);
        return (_c, _t) -> _c != null && _c.host.strict ? undefined : Value.NULL;
    }

    /**
     * The compiled node that reads variable {@code name}.
     *
     * <p>Which namespace a name belongs to is knowable just by looking at it, and the name never
     * changes after parsing: the choice is made once here, instead of being redone on every read
     * like {@link #getOrSetAnyVariable} does. For a local variable, every access saves a lookup
     * for the qualification dot and a prefix comparison; for {@code _}, {@code _i} and {@code _a}
     * it saves the entire map lookup, because they're fields of the context.
     */
    private LazyValue variableNode(String rawName)
    {
        // Interned once here: the frame compares names by identity before content, and names
        // coming from parsing are the same object on every evaluation
        String name = rawName.intern();
        if (Context.isImplicitName(name))
        {
            return switch (name)
            {
                case Context.IMPLICIT_VALUE -> read(name, (c, t) -> c.implicitValue().evalValue(c, t));
                case Context.IMPLICIT_INDEX -> read(name, (c, t) -> c.implicitIndex().evalValue(c, t));
                default -> read(name, (c, t) -> c.implicitAccumulator().evalValue(c, t));
            };
        }
        if (ScriptHost.isQualified(name))
        {
            return read(name, (c, t) -> qualifiedVariable(c, name).evalValue(c, t));
        }
        if (isGlobalVariableName(name))
        {
            return read(name, (c, t) -> c.host.getOrCreateGlobalVariable(module, name, () -> uninitializedVariable(name)).evalValue(c, t));
        }
        return read(name, (c, t) -> localVariable(c, name).evalValue(c, t));
    }

    /**
     * The node that reads {@code name} by evaluating {@code value}, and that knows how to state
     * its own name.
     *
     * <p>In the two contexts where an identifier is not a read (on the left side of an assignment,
     * and inside a signature) it doesn't produce the value but a {@link VariableReferenceValue}:
     * that's the l-value of variables, and it takes the place of the name every value used to
     * carry around (D-093). Inside a signature the value isn't even looked up, because there the
     * name belongs to a parameter that doesn't exist yet.
     */
    private static VariableRead read(String name, LazyValue value)
    {
        return new VariableRead()
        {
            @Override
            public String variableName()
            {
                return name;
            }

            @Override
            public Value evalValue(Context c, Context.Type t)
            {
                if (t == Context.LVALUE)
                {
                    return new VariableReferenceValue(name, value.evalValue(c, Context.NONE));
                }
                if (t == Context.LOCALIZATION)
                {
                    return new VariableReferenceValue(name);
                }
                return value.evalValue(c, t);
            }
        };
    }

    /** {@code alias.symbol}: it belongs to another module, and cannot be created from here. */
    private LazyValue qualifiedVariable(Context c, String name)
    {
        // either it exists and is public, or it is an error, and it is one right away
        LazyValue imported = c.host.getQualifiedGlobal(module, name);
        if (imported != null)
        {
            return imported;
        }
        // a public symbol that is not a binding is a function, and a function is a value: passing
        // it to another module has to work, and what travels is the FunctionValue rather than its
        // name, so it stays bound to the module that defined it wherever it gets called
        Value function = c.host.getQualifiedFunction(module, name);
        return (cc, tt) -> function;
    }

    /** A bare name: the current frame first, then the module globals, then its functions. */
    private LazyValue localVariable(Context c, String name)
    {
        LazyValue variable = c.getVariable(name);
        if (variable != null)
        {
            return variable;
        }
        variable = c.host.getGlobalVariable(module, name);
        if (variable != null)
        {
            return variable;
        }
        FunctionValue localFunction = c.host.getFunction(module, name);
        if (localFunction != null)
        {
            // the bare name of a module function evaluates to the function itself
            return (cc, tt) -> localFunction;
        }
        variable = uninitializedVariable(name);
        c.setVariable(name, variable);
        return variable;
    }

    /**
     * Reads a variable, replaces it with {@code updater}'s result and returns the new value.
     *
     * <p>A global variable is updated while holding the app's globals guard, so two threads running
     * {@code global_x += 1} cannot both read the same value and store the same result. That is the
     * same guard the value itself carries, so appending to a global list takes it only once.
     * Locals live in the calling frame, which no other thread can reach, so they need no lock.
     *
     * <p>{@code updater} runs inside that guard, therefore it must only combine the values it is
     * given and must not evaluate arbitrary app code.
     */
    public LazyValue updateAnyVariable(Context c, String name, UnaryOperator<Value> updater)
    {
        return isGlobalVariableName(name)
                ? c.host.withGlobalsLocked(() -> applyVariableUpdate(c, name, updater))
                : applyVariableUpdate(c, name, updater);
    }

    private LazyValue applyVariableUpdate(Context c, String name, UnaryOperator<Value> updater)
    {
        Value current = getOrSetAnyVariable(c, name).evalValue(c, Context.NONE);
        Value updated = updater.apply(current);
        LazyValue result = (cc, tt) -> updated;
        setAnyVariable(c, name, result);
        return result;
    }

    public static final Expression none = new Expression("null");

    /**
     * @param expression .
     */
    public Expression(String expression)
    {
        this.expression = stripExpression(expression);
        Operators.apply(this);
        ControlFlow.apply(this);
        Functions.apply(this);
        Arithmetic.apply(this);
        Sys.apply(this);
        Threading.apply(this);
        Loops.apply(this);
        DataStructures.apply(this);
        Io.apply(this);
        Http.apply(this);
        Modules.apply(this);
        for(String op : operators.keySet()) {
            assert functionalAliases.containsKey(op) : "Missing function for operator " + op;
        }
    }

    private String stripExpression(String expression)
    {
        return expression.stripTrailing().replaceAll("\\r\\n?", "\n").replaceAll("\\t", "   ");
    }


    private List<Token> shuntingYard(Context c, List<Token> tokens)
    {
        List<Token> outputQueue = new ArrayList<>();
        Stack<Token> stack = new ObjectArrayList<>();
        Token lastFunction = null;
        Token previousToken = null;
        for (Token token : tokens)
        {
            switch (token.type)
            {
                case STRINGPARAM:
                    //stack.push(token); // changed that so strings are treated like literals
                    //break;
                case LITERAL, HEX_LITERAL:
                    if (previousToken != null && (
                            previousToken.type == Token.TokenType.LITERAL ||
                                    previousToken.type == Token.TokenType.HEX_LITERAL ||
                                    previousToken.type == Token.TokenType.STRINGPARAM))
                    {
                        throw ParseError.at(this, token, "Missing operator");
                    }
                    outputQueue.add(token);
                    break;
                case VARIABLE:
                    outputQueue.add(token);
                    break;
                case FUNCTION:
                    stack.push(token);
                    lastFunction = token;
                    break;
                case COMMA:
                    if (previousToken != null && previousToken.type == Token.TokenType.OPERATOR)
                    {
                        throw ParseError.at(this, previousToken, "Missing parameter(s) for operator ");
                    }
                    while (!stack.isEmpty() && stack.top().type != Token.TokenType.OPEN_PAREN)
                    {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty())
                    {
                        if (lastFunction == null)
                        {
                            throw ParseError.at(this, token, "Unexpected comma");
                        }
                        else
                        {
                            throw new ExpressionException(c, this, lastFunction, "Parse error for function");
                        }
                    }
                    break;
                case OPERATOR:
                {
                    if (previousToken != null
                            && (previousToken.type == Token.TokenType.COMMA || previousToken.type == Token.TokenType.OPEN_PAREN))
                    {
                        throw ParseError.at(this, token, "Missing parameter(s) for operator '" + token + "'");
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null)
                    {
                        throw ParseError.at(this, token, "Unknown operator '" + token + "'");
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case UNARY_OPERATOR:
                {
                    if (previousToken != null && previousToken.type != Token.TokenType.OPERATOR
                            && previousToken.type != Token.TokenType.UNARY_OPERATOR
                            && previousToken.type != Token.TokenType.COMMA && previousToken.type != Token.TokenType.OPEN_PAREN)
                    {
                        throw ParseError.at(this, token, "Invalid position for unary operator " + token);
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null)
                    {
                        throw ParseError.at(this, token, "Unknown unary operator '" + token.surface.substring(0, token.surface.length() - 1) + "'");
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case OPEN_PAREN:
                    // removed implicit multiplication in this missing code block
                    if (previousToken != null && previousToken.type == Token.TokenType.FUNCTION)
                    {
                        outputQueue.add(token);
                    }
                    else if (previousToken != null && previousToken.type == Token.TokenType.CLOSE_PAREN)
                    {
                        // a call written after a value: 'f()(2)', '(fn(x) -> x)(2)'. The value
                        // before it is the function, and becomes the first argument of 'call';
                        // the parentheses that follow carry the others
                        Token apply = token.morphedInto(Token.TokenType.FUNCTION, "call");
                        apply.postfixCall = true;
                        outputQueue.add(token);
                        stack.push(apply);
                    }
                    stack.push(token);
                    break;
                case CLOSE_PAREN:
                    if (previousToken != null && previousToken.type == Token.TokenType.OPERATOR)
                    {
                        throw ParseError.at(this, previousToken, "Missing parameter(s) for operator " + previousToken);
                    }
                    while (!stack.isEmpty() && stack.top().type != Token.TokenType.OPEN_PAREN)
                    {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty())
                    {
                        throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Mismatched parentheses");
                    }
                    stack.pop();
                    if (!stack.isEmpty() && stack.top().type == Token.TokenType.FUNCTION)
                    {
                        outputQueue.add(stack.pop());
                    }
                    break;
                case MARKER:
                    if ("$".equals(token.surface))
                    {
                        StringBuilder sb = new StringBuilder(expression);
                        sb.setCharAt(token.pos, '\n');
                        expression = sb.toString();
                    }
                    break;
            }
            if (token.type != Token.TokenType.MARKER)
            {
                previousToken = token;
            }
        }

        while (!stack.isEmpty())
        {
            Token element = stack.pop();
            if (element.type == Token.TokenType.OPEN_PAREN || element.type == Token.TokenType.CLOSE_PAREN)
            {
                throw new ExpressionException(c, this, element, "Mismatched parentheses");
            }
            outputQueue.add(element);
        }
        return outputQueue;
    }

    private void shuntOperators(List<Token> outputQueue, Stack<Token> stack, ILazyOperator o1)
    {
        Token nextToken = stack.isEmpty() ? null : stack.top();
        while (nextToken != null
                && (nextToken.type == Token.TokenType.OPERATOR
                || nextToken.type == Token.TokenType.UNARY_OPERATOR)
                && ((o1.isLeftAssoc() && o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
                || (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence())))
        {
            outputQueue.add(stack.pop());
            nextToken = stack.isEmpty() ? null : stack.top();
        }
    }

    public enum LoadOverride {
        DEFAULT("clean"), CANONICAL("canonical"), OPTIMIZED("optimized"), FUNCTIONAL("functional"), FUNCTIONAL_OPTIMIZED("functional_optimized");
        public String equivalent;
        LoadOverride(String equivalent) {
            this.equivalent = equivalent;
        }
    }

    public Pair<Value, ExpressionNode> executeAndEvaluate(Context c, boolean optimize, LoadOverride override, @Nullable Consumer<String> logger)
    {
        if (ast == null)
        {
            boolean functions = false;
            if (override != LoadOverride.DEFAULT) {
                optimize = override == LoadOverride.OPTIMIZED || override == LoadOverride.FUNCTIONAL_OPTIMIZED;
                functions = override == LoadOverride.FUNCTIONAL || override == LoadOverride.FUNCTIONAL_OPTIMIZED;
            }

            Pair<ExpressionNode, LazyValue> ret = getAST(c, optimize, functions, logger);
            ast = ret.getRight();
            root = ret.getLeft();
        }
        return Pair.of(evaluatePartial(() -> ast, c, Context.Type.NONE), root);
    }

    /** The position to use when the error is about the whole module and not one precise token. */
    private SourcePosition modulePosition()
    {
        return new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0);
    }

    public Value evaluatePartial(Supplier<LazyValue> exprProvider, Context c, Context.Type expectedType)
    {
        try
        {
            return exprProvider.get().evalValue(c, expectedType);
        }
        catch (ContinueStatement | BreakStatement | ReturnStatement exc)
        {
            throw new BlossomRuntimeError(modulePosition(), "control_flow",
                    "continue, break and return only make sense inside a loop or a function.");
        }
        catch (ExitStatement exit)
        {
            return exit.retval == null ? Value.NULL : exit.retval;
        }
        catch (StackOverflowError ignored)
        {
            throw new BlossomRuntimeError(modulePosition(), "stack_overflow",
                    "Recursion went too deep.");
        }
        catch (final BlossomError typed)
        {
            // already typed, with position and stack: replacing it with a generic error would
            // erase exactly the information that is needed
            throw typed;
        }
        catch (InternalExpressionException exc)
        {
            throw new BlossomRuntimeError(modulePosition(), "evaluation", exc.getMessage());
        }
        catch (ArithmeticException exc)
        {
            throw new BlossomRuntimeError(modulePosition(), "arithmetic", exc.getMessage());
        }
    }

    public static class ExpressionNode
    {
        public LazyValue op;
        public List<ExpressionNode> args;
        public Token token;
        public List<Token> range;
        /**
         * The Value representation of the left parenthesis, used for parsing
         * varying numbers of function parameters.
         */
        public static final ExpressionNode PARAMS_START = new ExpressionNode(null, null, Token.NONE);

        public ExpressionNode(LazyValue op, List<ExpressionNode> args, Token token)
        {
            this.op = op;
            this.args = args;
            this.token = token;
            range = new ArrayList<>();
            range.add(token);
        }

        public static ExpressionNode ofConstant(Value val, Token token)
        {
            return new ExpressionNode(new LazyValue.Constant(val), Collections.emptyList(), token);
        }

        public List<Token> tokensRecursive(Expression expression, List<Token> reference, Map<Pair<Integer, Integer>, List<Integer>> referencePointers){
            List<Token> tokens = new ArrayList<>();
            switch (token.type) {
                case FUNCTION -> {
                    tokens.add(token);
                    tokens.add(findToken(token, Token.TokenType.OPEN_PAREN, "(", reference, referencePointers));
                    if (!args.isEmpty()) {
                        for (ExpressionNode arg : args) {
                            List<Token> argTokens = arg.tokensRecursive(expression, reference, referencePointers);
                            tokens.addAll(argTokens);
                            tokens.add(findToken(argTokens.getLast(), Token.TokenType.COMMA, ",", reference, referencePointers).disguiseAs(", ", null));
                        }
                        tokens.removeLast();
                    }
                    tokens.add(findToken(tokens.getLast(), Token.TokenType.CLOSE_PAREN, ")", reference, referencePointers));
                }
                case OPERATOR -> {
                    tokens.addAll(createOperatorArgumentTokens(expression, args.get(0), reference, referencePointers));
                    tokens.add(token.disguiseAs(token.surface.equals(";") ? (token.surface+" ") : (" "+token.surface+" "), null));
                    tokens.addAll(createOperatorArgumentTokens(expression, args.get(1), reference, referencePointers));
                }
                case UNARY_OPERATOR -> {
                    tokens.add(token.disguiseAs(" "+StringUtils.chop(token.surface),null));
                    tokens.addAll(createOperatorArgumentTokens(expression, args.get(0), reference, referencePointers));
                }
                case VARIABLE, LITERAL, HEX_LITERAL, CONSTANT -> tokens.add(token);
                case STRINGPARAM -> tokens.add(token.disguiseAs("'"+token.surface+"'", null));
                // these should be parsed out already, but ....
                //case LITERAL, STRINGPARAM, HEX_LITERAL, OPEN_PAREN, CLOSE_PAREN, MARKER -> tokens.add(token.morphedInto(token.type, token.surface+"?"));
                // CONSTANT ??
                default -> tokens.add(token.disguiseAs("?"+token.surface+"?", null));
            }
            return tokens;
        }

        public Token findToken(Token previous, Token.TokenType type, String expectedSurface, List<Token> reference, Map<Pair<Integer, Integer>, List<Integer>> referencePointers)
        {
            List<Integer> indices = referencePointers.get(Pair.of(previous.lineno, previous.linepos));
            if (indices == null)
            {
                return previous.morphedInto(type, expectedSurface);
            }
            for (int index : indices)
            {
                if (index + 1 == reference.size())
                {
                    continue;
                }
                Token token = reference.get(index + 1);
                if (token.type == type)
                {
                    return token.morphedInto(type, expectedSurface);
                }
            }
            return previous.morphedInto(type, expectedSurface);
        }

        public List<Token> createOperatorArgumentTokens(Expression expression, ExpressionNode operand, List<Token> reference, Map<Pair<Integer, Integer>, List<Integer>> referencePointers) {
            boolean needsBrackets = false;
            var operators = expression.operators;
            if (operators.containsKey(operand.token.surface) && operators.containsKey(token.surface)) {
                if (operators.get(operand.token.surface).getPrecedence() < operators.get(token.surface).getPrecedence()) {
                    needsBrackets = true;
                }
            }
            List<Token> argumentTokens = operand.tokensRecursive(expression, reference, referencePointers);
            if (!needsBrackets) {
                return argumentTokens;
            }
            List<Token> bracketed = new ArrayList<>();
            bracketed.add(findToken(argumentTokens.getFirst(), Token.TokenType.OPEN_PAREN, "(", reference, referencePointers));
            bracketed.get(0).swapPlace(argumentTokens.getFirst());
            bracketed.addAll(argumentTokens);
            bracketed.add(findToken(argumentTokens.getLast(), Token.TokenType.CLOSE_PAREN, ")", reference, referencePointers));
            return bracketed;
        }
    }


    /**
     * The name a call has to be looked up under among the built-ins, or {@code null} if it must
     * not be looked up among them at all.
     *
     * <p>Resolved once per call written in the source:
     * <ul>
     *   <li>{@code math.sqrt(4)} becomes {@code sqrt}, if the alias {@code math} points to a
     *       library module that exports that symbol;</li>
     *   <li>an unqualified name outside the prelude is <b>always</b> a user function, resolved at
     *       run time on its host. Built-ins are reachable only through an alias, so the implicit
     *       global namespace does not come back, and in exchange {@code fn sum(a, b)} is an
     *       ordinary name instead of one of the 160 reserved by the flat list of
     *       implementations.</li>
     * </ul>
     */
    private @Nullable String resolveBuiltinName(Context context, Token token)
    {
        String name = token.surface;
        if (ScriptHost.isQualified(name))
        {
            int separator = name.lastIndexOf(ScriptHost.QUALIFIER);
            String alias = name.substring(0, separator);
            String symbol = name.substring(separator + 1);
            String target = context.host.symbolsOf(module).moduleOfAlias(alias);
            if (target != null && StdLib.isBuiltinModule(target))
            {
                if (!StdLib.exports(target, symbol))
                {
                    throw new UndefinedSymbolError(BlossomError.positionOf(this, token),
                            "Module '" + target + "' does not export '" + symbol + "'.",
                            Suggestions.closestTo(symbol, StdLib.symbolsOf(target)));
                }
                return StdLib.implementationOf(target, symbol);
            }
            return null; // symbol of a user module: resolved at run time, on its own host
        }
        return StdLib.isInPrelude(name) ? name : null;
    }

    /**
     * Whether this node is a lambda written in place, {@code fn(x) -> ...}.
     *
     * <p>Only the tokens are inspected, so the answer is known while the tree is being built: that
     * is how loops tell a function from an expression using {@code _} without evaluating the body.
     * Two operator names are accepted because tree compaction rewrites {@code ->} into its
     * functional equivalent {@code define}.
     */
    private static boolean isLambdaNode(Token operator, Token left)
    {
        return (operator.surface.equals("->") || operator.surface.equals("define"))
                && left.type == Token.TokenType.FUNCTION && left.surface.equals("fn");
    }

    /**
     * {@code a |> f(b)}, which means {@code f(a, b)}.
     *
     * <p>The rewrite happens here, while the tree is being built: the pipe node disappears and what
     * is left is the call, with one extra argument in front. At run time there is no operator to
     * evaluate, so chaining three transformations costs the same as nesting them; the only gain is
     * reading order, which is the reason the operator exists.
     *
     * <p>If the right side is a value rather than a call, as in {@code x |> f} with {@code f} bound
     * to a function, it becomes {@code call(f, x)}, the same thing written another way.
     */
    private ExpressionNode pipeNode(ExpressionNode left, ExpressionNode right)
    {
        Token target = right.token;
        if (target.type == Token.TokenType.FUNCTION)
        {
            List<ExpressionNode> args = new ArrayList<>(right.args);
            // for a name that is not a built-in the parser already put the name as the first
            // argument of 'call': the subject goes after it, not before
            args.add(target.surface.equals("call") ? 1 : 0, left);
            return functionNode(target, args);
        }
        Token call = target.morphedInto(Token.TokenType.FUNCTION, "call");
        return functionNode(call, List.of(right, left));
    }

    private ExpressionNode functionNode(Token token, List<ExpressionNode> args)
    {
        ILazyFunction f = functions.get(token.surface);
        List<LazyValue> params = args.stream().map(n -> n.op).collect(Collectors.toList());
        ExpressionNode node = new ExpressionNode(
                (c, t) -> f.lazyEval(c, t, this, token, params).evalValue(c, t), args, token);
        token.node = node;
        return node;
    }

    /**
     * Whether this node, among the arguments of a call, is a name attached to a value.
     *
     * <p>{@code f(pos, update = false)}: an {@code =} with a plain name on the left. Only the
     * tokens are inspected, so the answer is known while the tree is being built. Two operator
     * names are accepted because compaction rewrites {@code =} into its functional equivalent.
     */
    private static boolean isNamedArgument(Token callee, ExpressionNode node)
    {
        // only inside a call to a user written function, or inside a signature. Operators, which
        // compaction also turns into calls ('a; b' becomes then(a, b), '[x]' becomes l(x)), have no
        // named parameters, and there an '=' stays the assignment it has always been
        if (!callee.surface.equals("call") && !callee.surface.equals("fn"))
        {
            return false;
        }
        return (node.token.surface.equals("=") || node.token.surface.equals("assign"))
                && node.args != null && node.args.size() == 2
                && node.args.get(0).token.type == Token.TokenType.VARIABLE
                && node.args.get(0).args.isEmpty();
    }

    /**
     * The name detached from its value.
     *
     * <p>The value stays lazy: in a function signature this becomes the default of a parameter, and
     * a default has to be evaluated on every call that uses it, not once at definition time,
     * otherwise {@code fn f(x = {})} would hand everyone the same map.
     */
    private static LazyValue namedArgument(String name, LazyValue value)
    {
        Value named = new blossom.value.NamedArgumentValue(name, value);
        return (c, t) -> named;
    }

    /**
     * Named arguments must come last, and whoever puts them elsewhere finds out immediately.
     *
     * <p>The check happens here, on the tokens, so it is a load time error and not a run time
     * surprise. It is also what lets the call boundary look at the last argument alone to know
     * whether any named ones are present.
     */
    private void checkNamedArgumentOrder(Context context, Token callee, List<ExpressionNode> args)
    {
        boolean seenNamed = false;
        for (ExpressionNode arg : args)
        {
            if (isNamedArgument(callee, arg))
            {
                seenNamed = true;
            }
            else if (seenNamed)
            {
                throw new ExpressionException(context, this, arg.token,
                        "An argument without a name comes after one with a name: the named ones go last.");
            }
        }
    }

    private static String argumentName(ExpressionNode node)
    {
        return node.args.get(0).token.surface;
    }

    private ExpressionNode binaryNode(Token token, ExpressionNode left, ExpressionNode right)
    {
        LazyValue result = (c, t) -> operators.get(token.surface).lazyEval(c, t, this, token, left.op, right.op).evalValue(c, t);
        // a lambda is recognised from the tokens, here, once: loops accept both a function and an
        // expression using '_', and have to tell them apart without evaluating the body
        if (isLambdaNode(token, left.token))
        {
            result = new LazyValue.Lambda(result);
        }
        ExpressionNode node = new ExpressionNode(result, List.of(left, right), token);
        token.node = node;
        return node;
    }

    /**
     * Puts {@code f = fn(a) -> a + 1} back together, which precedence splits at the wrong point.
     *
     * <p>{@code ->} binds looser than assignment, and it has to: the body of a function is often an
     * assignment, and {@code fn count() -> counter += 1} must read with the assignment inside the
     * body. The price is that storing a lambda in a variable parses the other way round,
     * {@code (f = fn(a)) -> a + 1}, where the left of the arrow carries no signature.
     *
     * <p>The two forms are told apart by looking at the tokens, that is without running anything:
     * left of the arrow there is an assignment whose right side is an {@code fn(...)}. In that case
     * only, the tree is flipped into {@code f = (fn(a) -> a + 1)}. No precedence to change and no
     * run time cost: it happens once, while the tree is being built.
     */
    /**
     * The prelude names that are not a user mistake on the left of {@code ->}: {@code fn} is the
     * lambda, {@code call} is what an unknown call gets rewritten into, that is every function the
     * user is defining, and {@code l}/{@code m}/{@code concat} are inserted by the tokenizer for
     * brackets, braces and interpolated strings.
     */
    private static final Set<String> RESERVED_ON_THE_LEFT_OF_ARROW = Set.of("fn", "call", "l", "m", "concat");

    private ExpressionNode reassociateLambdaAssignment(Token token, ExpressionNode left, ExpressionNode right)
    {
        // 'fn print(a) -> ...': a prelude name on the left of '->' has already become a call to the
        // built-in, so the signature never forms and the error would talk about '->' instead of
        // about the name. It is the name that is the problem, and it is known right here
        if (token.surface.equals("->")
                && left.token.type == Token.TokenType.FUNCTION
                && !RESERVED_ON_THE_LEFT_OF_ARROW.contains(left.token.surface)
                && StdLib.isInPrelude(left.token.surface))
        {
            throw new ParseError(BlossomError.positionOf(this, left.token),
                    "'" + left.token.surface + "' is part of the language and cannot be the name of a function."
                            + " The reserved names are " + String.join(", ", new java.util.TreeSet<>(StdLib.PRELUDE)) + ".");
        }
        // 'print = 1': a language name cannot become a variable. 'fn print(a) -> a' said so
        // already; this is the same rule from the other side, because otherwise the same name would
        // mean two things, the variable when read and the built-in when called, and neither would
        // be what the reader expects
        if (operators.get(token.surface) != null
                && Operators.precedence.get("assign=<>").equals(operators.get(token.surface).getPrecedence())
                && left.token.type == Token.TokenType.VARIABLE
                && left.args.isEmpty()
                && StdLib.isReservedAsAName(left.token.surface))
        {
            throw new ParseError(BlossomError.positionOf(this, left.token),
                    "'" + left.token.surface + "' is part of the language and cannot be the name of a variable.");
        }
        // 'f = fn(a) -> ...', 'x |> fn(a) -> ...', and both together: '->' binds looser than both
        // assignment and pipe, so the lambda ends up split in two and its signature hangs on the
        // right of whatever precedes it. It is put back together here
        if (token.surface.equals("->") && left.token.type == Token.TokenType.OPERATOR)
        {
            ExpressionNode spliced = spliceLambdaBody(token, left, right);
            if (spliced != null)
            {
                return spliced;
            }
        }
        return binaryNode(token, left, right);
    }

    /**
     * {@code <value>(a, b)}: a call written after something that is not a name.
     *
     * <p>Needed by {@code f()(2)} and {@code (fn(x) -> x)(2)}: the function is not a name the
     * parser can recognise, it is the value already sitting on the stack. It is pulled back out
     * from under the parameter list and put in front of them, the shape {@code call} expects.
     */
    private ExpressionNode postfixCallNode(Context context, Token token, Stack<ExpressionNode> nodeStack)
    {
        ILazyFunction call = functions.get("call");
        ArrayList<ExpressionNode> p = new ArrayList<>();
        while (!nodeStack.isEmpty() && nodeStack.top() != ExpressionNode.PARAMS_START)
        {
            p.add(nodeStack.pop());
        }
        if (nodeStack.isEmpty())
        {
            throw ParseError.at(this, token, "Mismatched parentheses");
        }
        nodeStack.pop();
        if (nodeStack.isEmpty() || nodeStack.top() == ExpressionNode.PARAMS_START)
        {
            throw ParseError.at(this, token, "There is nothing to call before '('.");
        }
        Collections.reverse(p);
        // the subject of the call goes first: 'call' wants the function as its first argument
        p.add(0, nodeStack.pop());
        checkNamedArgumentOrder(context, token, p);
        List<LazyValue> params = p.stream()
                .map(n -> isNamedArgument(token, n) ? namedArgument(argumentName(n), n.args.get(1).op) : n.op)
                .collect(Collectors.toList());
        ExpressionNode node = new ExpressionNode(
                (c, t) -> call.lazyEval(c, t, this, token, params).evalValue(c, t), p, token);
        token.node = node;
        return node;
    }

    /**
     * Reattaches the body to the signature of a lambda left at the end of an assignment or a pipe.
     *
     * <p>Walks down the right side until it finds {@code fn(...)}, rebuilding the nodes it crossed
     * on the way back, so that {@code x = 5 |> fn(v) -> v + 1} reads as expected instead of as
     * {@code (x = 5 |> fn(v)) -> v + 1}. Returns {@code null} when there is no signature at the
     * bottom, that is when there was nothing to put back together.
     */
    private @Nullable ExpressionNode spliceLambdaBody(Token arrow, ExpressionNode left, ExpressionNode body)
    {
        if (left.token.type == Token.TokenType.FUNCTION && left.token.surface.equals("fn"))
        {
            return binaryNode(arrow, left, body);
        }
        if (left.token.type != Token.TokenType.OPERATOR || left.args == null || left.args.size() != 2)
        {
            return null;
        }
        ILazyOperator operator = operators.get(left.token.surface);
        boolean assignment = operator != null
                && Operators.precedence.get("assign=<>").equals(operator.getPrecedence());
        boolean pipe = left.token.surface.equals("|>");
        if (!assignment && !pipe)
        {
            return null;
        }
        ExpressionNode rebuilt = spliceLambdaBody(arrow, left.args.get(1), body);
        if (rebuilt == null)
        {
            return null;
        }
        return assignment
                ? binaryNode(left.token, left.args.get(0), rebuilt)
                : pipeNode(left.args.get(0), rebuilt);
    }

    private ExpressionNode RPNToParseTree(List<Token> tokens, Context context)
    {
        Stack<ExpressionNode> nodeStack = new ObjectArrayList<>();
        for (Token token : tokens)
        {
            switch (token.type) {
                case UNARY_OPERATOR -> {
                    ExpressionNode node = nodeStack.pop();
                    LazyValue result = (c, t) -> operators.get(token.surface).lazyEval(c, t, this, token, node.op, null).evalValue(c, t);
                    ExpressionNode newNode = new ExpressionNode(result, Collections.singletonList(node), token);
                    token.node = newNode;
                    nodeStack.push(newNode);
                }
                case OPERATOR -> {
                    ExpressionNode v1 = nodeStack.pop();
                    ExpressionNode v2 = nodeStack.pop();
                    // 'x |> fn(a) -> ...': the pipe binds tighter than '->', so the right side
                    // here is still only the lambda signature. Rewriting now would slip the subject
                    // among the parameters; a '|>' node is left for the following '->' to handle
                    boolean pipeIntoLambdaSignature = token.surface.equals("|>")
                            && v1.token.type == Token.TokenType.FUNCTION && v1.token.surface.equals("fn");
                    nodeStack.push(token.surface.equals("|>") && !pipeIntoLambdaSignature
                            ? pipeNode(v2, v1)
                            : reassociateLambdaAssignment(token, v2, v1));
                }
                case VARIABLE -> {
                    Value constant = getConstantFor(token.surface);
                    if (constant != null)
                    {
                        token.morph(Token.TokenType.CONSTANT, token.surface);
                        ExpressionNode newNode = new ExpressionNode(LazyValue.ofConstant(constant), Collections.emptyList(), token);
                        token.node = newNode;
                        nodeStack.push(newNode);
                    }
                    else
                    {
                        ExpressionNode newNode = new ExpressionNode(variableNode(token.surface), Collections.emptyList(), token);
                        token.node = newNode;
                        nodeStack.push(newNode);
                    }
                }
                case FUNCTION -> {
                    if (token.postfixCall)
                    {
                        nodeStack.push(postfixCallNode(context, token, nodeStack));
                    }
                    else
                    {
                        String resolved = resolveBuiltinName(context, token);
                        String name = resolved == null ? token.surface : resolved;
                        ILazyFunction f;
                        ArrayList<ExpressionNode> p;
                        // globals will be evaluated lazily, not at compile time via .
                        boolean isKnown = resolved != null && functions.containsKey(resolved);
                        if (isKnown)
                        {
                            f = functions.get(name);
                            p = new ArrayList<>(!f.numParamsVaries() ? f.getNumParams() : 0);
                        }
                        else // potentially unknown function or just unknown function
                        {
                            f = functions.get("call");
                            p = new ArrayList<>();
                        }
                        // pop parameters off the stack until we hit the start of
                        // this function's parameter list
                        while (!nodeStack.isEmpty() && nodeStack.top() != ExpressionNode.PARAMS_START)
                        {
                            p.add(nodeStack.pop());
                        }
                        if (!isKnown)
                        {
                            p.add(ExpressionNode.ofConstant(new StringValue(name), token.morphedInto(Token.TokenType.STRINGPARAM, token.surface)));
                            token.morph(Token.TokenType.FUNCTION, "call");
                        }
                        else
                        {
                            // the token carries the resolved name: 'math.sqrt' becomes 'sqrt'
                            // once, here, instead of on every call
                            token.surface = name;
                        }
                        Collections.reverse(p);
                        if (nodeStack.top() == ExpressionNode.PARAMS_START)
                        {
                            nodeStack.pop();
                        }
                        checkNamedArgumentOrder(context, token, p);
                        List<LazyValue> params = p.stream()
                                .map(n -> isNamedArgument(token, n) ? namedArgument(argumentName(n), n.args.get(1).op) : n.op)
                                .collect(Collectors.toList());
                        ExpressionNode newNode = new ExpressionNode(
                                (c, t) -> f.lazyEval(c, t, this, token, params).evalValue(c, t),
                                p, token
                        );
                        token.node = newNode;
                        nodeStack.push(newNode);
                    }
                }
                case OPEN_PAREN -> {
                    token.node = ExpressionNode.PARAMS_START;
                    nodeStack.push(ExpressionNode.PARAMS_START);
                }
                case LITERAL -> {
                    Value number;
                    try
                    {
                        number = new NumericValue(token.surface);
                    }
                    catch (NumberFormatException exception)
                    {
                        throw ParseError.at(this, token, "Not a number");
                    }
                    //token.morph(Token.TokenType.CONSTANT, token.surface);
                    ExpressionNode newNode = ExpressionNode.ofConstant(number, token);
                    token.node = newNode;
                    nodeStack.push(newNode);
                }
                case STRINGPARAM -> {
                    //token.morph(Token.TokenType.CONSTANT, token.surface);
                    ExpressionNode newNode = ExpressionNode.ofConstant(new StringValue(token.surface), token);
                    token.node = newNode;
                    nodeStack.push(newNode);
                }
                case HEX_LITERAL -> {
                    Value hexNumber;
                    try
                    {
                        hexNumber = new NumericValue(new BigInteger(token.surface.substring(2), 16).longValue());
                    }
                    catch (NumberFormatException exception)
                    {
                        throw ParseError.at(this, token, "Not a number");
                    }
                    //token.morph(Token.TokenType.CONSTANT, token.surface);
                    ExpressionNode newNode = ExpressionNode.ofConstant(hexNumber, token);
                    token.node = newNode;
                    nodeStack.push(newNode);
                }
                default -> throw ParseError.at(this, token, "Unexpected token '" + token.surface + "'");
            }
        }
        return nodeStack.pop();
    }

    private Pair<ExpressionNode, LazyValue> getAST(Context context, boolean optimize, boolean functional, @Nullable Consumer<String> logger)
    {
        Tokenizer tokenizer = new Tokenizer(context, this, expression, allowComments, allowNewlineSubstitutions);
        // stripping lousy but acceptable semicolons
        List<Token> cleanedTokens = Tokenizer.postProcess(tokenizer.parseTokens());

        List<Token> rpn = shuntingYard(context, cleanedTokens);
        validate(context, rpn);
        ExpressionNode root = RPNToParseTree(rpn, context);
        if (!optimize && !functional)
        {
            return Pair.of(root, root.op);
        }

        Context optimizeOnlyContext = new Context.ContextForErrorReporting(context);
        // flipping to full functional representation makes it little underperforming, might be related
        // to the fact that operators are running from a bigger pool or function execution is slower
        optimizeTree(root, optimizeOnlyContext, logger, optimize, functional);
        if (!optimize) {
            return Pair.of(root, root.op);
        }
        return Pair.of(root, extractOp(optimizeOnlyContext, root, Context.Type.NONE));
    }

    private void optimizeTree(ExpressionNode root, Context optimizeOnlyContext, @Nullable Consumer<String> logger, boolean optimize, boolean toFunctional) {
        if (logger != null)
        {
            logger.accept("Input code size for " + getModuleName() + ": " + treeSize(root) + " nodes, " + treeDepth(root) + " deep");
        }

        // Defined out here to not need to conditionally assign them with debugging disabled
        int prevTreeSize = -1;
        int prevTreeDepth = -1;

        boolean changed = true;
        while (changed)
        {
            changed = false;
            while (true)
            {
                if (logger != null)
                {
                    prevTreeSize = treeSize(root);
                    prevTreeDepth = treeDepth(root);
                }
                boolean optimized = compactTree(root, Context.Type.NONE, 0, logger, optimize, toFunctional);
                if (!optimized)
                {
                    break;
                }
                changed = true;
                if (logger != null)
                {
                    logger.accept("Compacted from " + prevTreeSize + " nodes, " + prevTreeDepth + " code depth to " + treeSize(root) + " nodes, " + treeDepth(root) + " code depth");
                }
            }
            while (optimize)
            {
                if (logger != null)
                {
                    prevTreeSize = treeSize(root);
                    prevTreeDepth = treeDepth(root);
                }
                boolean optimized = optimizeConstantsAndPureFunctions(optimizeOnlyContext, root, Context.Type.NONE, 0, logger);
                if (!optimized)
                {
                    break;
                }
                changed = true;
                if (logger != null)
                {
                    logger.accept("Optimized from " + prevTreeSize + " nodes, " + prevTreeDepth + " code depth to " + treeSize(root) + " nodes, " + treeDepth(root) + " code depth");
                }
            }
        }
    }

    public List<Token> explain(Context context, @Nullable String code, @Nullable String method, @Nullable String style)
    {
        if (code == null)
        {
            ExpressionNode node;
            if (method != null)
            {
                FunctionValue function = context.host.getFunction(method);
                if (function == null) {
                    throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Unknown function " + method);
                }
                node = function.getToken().node;
                if (node == null) {
                    throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Function " + method + " is not a compiled function");
                }
            } else
            {
                node = context.host.root;
            }
            if (node == null || node.token.type == null) {
                throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "No code to explain");
            }

            // grab source reference
            Tokenizer tokenizer = new Tokenizer(context, this, context.host.main == null ? "" : stripExpression(context.host.main.code()) , true, true);
            List<Token> input = tokenizer.parseTokens();
            List<Token> cleanedTokens = Tokenizer.postProcess(input);

            Map<Pair<Integer, Integer>, List<Integer>> tokenPointers = new HashMap<>();
            for (int i = 0; i < cleanedTokens.size(); i++) {
                Token token = cleanedTokens.get(i);
                tokenPointers.computeIfAbsent(Pair.of(token.lineno, token.linepos), k -> new ArrayList<>()).add(i);
            }

            return node.tokensRecursive(this, cleanedTokens, tokenPointers);
        }
        if (style == null) {
            style = context.host.loadOverrides.equivalent;
        }

        //todo convert to explain
        Tokenizer tokenizer = new Tokenizer(context, this, stripExpression(code), true, true);
        // stripping lousy but acceptable semicolons
        List<Token> input = tokenizer.parseTokens();
        if (style.equalsIgnoreCase("raw")) {
            return input;
        }
        List<Token> cleanedTokens = Tokenizer.postProcess(input);
        if (style.equalsIgnoreCase("clean")) {
            return cleanedTokens;
        }

        List<Token> rpn = shuntingYard(context, cleanedTokens);
        validate(context, rpn);
        ExpressionNode root = RPNToParseTree(rpn, context);

        Map<Pair<Integer, Integer>, List<Integer>> tokenPointers = new HashMap<>();
        for (int i = 0; i < cleanedTokens.size(); i++) {
            Token token = cleanedTokens.get(i);
            tokenPointers.computeIfAbsent(Pair.of(token.lineno, token.linepos), k -> new ArrayList<>()).add(i);
        }

        List<Token> parsedTokens = root.tokensRecursive(this, cleanedTokens, tokenPointers);
        if (style.equalsIgnoreCase("canonical")) {
            return parsedTokens;
        }

        Context optimizeOnlyContext = new Context.ContextForErrorReporting(context);
        // pure functional
        optimizeTree(root, optimizeOnlyContext, null, style.contains("optimized"), style.contains("functional"));
        List<Token> compileTimeOptimized = root.tokensRecursive(this, cleanedTokens, tokenPointers);

        return compileTimeOptimized;
    }

    private int treeSize(ExpressionNode node)
    {
        return node.op instanceof LazyValue.ContextFreeLazyValue ? 1 : node.args.stream().mapToInt(this::treeSize).sum() + 1;
    }

    private int treeDepth(ExpressionNode node)
    {
        return node.op instanceof LazyValue.ContextFreeLazyValue ? 1 : node.args.stream().mapToInt(this::treeDepth).max().orElse(0) + 1;
    }


    private boolean compactTree(ExpressionNode node, Context.Type expectedType, int indent, @Nullable Consumer<String> logger, boolean optimize, boolean toFunctional)
    {
        // ctx is just to report errors, not values evaluation
        boolean optimized = false;
        Token.TokenType token = node.token.type;
        if (!token.isFunctional())
        {
            return false;
        }
        // input special cases here, like function signature
        if (node.op instanceof LazyValue.Constant)
        {
            return false; // optimized already
        }
        // function or operator
        String symbol = node.token.surface;
        Callables.EvalNode operation = ((token == Token.TokenType.FUNCTION) ? functions : operators).get(symbol);
        Context.Type requestedType = operation.staticType(expectedType);
        for (ExpressionNode arg : node.args)
        {
            if (compactTree(arg, requestedType, indent + 1, logger, optimize, toFunctional))
            {
                optimized = true;
            }
        }

        if (optimize && expectedType != Context.Type.MAPDEF && (symbol.equals("->") || symbol.equals("define") ) && node.args.size() == 2)
        {
            String rop = node.args.get(1).token.surface;
            ExpressionNode returnNode = null;
            if ((rop.equals(";") || rop.equals("then")))
            {
                List<ExpressionNode> thenArgs = node.args.get(1).args;
                if (thenArgs.size() > 1 && thenArgs.get(thenArgs.size() - 1).token.surface.equals("return"))
                {
                    returnNode = thenArgs.get(thenArgs.size() - 1);
                }
            }
            else if (rop.equals("return"))
            {
                returnNode = node.args.get(1);
            }
            if (returnNode != null) // tail return
            {
                if (!returnNode.args.isEmpty())
                {
                    returnNode.op = returnNode.args.get(0).op;
                    returnNode.token = returnNode.args.get(0).token;
                    returnNode.range = returnNode.args.get(0).range;
                    returnNode.args = returnNode.args.get(0).args;
                    if (logger != null)
                    {
                        logger.accept(" - Removed unnecessary tail return of " + returnNode.token.surface + " from function body at line " + (returnNode.token.lineno + 1) + ", node depth " + indent);
                    }
                }
                else
                {
                    returnNode.op = LazyValue.ofConstant(Value.NULL);
                    returnNode.token.morph(Token.TokenType.CONSTANT, "null");
                    returnNode.args = Collections.emptyList();
                    if (logger != null)
                    {
                        logger.accept(" - Removed unnecessary tail return from function body at line " + (returnNode.token.lineno + 1) + ", node depth " + indent);
                    }
                }

            }
        }
        for (Map.Entry<String, String> pair : functionalEquivalence.entrySet())
        {
            String operator = pair.getKey();
            String function = pair.getValue();
            if (optimize && (symbol.equals(operator) || symbol.equals(function)) && !node.args.isEmpty())
            {
                boolean leftOptimizable = operators.get(operator).isLeftAssoc();
                ExpressionNode optimizedChild = node.args.get(leftOptimizable ? 0 : (node.args.size() - 1));
                String type = optimizedChild.token.surface;
                if ((type.equals(operator) || type.equals(function)) && (!(optimizedChild.op instanceof LazyValue.ContextFreeLazyValue)))
                {
                    List<ExpressionNode> newargs = new ArrayList<>();
                    if (leftOptimizable)
                    {
                        newargs.addAll(optimizedChild.args);
                        for (int i = 1; i < node.args.size(); i++)
                        {
                            newargs.add(node.args.get(i));
                        }
                    }
                    else
                    {
                        for (int i = 0; i < node.args.size() - 1; i++)
                        {
                            newargs.add(node.args.get(i));
                        }
                        newargs.addAll(optimizedChild.args);
                    }

                    if (logger != null)
                    {
                        logger.accept(" - " + symbol + "(" + node.args.size() + ") => " + function + "(" + newargs.size() + ") at line " + (node.token.lineno + 1) + ", node depth " + indent);
                    }
                    node.token.morph(Token.TokenType.FUNCTION, function);
                    node.args = newargs;
                    return true;
                }
            }
        }

        if (toFunctional && functionalAliases.containsKey(symbol) && !node.args.isEmpty())
        {
            optimized = true;
            node.token.morph(Token.TokenType.FUNCTION, functionalAliases.get(symbol));
        }

        return optimized;
    }

    private boolean optimizeConstantsAndPureFunctions(Context ctx, ExpressionNode node, Context.Type expectedType, int indent, @Nullable Consumer<String> logger)
    {
        // ctx is just to report errors, not values evaluation
        boolean optimized = false;
        Token.TokenType token = node.token.type;
        if (!token.isFunctional())
        {
            return false;
        }
        String symbol = node.token.surface;

        // input special cases here, like function signature
        if (node.op instanceof LazyValue.Constant)
        {
            return false; // optimized already
        }
        // function or operator

        Callables.EvalNode operation = ((token == Token.TokenType.FUNCTION) ? functions : operators).get(symbol);
        Context.Type requestedType = operation.staticType(expectedType);
        for (ExpressionNode arg : node.args)
        {
            if (optimizeConstantsAndPureFunctions(ctx, arg, requestedType, indent + 1, logger))
            {
                optimized = true;
            }
        }

        for (ExpressionNode arg : node.args)
        {
            if (arg.token.type.isConstant())
            {
                continue;
            }
            if (arg.op instanceof LazyValue.ContextFreeLazyValue)
            {
                continue;
            }
            return optimized;
        }
        // a few exceptions which we don't implement in the framework for simplicity for now
        if (!operation.pure())
        {
            if (!(symbol.equals("->") || symbol.equals("define")) || expectedType != Context.Type.MAPDEF)
            {
                return optimized;
            }
        }
        // element access with constant elements will always resolve the same way.
        if (operation.pure() && symbol.equals(":") && expectedType == Context.Type.LVALUE)
        {
            expectedType = Context.Type.NONE;
        }
        List<LazyValue> args = new ArrayList<>(node.args.size());
        for (ExpressionNode arg : node.args)
        {
            try
            {
                if (arg.op instanceof LazyValue.Constant)
                {
                    Value val = ((LazyValue.Constant) arg.op).get();
                    args.add((c, t) -> val);
                }
                else
                {
                    args.add((c, t) -> arg.op.evalValue(ctx, requestedType));
                }
            }
            catch (NullPointerException npe)
            {
                throw new ExpressionException(ctx, this, node.token, "Attempted to evaluate context free expression");
            }
        }
        // applying argument unpacking
        args = AbstractLazyFunction.lazify(AbstractLazyFunction.unpackLazy(args, ctx, requestedType));
        Value result;
        try
        {
            if (operation instanceof ILazyFunction)
            {
                result = ((ILazyFunction) operation).lazyEval(ctx, expectedType, this, node.token, args).evalValue(null, expectedType);
            }
            else if (args.size() == 1)
            {
                result = ((ILazyOperator) operation).lazyEval(ctx, expectedType, this, node.token, args.get(0), null).evalValue(null, expectedType);
            }
            else // args == 2
            {
                result = ((ILazyOperator) operation).lazyEval(ctx, expectedType, this, node.token, args.get(0), args.get(1)).evalValue(null, expectedType);
            }
        }
        catch (RuntimeException | StackOverflowError failure)
        {
            // folding is an optimisation, not a semantics: a call that fails here has to fail where
            // it is written, at run time, so that 'try' can see it and dead code stays quiet.
            // Giving up on this node is always safe - what is left is the call itself.
            return optimized;
        }
        node.token.disguiseAs(null, "=> "+result.getString());
        node.op = LazyValue.ofConstant(result);
        if (logger != null)
        {
            logger.accept(" - " + symbol + "(" + args.stream().map(a -> a.evalValue(null, requestedType).getString()).collect(Collectors.joining(", ")) + ") => " + result.getString() + " at line " + (node.token.lineno + 1) + ", node depth " + indent);
        }
        return true;
    }

    private LazyValue extractOp(Context ctx, ExpressionNode node, Context.Type expectedType)
    {
        if (node.op instanceof LazyValue.Constant)
        {
            // constants are immutable
            if (node.token.type.isConstant())
            {
                Value value = ((LazyValue.Constant) node.op).get();
                return (c, t) -> value;
            }
            return node.op;
        }
        if (node.op instanceof LazyValue.ContextFreeLazyValue)
        {
            return ((LazyValue.ContextFreeLazyValue) node.op).evalType(expectedType);
        }
        Token token = node.token;
        switch (token.type)
        {
            case UNARY_OPERATOR:
            {
                ILazyOperator op = operators.get(token.surface);
                Context.Type requestedType = op.staticType(expectedType);
                LazyValue arg = extractOp(ctx, node.args.get(0), requestedType);
                return (c, t) -> op.lazyEval(c, t, this, token, arg, null).evalValue(c, t);
            }
            case OPERATOR:
            {
                ILazyOperator op = operators.get(token.surface);
                Context.Type requestedType = op.staticType(expectedType);
                LazyValue arg = extractOp(ctx, node.args.get(0), requestedType);
                LazyValue arh = extractOp(ctx, node.args.get(1), requestedType);
                LazyValue built = (c, t) -> op.lazyEval(c, t, this, token, arg, arh).evalValue(c, t);
                return isLambdaNode(token, node.args.get(0).token) ? new LazyValue.Lambda(built) : built;
            }
            case VARIABLE:
                return variableNode(token.surface);
            case FUNCTION:
            {
                ILazyFunction f = functions.get(token.surface);
                Context.Type requestedType = f.staticType(expectedType);
                List<LazyValue> params = node.args.stream()
                        .map(n -> isNamedArgument(token, n)
                                ? namedArgument(argumentName(n), extractOp(ctx, n.args.get(1), Context.Type.NONE))
                                : extractOp(ctx, n, requestedType))
                        .collect(Collectors.toList());
                LazyValue built = (c, t) -> f.lazyEval(c, t, this, token, params).evalValue(c, t);
                return !node.args.isEmpty() && isLambdaNode(token, node.args.get(0).token)
                        ? new LazyValue.Lambda(built) : built;
            }
            case CONSTANT:
                return node.op;
            default:
                throw new ExpressionException(ctx, this, node.token, "Unexpected token '" + node.token.type + " " + node.token.surface + "'");

        }
    }

    private void validate(Context c, List<Token> rpn)
    {
        /*-
         * Thanks to Norman Ramsey:
         * http://http://stackoverflow.com/questions/789847/postfix-notation-validation
         */
        // each push on to this stack is a new function scope, with the value of
        // each
        // layer on the stack being the count of the number of parameters in
        // that scope
        IntArrayList stack = new IntArrayList(); // IntArrayList instead of just IntStack because we need to query the size

        // push the 'global' scope
        stack.push(0);

        for (Token token : rpn)
        {
            switch (token.type)
            {
                case UNARY_OPERATOR:
                    if (stack.topInt() < 1)
                    {
                        throw ParseError.at(this, token, "Missing parameter(s) for operator " + token);
                    }
                    break;
                case OPERATOR:
                    if (stack.topInt() < 2)
                    {
                        if (token.surface.equals(";"))
                        {
                            throw ParseError.at(this, token, "Empty expression found for ';'");
                        }
                        throw ParseError.at(this, token, "Missing parameter(s) for operator " + token);
                    }
                    // pop the operator's 2 parameters and add the result
                    stack.set(stack.size() - 1, stack.topInt() - 2 + 1);
                    break;
                case FUNCTION:
                    stack.popInt();
                    // due to unpacking, all functions can have variable number of arguments
                    // we will be checking that at runtime.
                    // TODO try analyze arguments and assess if its possible that they are static
                    if (stack.size() <= 0)
                    {
                        throw ParseError.at(this, token, "Too many function calls, maximum scope exceeded");
                    }
                    if (token.postfixCall)
                    {
                        // also consumes the value before the parentheses: that is the function
                        if (stack.topInt() < 1)
                        {
                            throw ParseError.at(this, token, "There is nothing to call before '('.");
                        }
                        stack.set(stack.size() - 1, stack.topInt() - 1);
                    }
                    // push the result of the function
                    stack.set(stack.size() - 1, stack.topInt() + 1);
                    break;
                case OPEN_PAREN:
                    stack.push(0);
                    break;
                default:
                    stack.set(stack.size() - 1, stack.topInt() + 1);
            }
        }

        if (stack.size() > 1)
        {
            throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Too many unhandled function parameter lists");
        }
        else if (stack.topInt() > 1)
        {
            throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Too many numbers or variables");
        }
        else if (stack.topInt() < 1)
        {
            throw new ParseError(new SourcePosition(getModuleName() == null ? "<code>" : getModuleName(), 0, 0), "Empty expression");
        }
    }
}
