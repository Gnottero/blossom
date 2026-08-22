package blossom.language;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.exception.ExitStatement;
import blossom.exception.ProcessedThrowStatement;
import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import blossom.value.FunctionValue;
import blossom.interpreter.Callables;
import blossom.interpreter.Token;
import blossom.value.ListValue;
import blossom.value.MatchArmValue;
import blossom.value.TypedParameterValue;
import blossom.value.ValueTypes;
import blossom.value.VariableReferenceValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ControlFlow
{
    public static void apply(Expression expression) // public just to get the javadoc right
    {
        // needs to be lazy cause of custom contextualization
        expression.addLazyBinaryOperator(";", "then", Operators.precedence.get("nextop;"), true, true, t -> Context.Type.VOID, (c, t, lv1, lv2) ->
        {
            lv1.evalValue(c, Context.VOID);
            Value v2 = lv2.evalValue(c, t);
            return (cc, tt) -> v2;
        }, (c, t, lv) -> {
            int imax = lv.size() - 1;
            for (int i = 0; i < imax; i++)
            {
                lv.get(i).evalValue(c, Context.VOID);
            }
            Value v = lv.get(imax).evalValue(c, t);
            return (cc, tt) -> v;
        });


        /*
         * match(subject, shape -> body, ..., else -> body)
         *
         * The subject is evaluated only once, and each arm states what it recognizes instead of
         * repeating the comparison. A shape can be:
         *
         *   a value        0 -> 'unharmed'        matches if equal
         *   a name         d -> d * 2             always matches, and binds the subject to that name
         *   a typed name   d: num -> ...          matches if the subject is of that type
         *   else           else -> 'unknown'      the final branch
         *
         * If no arm matches, the result is null, just like an 'if' with no final branch.
         */
        expression.addCustomFunction("match", new Callables.AbstractLazyFunction(-1, "match")
        {
            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression expr, Token tok, List<LazyValue> lv)
            {
                if (lv.size() < 2)
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow",
                            "'match' needs a value and at least one arm, as in match(x, 1 -> 'one', else -> 'other')");
                }
                Value subject = lv.get(0).evalValue(c, Context.NONE);
                for (int i = 1; i < lv.size(); i++)
                {
                    Value armValue = lv.get(i).evalValue(c, Context.MATCH);
                    if (!(armValue instanceof final MatchArmValue arm))
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow",
                                "Every arm of a 'match' is written 'shape -> result', but one is "
                                        + armValue.getPrettyString() + ".");
                    }
                    Value shape = arm.pattern();
                    if (shape instanceof final VariableReferenceValue reference)
                    {
                        if (!reference.name().equals("else"))
                        {
                            c.setVariable(reference.name(), (cc, tt) -> subject);
                        }
                        Value taken = arm.body().evalValue(c, t);
                        return (cc, tt) -> taken;
                    }
                    if (shape instanceof final TypedParameterValue typed)
                    {
                        if (!ValueTypes.matches(typed.canonicalType(), subject))
                        {
                            continue;
                        }
                        c.setVariable(typed.parameter(), (cc, tt) -> subject);
                        Value taken = arm.body().evalValue(c, t);
                        return (cc, tt) -> taken;
                    }
                    if (subject.equals(shape))
                    {
                        Value taken = arm.body().evalValue(c, t);
                        return (cc, tt) -> taken;
                    }
                }
                return LazyValue.NULL;
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

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return Context.MATCH;
            }
        });

        // obvious lazy due to conditional evaluation of arguments
        expression.addLazyFunction("if", (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow", "'if' statement needs to have at least one condition and one case");
            }
            for (int i = 0; i < lv.size() - 1; i += 2)
            {
                if (lv.get(i).evalValue(c, Context.BOOLEAN).getBoolean())
                {
                    Value ret = lv.get(i + 1).evalValue(c, t);
                    return (cc, tt) -> ret;
                }
            }
            if (lv.size() % 2 == 1)
            {
                Value ret = lv.get(lv.size() - 1).evalValue(c, t);
                return (cc, tt) -> ret;
            }
            return (cc, tt) -> Value.NULL;
        });

        expression.addImpureFunction("exit", lv -> {
            throw new ExitStatement(lv.isEmpty() ? Value.NULL : lv.get(0));
        });

        expression.addImpureFunction("throw", lv ->
        {
            switch (lv.size())
            {
                case 0 -> throw new ThrowStatement(Value.NULL, Throwables.USER_DEFINED);
                case 1 -> throw new ThrowStatement(lv.get(0), Throwables.USER_DEFINED);
                case 2 -> throw new ThrowStatement(lv.get(1), Throwables.getTypeForException(lv.get(0).getString()));
                case 3 -> throw new ThrowStatement(lv.get(2), Throwables.getTypeForException(lv.get(1).getString()), lv.get(0).getString());
                default -> throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow", "throw() can't accept more than 3 parameters");
            }
        });

        // needs to be lazy since execution of parameters but first one are conditional
        expression.addLazyFunction("try", (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow", "'try' needs at least an expression block, and either a catch_epr, or a number of pairs of filters and catch_expr");
            }
            try
            {
                Value retval = lv.get(0).evalValue(c, t);
                return (ct, tt) -> retval;
            }
            catch (ProcessedThrowStatement ret)
            {
                if (lv.size() == 1)
                {
                    if (!ret.thrownExceptionType.isUserException())
                    {
                        throw ret;
                    }
                    return (ct, tt) -> Value.NULL;
                }
                if (lv.size() > 3 && lv.size() % 2 == 0)
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "control_flow", "Try-catch block needs the code to run, and either a catch expression for user thrown exceptions, or a number of pairs of filters and catch expressions");
                }

                Value val = null; // This is always assigned at some point, just the compiler doesn't know

                LazyValue defaultVal = c.implicitValue;
                c.implicitValue = (ct, tt) -> ret.data;
                LazyValue trace = c.getVariable("_trace");
                c.setVariable("_trace", (ct, tt) -> MapValue.wrap(Map.of(
                        StringValue.of("stack"), ListValue.wrap(ret.stack.stream().map(f -> ListValue.of(
                                // a function defined on the fly (script run, test) has no module:
                                // the stack should stay readable regardless, not blow up
                                StringValue.of(f.getModule() == null ? "" : f.getModule().name()),
                                StringValue.of(f.getString()),
                                NumericValue.of(f.getToken().lineno + 1),
                                NumericValue.of(f.getToken().linepos + 1)
                        ))),

                        StringValue.of("locals"), MapValue.wrap(locals(ret.context)),
                        StringValue.of("token"), ListValue.of(
                                StringValue.of(ret.token.surface),
                                NumericValue.of(ret.token.lineno + 1),
                                NumericValue.of(ret.token.linepos + 1)
                        )
                )));

                if (lv.size() == 2)
                {
                    if (ret.thrownExceptionType.isUserException())
                    {
                        val = catchBranch(c, lv.get(1)).evalValue(c, t);
                    }
                }
                else
                {
                    int pointer = 1;
                    while (pointer < lv.size() - 1)
                    {
                        if (ret.thrownExceptionType.isRelevantFor(lv.get(pointer).evalValue(c).getString()))
                        {
                            val = catchBranch(c, lv.get(pointer + 1)).evalValue(c, t);
                            break;
                        }
                        pointer += 2;
                    }
                }
                c.implicitValue = defaultVal;
                if (trace != null)
                {
                    c.setVariable("_trace", trace);
                }
                else
                {
                    c.delVariable("_trace");
                }
                if (val == null)  // not handled
                {
                    throw ret;
                }
                Value retval = val;
                return (ct, tt) -> retval;
            }
        });
    }

    /**
     * The fallback branch of a {@code try}, which can be written in two ways.
     *
     * <p>The traditional form is an expression, where {@code _} is the thrown value. The newer
     * form is a lambda, {@code fn(e) -> ...}, which gives it a name: that is the form to prefer,
     * because {@code _} is also the current element of any loop, and a {@code for} inside a
     * {@code catch} would steal it. With a name that problem does not exist.
     *
     * <p>With two parameters, the second one is {@code _trace}.
     */
    private static LazyValue catchBranch(Context c, LazyValue branch)
    {
        if (!(branch instanceof LazyValue.Lambda))
        {
            return branch;
        }
        FunctionValue handler = (FunctionValue) branch.evalValue(c, Context.NONE);
        int arity = handler.getArguments().size();
        return (cc, tt) ->
        {
            List<Value> args = new ArrayList<>(arity);
            if (arity > 0)
            {
                args.add(cc.implicitValue().evalValue(cc, Context.NONE));
            }
            if (arity > 1)
            {
                LazyValue trace = cc.getVariable("_trace");
                args.add(trace == null ? Value.NULL : trace.evalValue(cc, Context.NONE));
            }
            while (args.size() < arity)
            {
                args.add(Value.NULL);
            }
            return handler.callInContext(cc, Context.NONE, args).evalValue(cc, Context.NONE);
        };
    }

    /**
     * The local variables of the frame where the error originated, for the {@code _trace} of
     * {@code try}.
     *
     * <p>{@code _trace} itself is excluded: it is what we are currently building, and reading it
     * here would make it appear inside itself.
     */
    private static Map<Value, Value> locals(Context context)
    {
        Map<Value, Value> locals = new java.util.HashMap<>();
        context.forEachVariable((name, value) ->
        {
            if (!name.equals("_trace"))
            {
                locals.put(StringValue.of(name), value.evalValue(context));
            }
        });
        return locals;
    }
}
