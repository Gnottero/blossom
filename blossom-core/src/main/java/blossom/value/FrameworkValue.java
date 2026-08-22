package blossom.value;


/**
 * The base for values that exist only for the interpreter and not for whoever writes the script:
 * function signatures, unpacked arguments, annotations.
 *
 * <p>These are not language values: if one of them ends up where a real value is expected, the
 * error must be reported rather than hidden behind a plausible conversion.
 */
public abstract class FrameworkValue extends Value
{
    @Override
    public String getString()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as a value");
    }

    @Override
    public boolean getBoolean()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as a value");
    }

    @Override
    public Value clone()
    {
        throw new UnsupportedOperationException("Internal language component cannot be copied");
    }

    @Override
    public int hashCode()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as map key");
    }

}
