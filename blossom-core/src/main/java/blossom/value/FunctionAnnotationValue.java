package blossom.value;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;

public class FunctionAnnotationValue extends Value
{
    public enum Type
    {
        GLOBAL, VARARG
    }

    public Type type;

    public FunctionAnnotationValue(Value variable, Type type)
    {
        if (variable.boundVariable == null)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "You can only borrow variables from the outer scope");
        }
        this.boundVariable = variable.boundVariable;
        this.type = type;
    }

    @Override
    public String getString()
    {
        return boundVariable;
    }

    @Override
    public boolean getBoolean()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        throw new UnsupportedOperationException("Global value cannot be used as a map key");
    }

}
