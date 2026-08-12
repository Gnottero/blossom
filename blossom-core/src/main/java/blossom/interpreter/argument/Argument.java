package blossom.interpreter.argument;

public abstract class Argument
{
    public final int offset;

    protected Argument(int offset)
    {
        this.offset = offset;
    }
}
