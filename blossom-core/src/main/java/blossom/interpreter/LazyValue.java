package blossom.interpreter;

import blossom.value.Value;

/**
 * LazyNumber interface created for lazily evaluated functions
 */
@FunctionalInterface
public interface LazyValue
{
    LazyValue FALSE = (c, t) -> Value.FALSE;
    LazyValue TRUE = (c, t) -> Value.TRUE;
    LazyValue NULL = (c, t) -> Value.NULL;
    LazyValue ZERO = (c, t) -> Value.ZERO;

    static LazyValue ofConstant(Value val)
    {
        return new Constant(val);
    }

    Value evalValue(Context c, Context.Type type);

    default Value evalValue(Context c)
    {
        return evalValue(c, Context.Type.NONE);
    }

    @FunctionalInterface
    interface ContextFreeLazyValue extends LazyValue
    {

        Value evalType(Context.Type type);

        @Override
        default Value evalValue(Context c, Context.Type type)
        {
            return evalType(type);
        }
    }


    /**
     * Il valore di una lambda scritta sul posto, {@code fn(x) -> ...}.
     *
     * <p>Serve a distinguerla, mentre si costruisce l'albero, da un'espressione con {@code _}: i
     * cicli e le trasformazioni accettano tutte e due le forme, e devono sapere quale hanno davanti
     * senza valutare niente - valutare per scoprirlo vorrebbe dire eseguire il corpo una volta di
     * troppo, e un corpo che stampa lo si vedrebbe.
     *
     * <p>La distinzione e' statica: la fa il token, quindi non costa niente a runtime.
     */
    final class Lambda implements LazyValue
    {
        private final LazyValue delegate;

        public Lambda(LazyValue delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Value evalValue(Context c, Context.Type type)
        {
            return delegate.evalValue(c, type);
        }
    }

    class Constant implements ContextFreeLazyValue
    {
        Value result;

        public Constant(Value value)
        {
            result = value;
        }

        public Value get()
        {
            return result;
        }

        @Override
        public Value evalType(Context.Type type)
        {

            return result.fromConstant();
        }

        @Override
        public Value evalValue(Context c, Context.Type type)
        {
            return result.fromConstant();
        }
    }
}
