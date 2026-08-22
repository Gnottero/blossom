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
     * The value of a lambda written in place, {@code fn(x) -> ...}.
     *
     * <p>It tells one apart, while the tree is being built, from an expression using {@code _}:
     * loops and transformations accept both forms, and have to know which one they face without
     * evaluating anything, since evaluating to find out would run the body one time too many, and a
     * body that prints would show it.
     *
     * <p>The distinction is static: the token makes it, so it costs nothing at run time.
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
