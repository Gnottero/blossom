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
     * Il codice hash del valore che il testo denota, se ne denota uno.
     *
     * <p>{@code '1' == 1} risponde vero - lo dice la guida, e' l'indulgenza voluta - ma il codice
     * hash era quello del testo, quindi in una mappa {@code 1} e {@code '1'} finivano in due
     * caselle diverse pur essendo lo stesso valore: uguali con hash diversi e' il contratto di
     * HashMap rotto, e la chiave scritta in un modo non trovava quella scritta nell'altro.
     *
     * <p>Si paga il confronto di un carattere sulle chiavi che numeri non sono, che sono la maggior
     * parte: la conversione si tenta solo quando il testo comincia come un numero.
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
                // solo se il testo e' esattamente come il numero si riscrive: '1.0' e 1 restano
                // due valori diversi, e devono restare due chiavi diverse
                if (str.equals(asNumber.getString()))
                {
                    return asNumber.hashCode();
                }
            }
            catch (NumberFormatException ignored)
            {
                // non era un numero: vale il codice hash del testo, qui sotto
            }
        }
        return super.hashCode();
    }

    private static boolean looksNumeric(char first)
    {
        return (first >= '0' && first <= '9') || first == '-' || first == '+' || first == '.';
    }

}
