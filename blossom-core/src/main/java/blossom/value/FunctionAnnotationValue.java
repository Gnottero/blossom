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

    private final String name;

    public FunctionAnnotationValue(Value variable, Type type)
    {
        if (!(variable instanceof final VariableReferenceValue reference))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "function", "You can only borrow variables from the outer scope");
        }
        this.name = reference.name();
        this.type = type;
    }

    /** The name of the annotated variable: 'outer(x)' or '... rest' in a signature. */
    public String name()
    {
        return name;
    }

    @Override
    public String getString()
    {
        return name;
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
