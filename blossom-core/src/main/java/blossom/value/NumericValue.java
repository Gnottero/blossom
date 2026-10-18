package blossom.value;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;


import static java.lang.Math.abs;
import static java.lang.Math.signum;

public class NumericValue extends Value
{
    private final double value;
    /**
     * The integer value, when the number is an integer.
     *
     * <p>Two fields rather than a single {@code Long}: with a {@code Long}, every integer outside
     * the JVM's cache (-128..127) cost <em>two</em> allocations instead of one, the second just to
     * be able to say "I'm an integer". In an interpreter that creates a number for every arithmetic
     * operation, that second allocation is pure pressure on the garbage collector.
     */
    private long longValue;
    private boolean isLong;
    private static final double epsilon = abs(32 * ((7 * 0.1) * 10 - 7));
    private static final MathContext displayRounding = new MathContext(12, RoundingMode.HALF_EVEN);

    public static NumericValue asNumber(Value v1, String id)
    {
        if (v1 instanceof NumericValue nv)
        {
            return nv;
        }
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "number", "Argument " + id + " has to be of a numeric type");
    }

    public static NumericValue asNumber(Value v1)
    {
        if (v1 instanceof NumericValue nv)
        {
            return nv;
        }
        throw new TypeMismatchError(SourcePosition.UNKNOWN,"Operand has to be of a numeric type");
    }

    /**
     * The integer number {@code value}.
     *
     * <p>There used to be a cache of shared instances for small integers here, removed because
     * values then carried a mutable field with the name of the variable they were bound to, and
     * shared instances would have let multiple threads overwrite it (see DECISIONI.md D-071). That
     * field is gone now, the name lives on the node that reads the variable instead, see {@link
     * VariableReferenceValue} and D-093, so the obstacle is gone and the cache can be reconsidered
     * on its own merits, no longer as a correctness issue.
     */
    public static NumericValue of(long value)
    {
        return new NumericValue(value);
    }

    public static <T extends Number> Value of(T value)
    {
        if (value == null)
        {
            return Value.NULL;
        }
        if (value.doubleValue() == value.longValue())
        {
            return of(value.longValue());
        }
        if (value instanceof Float)
        {
            return new NumericValue(0.000_001D * Math.round(1_000_000.0D * value.doubleValue()));
        }
        return new NumericValue(value.doubleValue());
    }


    @Override
    public String getString()
    {
        if (isLong)
        {
            return Long.toString(getLong());
        }
        try
        {
            if (Double.isInfinite(value))
            {
                return (value > 0) ? "INFINITY" : "-INFINITY";
            }
            if (Double.isNaN(value))
            {
                return "NaN";
            }
            if (abs(value) < epsilon)
            {
                return (signum(value) < 0) ? "-0" : "0"; // zero rounding fails with big decimals
            }
            // doubles have 16 digit precision, 12 is plenty to display
            return BigDecimal.valueOf(value).round(displayRounding).stripTrailingZeros().toPlainString();
        }
        catch (NumberFormatException exc)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,"Incorrect number format for " + value);
        }
    }

    @Override
    public String getPrettyString()
    {
        return isLong || getDouble() == getLong()
                ? Long.toString(getLong())
                : String.format(Locale.ROOT, "%.1f..", getDouble());
    }

    @Override
    public boolean getBoolean()
    {
        return abs(value) > epsilon;
    }

    public double getDouble()
    {
        return value;
    }

    public float getFloat()
    {
        return (float) value;
    }

    private static long floor(double v)
    {
        long invValue = (long) v;
        return v < invValue && invValue != Long.MIN_VALUE ? invValue - 1 : invValue;
    }

    public long getLong()
    {
        return isLong ? longValue : floor(value + epsilon);
    }

    @Override
    public Value add(Value v)
    {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof NumericValue nv)
        {
            return isLong && nv.isLong ? of(longValue + nv.longValue) : new NumericValue(value + nv.value);
        }
        return super.add(v);
    }

    @Override
    public Value subtract(Value v)
    {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof NumericValue nv)
        {
            return isLong && nv.isLong ? of(longValue - nv.longValue) : new NumericValue(value - nv.value);
        }
        return super.subtract(v);
    }

    @Override
    public Value multiply(Value v)
    {
        if (v instanceof NumericValue nv)
        {
            return isLong && nv.isLong ? of(longValue * nv.longValue) : new NumericValue(value * nv.value);
        }
        return v instanceof ListValue ? v.multiply(this) : new StringValue(StringUtils.repeat(v.getString(), (int) getLong()));
    }

    @Override
    public Value divide(Value v)
    {
        return v instanceof NumericValue nv ? new NumericValue(getDouble() / nv.getDouble()) : super.divide(v);
    }

    @Override
    public Value clone()
    {
        return isLong ? new NumericValue(longValue) : new NumericValue(value);
    }

    @Override
    public int compareTo(Value o)
    {
        if (o.isNull())
        {
            return -o.compareTo(this);
        }
        if (o instanceof NumericValue no)
        {
            return isLong && no.isLong ? Long.compare(longValue, no.longValue) : Double.compare(value, no.value);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Value otherValue)
        {
            if (otherValue.isNull())
            {
                return o.equals(this);
            }
            if (o instanceof NumericValue no)
            {
                if (isLong && no.isLong)
                {
                    return longValue == no.longValue;
                }
                if (Double.isNaN(this.value) || Double.isNaN(no.value))
                {
                    return false;
                }
                return !this.subtract(no).getBoolean();
            }
            return super.equals(o);
        }
        return false;
    }

    public NumericValue(double value)
    {
        this.value = value;
    }

    public NumericValue(String value)
    {
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.stripTrailingZeros().scale() <= 0)
        {
            try
            {
                longValue = decimal.longValueExact();
                isLong = true;
            }
            catch (ArithmeticException ignored)
            {
            }
        }
        this.value = decimal.doubleValue();
    }

    public NumericValue(long value)
    {
        this.longValue = value;
        this.isLong = true;
        this.value = (double) value;
    }

    @Override
    public int length()
    {
        return Long.toString(getLong()).length();
    }

    @Override
    public double readDoubleNumber()
    {
        return value;
    }

    @Override
    public long readInteger()
    {
        return getLong();
    }

    @Override
    public String getTypeString()
    {
        return "number";
    }

    @Override
    public int hashCode()
    {
        // is sufficiently close to the integer value
        return isLong || Math.abs(Math.floor(value + 0.5D) - value) < epsilon ? Long.hashCode(getLong()) : Double.hashCode(value);
    }


    public int getInt()
    {
        return (int) getLong();
    }


    @Override
    public JsonElement toJson()
    {
        if (isLong)
        {
            return new JsonPrimitive(longValue);
        }
        if (isInteger())
        {
            return new JsonPrimitive(getLong());
        }
        double d = getDouble();
        if (Double.isNaN(d) || Double.isInfinite(d))
        {
            // json has neither NaN nor infinity, and gson would write them anyway, unquoted: out
            // would come text no serious reader parses back. Better to say so here, where it is
            // still known which number it was
            throw new blossom.exception.ThrowStatement(
                    "json has no way to write " + getString() + ": only finite numbers can be encoded",
                    blossom.exception.Throwables.JSON_ERROR);
        }
        return new JsonPrimitive(d);
    }

    public NumericValue opposite()
    {
        return isLong ? of(-longValue) : new NumericValue(-value);
    }

    public boolean isInteger()
    {
        return isLong || getDouble() == getLong();
    }

    public Value mod(NumericValue n2)
    {
        if (this.isLong && n2.isLong)
        {
            return of(Math.floorMod(longValue, n2.longValue));
        }
        double x = value;
        double y = n2.value;
        if (y == 0)
        {
            throw new ArithmeticException("Division by zero");
        }
        return new NumericValue(x - Math.floor(x / y) * y);
    }
}
