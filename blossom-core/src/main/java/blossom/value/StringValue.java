package blossom.value;


import org.jspecify.annotations.Nullable;

public class StringValue extends Value
{
    public static Value EMPTY = StringValue.of("");

    private final String str;

    @Override
    public String getString()
    {
        return str;
    }

    @Override
    public boolean getBoolean()
    {
        return str != null && !str.isEmpty();
    }

    @Override
    public Value clone()
    {
        return new StringValue(str);
    }

    public StringValue(String str)
    {
        this.str = str;
    }

    public static Value of(@Nullable String value)
    {
        return value == null ? Value.NULL : new StringValue(value);
    }

    @Override
    public String getTypeString()
    {
        return "string";
    }

    /**
     * The hash code of the value the text denotes, if it denotes one.
     *
     * <p>{@code '1' == 1} answers true, as the guide says and as the leniency intends, but the hash
     * code used to be the text's, so in a map {@code 1} and {@code '1'} landed in two different
     * buckets while being the same value: equal with different hashes breaks the HashMap contract,
     * and the key written one way did not find the one written the other.
     *
     * <p>The price is one character comparison on keys that are not numbers, which are most of them:
     * the conversion is attempted only when the text starts like a number.
     */
    @Override
    public int hashCode()
    {
        if ("true".equals(str))
        {
            return Long.hashCode(1L);
        }
        if ("false".equals(str))
        {
            return Long.hashCode(0L);
        }
        if (str != null && !str.isEmpty() && looksNumeric(str.charAt(0)))
        {
            try
            {
                NumericValue asNumber = new NumericValue(str);
                // only when the text is exactly how the number is written back: '1.0' and 1 stay
                // two different values, and they have to stay two different keys
                if (str.equals(asNumber.getString()))
                {
                    return asNumber.hashCode();
                }
            }
            catch (NumberFormatException ignored)
            {
                // it was not a number: the text hash code below applies
            }
        }
        return super.hashCode();
    }

    private static boolean looksNumeric(char first)
    {
        return (first >= '0' && first <= '9') || first == '-' || first == '+' || first == '.';
    }

}
