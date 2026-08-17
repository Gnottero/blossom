package blossom.value;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Blossom;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


import org.jspecify.annotations.Nullable;

public abstract class Value implements Comparable<Value>, Cloneable, blossom.interpreter.LazyValue
{
    /**
     * Un valore è anche il nodo che lo produce: valutarlo restituisce sé stesso.
     *
     * <p>Serve a togliere un'allocazione dai punti più caldi dell'interprete. Ogni funzione - built-in
     * o definita da chi scrive - restituiva il proprio risultato avvolto in una lambda
     * {@code (c, t) -> risultato} che il chiamante apriva subito dopo, e ogni argomento veniva messo
     * nel frame nello stesso modo. Un valore che sa già rispondere a {@code evalValue} rende quelle
     * lambda inutili.
     */
    @Override
    public Value evalValue(blossom.interpreter.Context c, blossom.interpreter.Context.Type type)
    {
        return this;
    }

    public static final NumericValue FALSE = BooleanValue.FALSE;
    public static final NumericValue TRUE = BooleanValue.TRUE;
    public static final NumericValue ZERO = new NumericValue(0);
    public static final NumericValue ONE = new NumericValue(1);

    public static final NullValue NULL = NullValue.NULL;
    public static final UndefValue UNDEF = UndefValue.UNDEF;
    public static final UndefValue EOL = UndefValue.EOL;

    public abstract String getString();

    public String getPrettyString()
    {
        return getString();
    }


    public abstract boolean getBoolean();

    public Value add(Value o)
    {
        if (o instanceof final Concatenable concatenable)
        {
            return concatenable.concatenatedWith(this);
        }
        String leftStr = this.getString();
        String rightStr = o.getString();
        return new StringValue(leftStr + rightStr);
    }

    public Value subtract(Value v)
    {
        return new StringValue(this.getString().replace(v.getString(), ""));
    }

    public Value multiply(Value v)
    {
        return v instanceof NumericValue || v instanceof ListValue
                ? v.multiply(this)
                : new StringValue(this.getString() + "." + v.getString());
    }

    public Value divide(Value v)
    {
        if (v instanceof NumericValue number)
        {
            String lstr = getString();
            return new StringValue(lstr.substring(0, (int) (lstr.length() / number.getDouble())));
        }
        return new StringValue(getString() + "/" + v.getString());
    }

    @Override
    public int compareTo(Value o)
    {
        return o instanceof NumericValue || o instanceof ListValue || o instanceof ThreadValue
                ? -o.compareTo(this)
                : getString().compareTo(o.getString());
    }

    @Override // for hashmap key access, and == operator
    public boolean equals(Object o)
    {
        if (o instanceof Value v)
        {
            return this.compareTo(v) == 0;
        }
        return false;
    }

    public Value in(Value value1)
    {
        Pattern p;
        try
        {
            p = Pattern.compile(value1.getString());
        }
        catch (PatternSyntaxException pse)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", "Incorrect matching pattern: " + pse.getMessage());
        }
        Matcher m = p.matcher(this.getString());
        if (!m.find())
        {
            return Value.NULL;
        }
        int gc = m.groupCount();
        if (gc == 0)
        {
            return new StringValue(m.group());
        }
        if (gc == 1)
        {
            return StringValue.of(m.group(1));
        }
        List<Value> groups = new ArrayList<>(gc);
        for (int i = 1; i <= gc; i++)
        {
            groups.add(StringValue.of(m.group(i)));
        }
        return ListValue.wrap(groups);
    }

    public int length()
    {
        return getString().length();
    }

    public Value slice(long fromDesc, @Nullable Long toDesc)
    {
        String value = this.getString();
        int size = value.length();
        int from = ListValue.normalizeIndex(fromDesc, size);
        if (toDesc == null)
        {
            return new StringValue(value.substring(from));
        }
        int to = ListValue.normalizeIndex(toDesc, size + 1);
        if (from > to)
        {
            return StringValue.EMPTY;
        }
        return new StringValue(value.substring(from, to));
    }

    public Value split(@Nullable Value delimiter)
    {
        if (delimiter == null)
        {
            delimiter = StringValue.EMPTY;
        }
        try
        {
            return ListValue.wrap(Arrays.stream(getString().split(delimiter.getString())).map(StringValue::new));
        }
        catch (PatternSyntaxException pse)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", "Incorrect pattern for 'split': " + pse.getMessage());
        }
    }

    public double readDoubleNumber()
    {
        String s = getString();
        try
        {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException e)
        {
            return Double.NaN;
        }
    }

    public long readInteger()
    {
        return (long) readDoubleNumber();
    }

    public String getTypeString()
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", "How did you get here? Cannot get type of an intenal type.");
    }

    @Override
    public int hashCode()
    {
        String stringVal = getString();
        return stringVal.isEmpty() ? 0 : ("s" + stringVal).hashCode();
    }

    public Value deepcopy()
    {
        try
        {
            return (Value) this.clone();
        }
        catch (CloneNotSupportedException e)
        {
            // should never happen
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", "Cannot make a copy of value: " + this);
        }
    }


    public JsonElement toJson()
    {
        return new JsonPrimitive(getString());
    }

    public boolean isNull()
    {
        return false;
    }

    /**
     * @return retrieves useful in-run value of an optimized code-base value.
     * For immutable values (most of them) it can return itself,
     * but for mutables, it needs to be its copy or deep copy.
     */
    public Value fromConstant()
    {
        return this;
    }
}
