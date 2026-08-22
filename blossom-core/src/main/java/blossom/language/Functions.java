package blossom.language;

import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Callables;
import blossom.interpreter.LazyValue;
import blossom.interpreter.Token;
import blossom.interpreter.argument.FunctionArgument;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.ReturnStatement;
import blossom.value.FunctionSignatureValue;
import blossom.value.FunctionValue;
import blossom.value.FunctionAnnotationValue;
import blossom.value.ListValue;
import blossom.value.Value;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Functions
{
    public static void apply(Expression expression) // public just to get the javadoc right
    {
        // the old import(name, symbols...) no longer exists: import is now a preamble
        // declaration, resolved at load time, and symbols are reached through an alias (see
        // DECISIONI.md)

        // needs to be lazy because of custom context of execution of arguments as a signature
        expression.addCustomFunction("call", new Callables.AbstractLazyFunction(-1, "call")
        {
            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression expr, Token tok, List<LazyValue> lv)
            {
                if (lv.isEmpty())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "'call' expects at least function name to call");
                }
                // lv.remove(lv.size()-1); // aint gonna cut it // maybe it will because of the eager eval changes
                if (t != Context.SIGNATURE) // just call the function
                {
                    List<Value> args = Callables.AbstractFunction.unpackLazy(lv, c, Context.NONE);
                    FunctionArgument functionArgument = FunctionArgument.findIn(c, expression.module, args, 0, false, true);
                    FunctionValue fun = functionArgument.function;
                    return fun.callInContext(c, t, functionArgument.args);
                }
                // gimme signature
                String name = lv.get(0).evalValue(c, Context.NONE).getString();
                List<String> args = new ArrayList<>();
                List<String> argumentTypes = new ArrayList<>();
                List<LazyValue> defaults = new ArrayList<>();
                List<String> globals = new ArrayList<>();
                String varArgs = null;
                int varArgsAt = -1;
                for (int i = 1; i < lv.size(); i++)
                {
                    Value v = lv.get(i).evalValue(c, Context.LOCALIZATION);
                    if (v instanceof final FunctionAnnotationValue fav)
                    {
                        if (fav.type == FunctionAnnotationValue.Type.GLOBAL)
                        {
                            globals.add(fav.name());
                        }
                        else
                        {
                            if (varArgs != null)
                            {
                                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Variable argument identifier is already defined as " + varArgs + ", trying to overwrite with " + fav.name());
                            }
                            varArgs = fav.name();
                            varArgsAt = args.size();
                        }
                    }
                    else if (v instanceof final blossom.value.TypedParameterValue typed)
                    {
                        args.add(typed.parameter());
                        argumentTypes.add(typed.canonicalType());
                        defaults.add(null);
                    }
                    else if (v instanceof final blossom.value.VariableReferenceValue reference)
                    {
                        args.add(reference.name());
                        argumentTypes.add(null);
                        defaults.add(null);
                    }
                    else if (v instanceof final blossom.value.NamedArgumentValue named)
                    {
                        // 'update = true' inside a signature: a parameter with a fallback value
                        args.add(named.name());
                        argumentTypes.add(null);
                        defaults.add(named.value());
                    }
                    else
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Only variables can be used in function signature, not  " + v.getString());
                    }
                }
                validateSignature(name, args, varArgs, varArgsAt, globals);
                FunctionSignatureValue signature = new FunctionSignatureValue(name, args, argumentTypes, varArgs, globals);
                signature.withDefaults(defaults);
                Value retval = signature;
                return (cc, tt) -> retval;
            }

            @Override
            public boolean pure()
            {
                return false; //true for sinature, but lets leave it for later
            }

            @Override
            public boolean transitive()
            {
                return false;
            }

            @Override
            public Context.Type staticType(Context.Type outerType)
            {
                return outerType == Context.SIGNATURE ? Context.LOCALIZATION : Context.NONE;
            }
        });


        /*
         * fn(a, b) -> a + b : a function written without a name.
         *
         * The tokenizer doesn't need to know anything special: it decides FUNCTION or VARIABLE by
         * checking whether a parenthesis follows the name, so 'fn(' is already a call while
         * 'fn name(...)' remains the declaration keyword. The two forms can't be confused.
         *
         * The body is identical to that of 'call' in Context.SIGNATURE, minus the first argument:
         * there the function name is written explicitly, here the parser supplies it.
         */
        expression.addCustomFunction("fn", new Callables.AbstractLazyFunction(-1, "fn")
        {
            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression expr, Token tok, List<LazyValue> lv)
            {
                if (t != Context.SIGNATURE)
                {
                    throw new ParseError(SourcePosition.UNKNOWN,
                            "'fn(...)' is a function without a name and only makes sense before '->', "
                                    + "as in fn(a, b) -> a + b.");
                }
                List<String> args = new ArrayList<>();
                List<String> argumentTypes = new ArrayList<>();
                List<LazyValue> defaults = new ArrayList<>();
                List<String> globals = new ArrayList<>();
                String varArgs = null;
                int varArgsAt = -1;
                for (LazyValue parameter : lv)
                {
                    Value v = parameter.evalValue(c, Context.LOCALIZATION);
                    if (v instanceof final FunctionAnnotationValue fav)
                    {
                        if (fav.type == FunctionAnnotationValue.Type.GLOBAL)
                        {
                            globals.add(fav.name());
                        }
                        else
                        {
                            if (varArgs != null)
                            {
                                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Variable argument identifier is already defined as " + varArgs + ", trying to overwrite with " + fav.name());
                            }
                            varArgs = fav.name();
                            varArgsAt = args.size();
                        }
                    }
                    else if (v instanceof final blossom.value.TypedParameterValue typed)
                    {
                        args.add(typed.parameter());
                        argumentTypes.add(typed.canonicalType());
                        defaults.add(null);
                    }
                    else if (v instanceof final blossom.value.VariableReferenceValue reference)
                    {
                        args.add(reference.name());
                        argumentTypes.add(null);
                        defaults.add(null);
                    }
                    else if (v instanceof final blossom.value.NamedArgumentValue named)
                    {
                        // 'update = true' inside a signature: a parameter with a fallback value
                        args.add(named.name());
                        argumentTypes.add(null);
                        defaults.add(named.value());
                    }
                    else
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Only variables can be used in function signature, not  " + v.getString());
                    }
                }
                validateSignature("fn(...)", args, varArgs, varArgsAt, globals);
                FunctionSignatureValue signature =
                        new FunctionSignatureValue(Expression.anonymousName(tok), args, argumentTypes, varArgs, globals);
                signature.withDefaults(defaults);
                Value retval = signature;
                return (cc, tt) -> retval;
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
                return outerType == Context.SIGNATURE ? Context.LOCALIZATION : Context.NONE;
            }
        });


        // lazy because the argument is needed as a name, not as a value: addContextFunction
        // evaluates its own arguments in Context.NONE, where an identifier has already been read
        expression.addLazyFunction("outer", 1, (c, t, lv) ->
        {
            if (t != Context.LOCALIZATION)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Outer scoping of variables is only possible in function signatures.");
            }
            Value annotation = new FunctionAnnotationValue(lv.get(0).evalValue(c, Context.LOCALIZATION),
                    FunctionAnnotationValue.Type.GLOBAL);
            return (cc, tt) -> annotation;
        });

        //assigns const procedure to the lhs, returning its previous value
        // must be lazy due to RHS being an expression to save to execute
        expression.addLazyBinaryOperatorWithDelegation("->", "define", Operators.precedence.get("def->"), false, false, (c, type, e, t, lv1, lv2) ->
        {
            if (type == Context.MAPDEF)
            {
                Value result = ListValue.of(lv1.evalValue(c), lv2.evalValue(c));
                return (cc, tt) -> result;
            }
            if (type == Context.MATCH)
            {
                // inside a match the arrow binds a shape to a body, and the body is not evaluated
                // here: out of ten arms only one gets executed
                Value arm = new blossom.value.MatchArmValue(lv1.evalValue(c, Context.LOCALIZATION), lv2);
                return (cc, tt) -> arm;
            }
            Value v1 = lv1.evalValue(c, Context.SIGNATURE);
            if (!(v1 instanceof final FunctionSignatureValue sign))
            {
                throw new ParseError(SourcePosition.UNKNOWN, "'->' wants a function signature on its left, e.g. name(a, b) -> a + b.");
            }
            Value result = expression.createUserDefinedFunction(c, sign.identifier(), e, t, sign.arguments(),
                    sign.argumentTypes(), sign.defaults(), sign.varArgs(), sign.globals(), lv2);
            return (cc, tt) -> result;
        });

        expression.addImpureFunction("return", lv -> {
            throw new ReturnStatement(lv.size() == 0 ? Value.NULL : lv.get(0));
        });
    }

    /**
     * Controlla una firma prima che diventi una funzione.
     *
     * <p>Sono errori che si vedono guardando la riga, quindi vanno detti quando la riga si legge:
     * lasciarli passare non produce un errore piu' tardi, produce una funzione che lega gli
     * argomenti a nomi diversi da quelli che c'e' scritto.
     *
     * @param varArgsAt quanti parametri normali c'erano quando e' comparso {@code ...rest}: se non
     *                  sono tutti, il resto della firma sta dopo qualcosa che prende tutto il resto
     */
    private static void validateSignature(String name, List<String> args, @Nullable String varArgs,
            int varArgsAt, List<String> globals)
    {
        if (varArgs != null && varArgsAt != args.size())
        {
            throw new ParseError(SourcePosition.UNKNOWN, "'...'" + varArgs + " must be the last parameter of "
                    + name + ": it takes every argument that is left, so nothing can follow it.");
        }
        Set<String> seen = new HashSet<>();
        for (String parameter : args)
        {
            if (StdLib.isReservedAsAName(parameter))
            {
                throw new ParseError(SourcePosition.UNKNOWN, "'" + parameter + "' is part of the language"
                        + " and cannot be the name of a parameter of " + name + ".");
            }
            if (!seen.add(parameter))
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "Parameter '" + parameter + "' of " + name + " is declared twice.");
            }
        }
        if (varArgs != null && !seen.add(varArgs))
        {
            throw new ParseError(SourcePosition.UNKNOWN,
                    "Parameter '" + varArgs + "' of " + name + " is declared twice.");
        }
        for (String outer : globals)
        {
            if (!seen.add(outer))
            {
                throw new ParseError(SourcePosition.UNKNOWN,
                        "'outer(" + outer + ")' of " + name + " collides with a parameter of the same name.");
            }
        }
    }
}
