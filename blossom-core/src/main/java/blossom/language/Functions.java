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

import java.util.ArrayList;
import java.util.List;

public class Functions
{
    public static void apply(Expression expression) // public just to get the javadoc right
    {
        // il vecchio import(nome, simboli...) non esiste piu': l'import e' una dichiarazione del
        // preambolo, risolta a caricamento, e i simboli si raggiungono per alias (vedi DECISIONI.md)

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
                //lv.remove(lv.size()-1); // aint gonna cut it // maybe it will because of the eager eval changes
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
                        // 'update = true' dentro una firma: un parametro con un valore di riserva
                        args.add(named.name());
                        argumentTypes.add(null);
                        defaults.add(named.value());
                    }
                    else
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Only variables can be used in function signature, not  " + v.getString());
                    }
                }
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
         * fn(a, b) -> a + b - una funzione scritta senza nome.
         *
         * Il tokenizer non ha bisogno di sapere niente: decide FUNCTION o VARIABLE guardando se
         * dopo il nome c'e' una parentesi, quindi 'fn(' e' gia' una chiamata mentre 'fn nome(...)'
         * resta la parola chiave della dichiarazione. Le due forme non possono confondersi.
         *
         * Il corpo e' identico a quello di 'call' in Context.SIGNATURE, meno il primo argomento:
         * li' il nome della funzione e' scritto, qui lo mette il parser.
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
                        // 'update = true' dentro una firma: un parametro con un valore di riserva
                        args.add(named.name());
                        argumentTypes.add(null);
                        defaults.add(named.value());
                    }
                    else
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "Only variables can be used in function signature, not  " + v.getString());
                    }
                }
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


        // pigra perche' l'argomento le serve come nome, non come valore: addContextFunction valuta
        // i propri argomenti in Context.NONE, dove un identificatore e' gia' stato letto
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
}
