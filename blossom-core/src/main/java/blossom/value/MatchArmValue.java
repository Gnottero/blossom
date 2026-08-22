package blossom.value;

import blossom.interpreter.LazyValue;

/**
 * One arm of a {@code match}: the pattern to recognize and the code to run when it matches.
 *
 * <p>The body stays lazy, which is the whole point: a {@code match} with ten arms only runs one of
 * them, and evaluating the other nine just to build the list would be both wrong and wasteful.
 */
public final class MatchArmValue extends FrameworkValue
{
    private final Value pattern;
    private final LazyValue body;

    public MatchArmValue(Value pattern, LazyValue body)
    {
        this.pattern = pattern;
        this.body = body;
    }

    public Value pattern()
    {
        return pattern;
    }

    public LazyValue body()
    {
        return body;
    }

    @Override
    public String getString()
    {
        return pattern.getString() + " -> ...";
    }

    @Override
    public String getTypeString()
    {
        return "match_arm";
    }
}
