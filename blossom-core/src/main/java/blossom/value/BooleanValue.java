package blossom.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanValue extends NumericValue
{
    public static final BooleanValue FALSE = new BooleanValue(false);
    public static final BooleanValue TRUE = new BooleanValue(true);

    boolean boolValue;

    private BooleanValue(boolean boolval)
    {
        super(boolval ? 1L : 0L);
        boolValue = boolval;
    }

    public static BooleanValue of(boolean value)
    {
        return value ? TRUE : FALSE;
    }

    @Override
    public String getString()
    {
        return boolValue ? "true" : "false";
    }

    @Override
    public String getPrettyString()
    {
        return getString();
    }

    @Override
    public String getTypeString()
    {
        return "bool";
    }

    @Override
    public Value clone()
    {
        return new BooleanValue(boolValue);
    }

    @Override
    public int hashCode()
    {
        // a boolean is the number 1 or 0, and 'true == 1' answers true because this class extends
        // NumericValue, so it has to carry their hash code too, or as a map key 'true' and 1 would
        // land in two different buckets while being the same value
        return Long.hashCode(getLong());
    }


    @Override
    public JsonElement toJson()
    {
        return new JsonPrimitive(boolValue);
    }

    @Override
    public boolean isInteger()
    {
        return true;
    }
}
