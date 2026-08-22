package blossom.value;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * A parameter with a declared type: what {@code name: type} produces inside a signature.
 *
 * <p>It lives only for the time it takes to build the signature. It's not a value a script can
 * ever see: as soon as the signature is ready, what's left is the parameter name on one side and
 * the canonical type on the other.
 *
 * <p>The type is validated here, i.e. when the function is defined and not on its first call:
 * {@code fn f(a: nmu)} is a typo, and it should be reported right away instead of on the day
 * someone happens to call it.
 */
public final class TypedParameterValue extends Value
{
    private final String parameter;
    private final String canonicalType;

    public TypedParameterValue(String parameter, String declaredType)
    {
        String canonical = ValueTypes.canonical(declaredType);
        if (canonical == null)
        {
            throw new ParseError(SourcePosition.UNKNOWN, "Unknown type '" + declaredType
                    + "' in the signature: the types are " + String.join(", ", ValueTypes.names()) + ".",
                    Suggestions.closestTo(declaredType, ValueTypes.names()));
        }
        this.parameter = parameter;
        this.canonicalType = canonical;
    }

    /** The name of the declared parameter. */
    public String parameter()
    {
        return parameter;
    }

    public String canonicalType()
    {
        return canonicalType;
    }

    @Override
    public String getString()
    {
        return parameter + ": " + canonicalType;
    }

    @Override
    public boolean getBoolean()
    {
        return false;
    }

    @Override
    public String getTypeString()
    {
        return "parameter";
    }

    @Override
    public int hashCode()
    {
        throw new UnsupportedOperationException("a typed parameter is not a value");
    }
}
