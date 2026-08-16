package blossom.language;

import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.Fluff;
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

import java.util.ArrayList;
import java.util.List;

public class Functions
{
    public static void apply(Expression expression) // public just to get the javadoc right
    {
        // il vecchio import(nome, simboli...) non esiste piu': l'import e' una dichiarazione del
        // preambolo, risolta a caricamento, e i simboli si raggiungono per alias (vedi DECISIONI.md)

        // needs to be lazy because of custom context of execution of arguments as a signature
        expression.addCustomFunction("call", new Fluff.AbstractLazyFunction(-1, "call")
        {
            @Override
            public LazyValue lazyEval(Context c, Context.Type t, Expression expr, Token tok, List<LazyValue> lv)
            {
                if (lv.isEmpty())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "'call' expects at least function name to call");
                }
                //lv.remove(lv.size()-1); // aint gonna cut it // maybe it will because of the eager eval changes
                if (t != Context.SIGNATURE) // just call the function
                {
                    List<Value> args = Fluff.AbstractFunction.unpackLazy(lv, c, Context.NONE);
                    FunctionArgument functionArgument = FunctionArgument.findIn(c, expression.module, args, 0, false, true);
                    FunctionValue fun = functionArgument.function;
                    return fun.callInContext(c, t, functionArgument.args);
                }
                // gimme signature
                String name = lv.get(0).evalValue(c, Context.NONE).getString();
                List<String> args = new ArrayList<>();
                List<String> argumentTypes = new ArrayList<>();
                List<String> globals = new ArrayList<>();
                String varArgs = null;
                for (int i = 1; i < lv.size(); i++)
                {
                    Value v = lv.get(i).evalValue(c, Context.LOCALIZATION);
                    if (!v.isBound())
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Only variables can be used in function signature, not  " + v.getString());
                    }
                    if (v instanceof final FunctionAnnotationValue fav)
                    {
                        if (fav.type == FunctionAnnotationValue.Type.GLOBAL)
                        {
                            globals.add(v.boundVariable);
                        }
                        else
                        {
                            if (varArgs != null)
                            {
                                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Variable argument identifier is already defined as " + varArgs + ", trying to overwrite with " + v.boundVariable);
                            }
                            varArgs = v.boundVariable;
                        }
                    }
                    else if (v instanceof final blossom.value.TypedParameterValue typed)
                    {
                        args.add(typed.boundVariable);
                        argumentTypes.add(typed.canonicalType());
                    }
                    else
                    {
                        args.add(v.boundVariable);
                        argumentTypes.add(null);
                    }
                }
                Value retval = new FunctionSignatureValue(name, args, argumentTypes, varArgs, globals);
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


        expression.addContextFunction("outer", 1, (c, t, lv) ->
        {
            if (t != Context.LOCALIZATION)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Outer scoping of variables is only possible in function signatures.");
            }
            return new FunctionAnnotationValue(lv.get(0), FunctionAnnotationValue.Type.GLOBAL);
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
            Value v1 = lv1.evalValue(c, Context.SIGNATURE);
            if (!(v1 instanceof final FunctionSignatureValue sign))
            {
                throw new ParseError(SourcePosition.UNKNOWN, "'->' wants a function signature on its left, e.g. name(a, b) -> a + b.");
            }
            Value result = expression.createUserDefinedFunction(c, sign.identifier(), e, t, sign.arguments(),
                    sign.argumentTypes(), sign.varArgs(), sign.globals(), lv2);
            return (cc, tt) -> result;
        });

        expression.addImpureFunction("return", lv -> {
            throw new ReturnStatement(lv.size() == 0 ? Value.NULL : lv.get(0));
        });
    }
}
