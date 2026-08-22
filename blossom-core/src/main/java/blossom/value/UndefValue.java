package blossom.value;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import com.google.gson.JsonElement;

public class UndefValue extends NullValue
{
    public static final UndefValue UNDEF = new UndefValue();
    public static final UndefValue EOL = new UndefValue();

    /** The name of the never-initialized variable, for the error message. */
    private final String name;

    public UndefValue()
    {
        this(null);
    }

    private UndefValue(String name)
    {
        this.name = name;
    }

    /** The placeholder for variable {@code name}, which cannot be read under 'strict'. */
    public static UndefValue named(String name)
    {
        return new UndefValue(name);
    }

    private RuntimeException getError()
    {
        return new BlossomRuntimeError(SourcePosition.UNKNOWN, "value",
                "variable " + (name == null ? "" : name + " ") + "was used before initialization under 'strict' app config");
    }

    @Override
    public String getString()
    {
        throw getError();
    }

    @Override
    public String getPrettyString()
    {
        return "undefined";
    }

    @Override
    public boolean getBoolean()
    {
        throw getError();
    }

    @Override
    public Value clone()
    {
        return new UndefValue(name);
    }

    @Override
    public boolean equals(Object o)
    {
        throw getError();
    }

    @Override
    public Value slice(long fromDesc, Long toDesc)
    {
        throw getError();
    }

    @Override
    public NumericValue opposite()
    {
        throw getError();
    }

    @Override
    public int length()
    {
        throw getError();
    }

    @Override
    public int compareTo(Value o)
    {
        throw getError();
    }

    @Override
    public Value in(Value value)
    {
        throw getError();
    }

    @Override
    public String getTypeString()
    {
        return "undef";
    }

    @Override
    public int hashCode()
    {
        throw getError();
    }


    @Override
    public Value split(Value delimiter)
    {
        throw getError();
    }

    @Override
    public JsonElement toJson()
    {
        throw getError();
    }

    @Override
    public boolean isNull()
    {
        throw getError();
    }

    @Override
    public Value add(Value v)
    {
        throw getError();
    }

    @Override
    public Value subtract(Value v)
    {
        throw getError();
    }

    @Override
    public Value multiply(Value v)
    {
        throw getError();
    }

    @Override
    public Value divide(Value v)
    {
        throw getError();
    }

    @Override
    public double readDoubleNumber()
    {
        throw getError();
    }

    @Override
    public long readInteger()
    {
        throw getError();
    }

}
