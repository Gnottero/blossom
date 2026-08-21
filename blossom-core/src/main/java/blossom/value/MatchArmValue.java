package blossom.value;

import blossom.interpreter.LazyValue;

/**
 * Un braccio di {@code match}: la forma da riconoscere e il codice da eseguire se combacia.
 *
 * <p>Il corpo resta pigro, che è tutto il punto: in un {@code match} di dieci bracci ne viene
 * eseguito uno, e valutare gli altri nove per costruire la lista sarebbe sbagliato oltre che
 * inutile.
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
