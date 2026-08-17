package blossom.value;

import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Un parametro con il tipo dichiarato: quello che {@code nome: tipo} produce dentro una firma.
 *
 * <p>Vive solo il tempo di costruire la firma. Non è un valore che uno script possa vedere: appena
 * la firma è pronta, resta il nome del parametro da una parte e il tipo canonico dall'altra.
 *
 * <p>Il tipo viene validato qui, cioè alla definizione della funzione e non alla prima chiamata:
 * {@code fn f(a: nmu)} è un errore di battitura, e va detto subito invece che il giorno in cui
 * qualcuno passa di lì.
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

    /** Il nome del parametro dichiarato. */
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
