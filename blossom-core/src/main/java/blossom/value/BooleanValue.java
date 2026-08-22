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
        // un booleano e' il numero 1 o 0 - 'true == 1' risponde vero perche' questa classe estende
        // NumericValue - quindi deve avere anche il loro stesso codice hash, o come chiave di una
        // mappa 'true' e 1 finirebbero in due caselle diverse pur essendo lo stesso valore
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
