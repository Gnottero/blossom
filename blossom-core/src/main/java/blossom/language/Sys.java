package blossom.language;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.ArityError;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.utils.PerlinNoiseSampler;
import blossom.utils.SimplexNoiseSampler;
import blossom.value.AbstractListValue;
import blossom.value.BooleanValue;
import blossom.value.FunctionValue;
import blossom.value.LazyListValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NullValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Sys
{
    public static final Random randomizer = new Random();
    /** %[argument_index$][flags][width][.precision][t]conversion */
    private static final Pattern formatPattern = Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

    /**
     * The number a numeric placeholder wants, or the error saying what arrived in its place.
     *
     * <p>It used to go through {@code readInteger}, which answered zero for anything that was not a
     * number: {@code str('%d', 'x')} gave {@code '0'}, a wrong result without a line of warning. A
     * string written like a number still passes, because that is the same leniency the arithmetic
     * operators have.
     */
    private static Number numberFor(Value value, String specifier)
    {
        if (value instanceof final blossom.value.NumericValue nv)
        {
            return nv.getDouble();
        }
        if (value instanceof blossom.value.StringValue)
        {
            try
            {
                return Double.parseDouble(value.getString().trim());
            }
            catch (NumberFormatException ignored)
            {
                // falls into the error below, with the text that was not a number
            }
        }
        throw new TypeMismatchError(SourcePosition.UNKNOWN, "'" + specifier + "' wants a number, but it was given "
                + value.getTypeString() + (value.isNull() ? "" : " (" + value.getString() + ")") + ".");
    }

    private static final java.util.TimeZone UTC = java.util.TimeZone.getTimeZone("UTC");

    /**
     * The integer for {@code %d}, {@code %o} and {@code %x}: read as a long and not through a
     * double, which past 2^53 drops the low digits, and rounded down like every other place where
     * the language turns a number into an integer.
     */
    private static long longFor(Value value, String specifier)
    {
        if (value instanceof final blossom.value.NumericValue nv)
        {
            return nv.getLong();
        }
        if (value instanceof blossom.value.StringValue)
        {
            try
            {
                return Long.parseLong(value.getString().trim());
            }
            catch (NumberFormatException ignored)
            {
                // not an integer written out: maybe a decimal one, read like any other number
            }
        }
        return new blossom.value.NumericValue(numberFor(value, specifier).doubleValue()).getLong();
    }

    public static void apply(Expression expression)
    {
        expression.addUnaryFunction("hash_code", v -> new NumericValue(v.hashCode()));

        expression.addImpureUnaryFunction("copy", Value::deepcopy);

        expression.addTypedContextFunction("bool", 1, Context.BOOLEAN, (c, t, lv) ->
        {
            Value v = lv.get(0);
            if (v instanceof StringValue)
            {
                String str = v.getString().toLowerCase(Locale.ROOT);
                if ("false".equals(str) || "null".equals(str))
                {
                    return Value.FALSE;
                }
            }
            return BooleanValue.of(v.getBoolean());
        });

        expression.addUnaryFunction("number", v ->
        {
            if (v instanceof final NumericValue num)
            {
                return num.clone();
            }
            if (v instanceof ListValue || v instanceof MapValue)
            {
                return new NumericValue(v.length());
            }
            try
            {
                return new NumericValue(v.getString());
            }
            catch (NumberFormatException format)
            {
                return Value.NULL;
            }
        });

        /*
         * The pieces of an interpolated string, already split by the tokenizer.
         *
         * Never written by hand: "a${b}c" is its only form. A StringBuilder instead of n binary
         * concatenations, because with four or five placeholders the difference shows.
         */
        expression.addFunction("concat", lv ->
        {
            StringBuilder text = new StringBuilder();
            for (Value piece : lv)
            {
                text.append(piece.getString());
            }
            return StringValue.of(text.toString());
        });

        expression.addFunction("str", lv ->
        {
            if (lv.isEmpty())
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'str' requires at least one argument");
            }
            String format = lv.get(0).getString();
            if (lv.size() == 1)
            {
                return new StringValue(format);
            }
            int argIndex = 1;
            if (lv.get(1) instanceof final ListValue list && lv.size() == 2)
            {
                lv = list.getItems();
                argIndex = 0;
            }
            List<Object> args = new ArrayList<>();
            Matcher m = formatPattern.matcher(format);

            for (int i = 0, len = format.length(); i < len; )
            {
                if (m.find(i))
                {
                    // Anything between the start of the string and the beginning
                    // of the format specifier is either fixed text or contains
                    // an invalid format string.
                    // we skip it and let the String.format fail
                    char fmt = m.group(6).toLowerCase(Locale.ROOT).charAt(0);
                    if (fmt == 's')
                    {
                        if (argIndex >= lv.size())
                        {
                            throw new ArityError(SourcePosition.UNKNOWN,"Not enough arguments for " + m.group(0));
                        }
                        args.add(lv.get(argIndex).getString());
                        argIndex++;
                    }
                    else if (fmt == 'd' || fmt == 'o' || fmt == 'x')
                    {
                        if (argIndex >= lv.size())
                        {
                            throw new ArityError(SourcePosition.UNKNOWN,"Not enough arguments for " + m.group(0));
                        }
                        args.add(longFor(lv.get(argIndex), m.group(0)));
                        argIndex++;
                    }
                    else if (fmt == 'a' || fmt == 'e' || fmt == 'f' || fmt == 'g')
                    {
                        if (argIndex >= lv.size())
                        {
                            throw new ArityError(SourcePosition.UNKNOWN,"Not enough arguments for " + m.group(0));
                        }
                        args.add(numberFor(lv.get(argIndex), m.group(0)).doubleValue());
                        argIndex++;
                    }
                    else if (fmt == 'b')
                    {
                        if (argIndex >= lv.size())
                        {
                            throw new ArityError(SourcePosition.UNKNOWN,"Not enough arguments for " + m.group(0));
                        }
                        args.add(lv.get(argIndex).getBoolean());
                        argIndex++;
                    }
                    else if (fmt != '%')
                    {
                        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "system", "Format not supported: " + m.group(6));
                    }

                    i = m.end();
                }
                else
                {
                    // No more valid format specifiers.  Check for possible invalid
                    // format specifiers.
                    // we skip it and let the String.format fail
                    break;
                }
            }
            if (argIndex < lv.size())
            {
                throw new ArityError(SourcePosition.UNKNOWN, "'str' was given " + (lv.size() - argIndex)
                        + " argument(s) more than the format uses: '" + format + "' asks for "
                        + args.size() + ".");
            }
            try
            {
                return new StringValue(String.format(Locale.ROOT, format, args.toArray()));
            }
            catch (IllegalFormatException ife)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "system", "Illegal string format: " + ife.getMessage());
            }
        });

        expression.addUnaryFunction("lower", v -> new StringValue(v.getString().toLowerCase(Locale.ROOT)));

        expression.addUnaryFunction("upper", v -> new StringValue(v.getString().toUpperCase(Locale.ROOT)));

        expression.addUnaryFunction("title", v -> new StringValue(titleCase(v.getString())));

        /*
         * replace(text, regex, with) replaces every occurrence
         * replace(text, regex, with, count) replaces the first 'count' ones
         *
         * They were two functions, and the second said "first" to mean "one". A trailing number says
         * how many, and "one" is the case where that number is 1.
         */
        expression.addFunction("replace", lv ->
        {
            if (lv.size() < 2 || lv.size() > 4)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "system",
                        "'replace' expects the string, the pattern, an optional replacement and an optional count");
            }
            String data = lv.get(0).getString();
            String regex = lv.get(1).getString();
            String replacement = lv.size() > 2 ? lv.get(2).getString() : "";
            long limit = lv.size() > 3 ? NumericValue.asNumber(lv.get(3)).getLong() : -1;
            try
            {
                if (limit < 0)
                {
                    return new StringValue(data.replaceAll(regex, replacement));
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(data);
                StringBuilder out = new StringBuilder();
                for (long done = 0; done < limit && matcher.find(); done++)
                {
                    matcher.appendReplacement(out, replacement);
                }
                matcher.appendTail(out);
                return new StringValue(out.toString());
            }
            catch (PatternSyntaxException pse)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "system", "Incorrect pattern for replace: " + pse.getMessage());
            }
        });

        expression.addUnaryFunction("type", v -> new StringValue(v.getTypeString()));
        expression.addUnaryFunction("length", v ->
        {
            // 'size' answers about what has a size. On everything else it used to answer anyway,
            // counting the digits of the number or the characters of its representation, which is a
            // plausible and wrong number: the worst way to fail
            if (v instanceof LazyListValue)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'size' cannot measure a lazy list: it produces its elements as it goes and"
                                + " may not end. Materialise it first, e.g. list.slice(l, 0, n).");
            }
            if (!(v instanceof AbstractListValue || v instanceof MapValue || v instanceof StringValue
                    || v instanceof NullValue))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'size' wants a list, a map or a string, but got a " + v.getTypeString() + ".");
            }
            return new NumericValue(v.length());
        });
        expression.addContextFunction("rand", -1, (c, t, lv) ->
        {
            int argsize = lv.size();
            Random randomizer = Sys.randomizer;
            if (argsize != 1 && argsize != 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'rand' takes one (range) or two arguments (range and seed)");
            }
            if (argsize == 2)
            {
                randomizer = c.host.getRandom(NumericValue.asNumber(lv.get(1)).getLong());
            }
            Value argument = lv.get(0);
            if (argument instanceof final ListValue listValue)
            {
                List<Value> list = listValue.getItems();
                return list.get(randomizer.nextInt(list.size()));
            }
            double value = NumericValue.asNumber(argument).getDouble() * randomizer.nextDouble();
            return t == Context.BOOLEAN ? BooleanValue.of(value >= 1.0D) : new NumericValue(value);
        });
        expression.addContextFunction("reset_seed", 1, (c, t, lv) -> {
            boolean gotIt = c.host.resetRandom(NumericValue.asNumber(lv.get(0)).getLong());
            return BooleanValue.of(gotIt);
        });

        expression.addFunction("perlin", lv ->
        {
            PerlinNoiseSampler sampler;
            Value x;
            Value y;
            Value z;

            if (lv.size() >= 4)
            {
                x = lv.get(0);
                y = lv.get(1);
                z = lv.get(2);
                sampler = PerlinNoiseSampler.getPerlin(NumericValue.asNumber(lv.get(3)).getLong());
            }
            else
            {
                sampler = PerlinNoiseSampler.instance;
                y = Value.NULL;
                z = Value.NULL;
                if (lv.isEmpty())
                {
                    throw new ArityError(SourcePosition.UNKNOWN,"'perlin' requires at least one dimension to sample from");
                }
                x = NumericValue.asNumber(lv.get(0));
                if (lv.size() > 1)
                {
                    y = NumericValue.asNumber(lv.get(1));
                    if (lv.size() > 2)
                    {
                        z = NumericValue.asNumber(lv.get(2));
                    }
                }
            }

            double result;

            if (z.isNull())
            {
                result = y.isNull()
                        ? sampler.sample1d(NumericValue.asNumber(x).getDouble())
                        : sampler.sample2d(NumericValue.asNumber(x).getDouble(), NumericValue.asNumber(y).getDouble());
            }
            else
            {
                result = sampler.sample3d(
                        NumericValue.asNumber(x).getDouble(),
                        NumericValue.asNumber(y).getDouble(),
                        NumericValue.asNumber(z).getDouble());
            }
            return new NumericValue(result);
        });

        expression.addFunction("simplex", lv ->
        {
            SimplexNoiseSampler sampler;
            Value x;
            Value y;
            Value z;

            if (lv.size() >= 4)
            {
                x = lv.get(0);
                y = lv.get(1);
                z = lv.get(2);
                sampler = SimplexNoiseSampler.getSimplex(NumericValue.asNumber(lv.get(3)).getLong());
            }
            else
            {
                sampler = SimplexNoiseSampler.instance;
                z = Value.NULL;
                if (lv.size() < 2)
                {
                    throw new ArityError(SourcePosition.UNKNOWN,"'simplex' requires at least two dimensions to sample from");
                }
                x = NumericValue.asNumber(lv.get(0));
                y = NumericValue.asNumber(lv.get(1));
                if (lv.size() > 2)
                {
                    z = NumericValue.asNumber(lv.get(2));
                }
            }
            double result;

            if (z.isNull())
            {
                result = sampler.sample2d(NumericValue.asNumber(x).getDouble(), NumericValue.asNumber(y).getDouble());
            }
            else
            {
                result = sampler.sample3d(
                        NumericValue.asNumber(x).getDouble(),
                        NumericValue.asNumber(y).getDouble(),
                        NumericValue.asNumber(z).getDouble());
            }
            return new NumericValue(result);
        });

        expression.addUnaryFunction("print", v ->
        {
            System.out.println(v.getString());
            return v; // pass through for variables
        });

        expression.addContextFunction("time", 0, (c, t, lv) ->
                new NumericValue((System.nanoTime() / 1000.0) / 1000.0));

        expression.addContextFunction("unix_time", 0, (c, t, lv) ->
                new NumericValue(System.currentTimeMillis()));

        expression.addFunction("convert_date", lv ->
        {
            int argsize = lv.size();
            if (lv.isEmpty())
            {
                throw new ArityError(SourcePosition.UNKNOWN,"'convert_date' requires at least one parameter");
            }
            Value value = lv.get(0);
            if (argsize == 1 && !(value instanceof ListValue))
            {
                // in UTC, not in the zone of the machine: the same timestamp is the same date on every server
                Calendar cal = new GregorianCalendar(UTC, Locale.ROOT);
                cal.setTimeInMillis(NumericValue.asNumber(value, "timestamp").getLong());
                int weekday = cal.get(Calendar.DAY_OF_WEEK) - 1;
                if (weekday == 0)
                {
                    weekday = 7;
                }
                return ListValue.ofNums(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH),
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        cal.get(Calendar.SECOND),
                        weekday,
                        cal.get(Calendar.DAY_OF_YEAR),
                        cal.get(Calendar.WEEK_OF_YEAR)
                );
            }
            else if (value instanceof final ListValue list)
            {
                lv = list.getItems();
                argsize = lv.size();
            }
            Calendar cal = new GregorianCalendar(UTC, Locale.ROOT);
            // clear() and not a constructor with the date: that one takes the milliseconds from the clock
            cal.clear();
            cal.set(0, Calendar.JANUARY, 1, 0, 0, 0);

            if (argsize == 3)
            {
                cal.set(
                        NumericValue.asNumber(lv.get(0)).getInt(),
                        NumericValue.asNumber(lv.get(1)).getInt() - 1,
                        NumericValue.asNumber(lv.get(2)).getInt()
                );
            }
            else if (argsize == 6)
            {
                cal.set(
                        NumericValue.asNumber(lv.get(0)).getInt(),
                        NumericValue.asNumber(lv.get(1)).getInt() - 1,
                        NumericValue.asNumber(lv.get(2)).getInt(),
                        NumericValue.asNumber(lv.get(3)).getInt(),
                        NumericValue.asNumber(lv.get(4)).getInt(),
                        NumericValue.asNumber(lv.get(5)).getInt()
                );
            }
            else
            {
                throw new ArityError(SourcePosition.UNKNOWN,"Date conversion requires 3 arguments for Dates or 6 arguments, for time");
            }
            return new NumericValue(cal.getTimeInMillis());
        });

        // lazy cause evaluates expression multiple times
        expression.addLazyFunction("profile_expr", 1, (c, t, lv) ->
        {
            LazyValue lazy = lv.get(0);
            long end = System.nanoTime() + 50000000L;
            long it = 0;
            while (System.nanoTime() < end)
            {
                lazy.evalValue(c);
                it++;
            }
            Value res = new NumericValue(it);
            return (cc, tt) -> res;
        });

        expression.addContextFunction("var", 1, (c, t, lv) ->
                expression.getOrSetAnyVariable(c, lv.get(0).getString()).evalValue(c));

        expression.addContextFunction("undef", 1, (c, t, lv) ->
        {
            Value remove = lv.get(0);
            if (remove instanceof FunctionValue)
            {
                c.host.delFunction(expression.module, remove.getString());
                return Value.NULL;
            }
            String varname = remove.getString();
            boolean isPrefix = varname.endsWith("*");
            if (isPrefix)
            {
                varname = varname.replaceAll("\\*+$", "");
            }
            if (isPrefix)
            {
                c.host.delFunctionWithPrefix(expression.module, varname);
                if (Expression.isGlobalVariableName(varname))
                {
                    c.host.delGlobalVariableWithPrefix(expression.module, varname);
                }
                else if (!varname.startsWith("_"))
                {
                    c.removeVariablesMatching(varname);
                }
            }
            else
            {
                c.host.delFunction(expression.module, varname);
                if (Expression.isGlobalVariableName(varname))
                {
                    c.host.delGlobalVariable(expression.module, varname);
                }
                else if (!varname.startsWith("_"))
                {
                    c.delVariable(varname);
                }
            }
            return Value.NULL;
        });

        //deprecate
        expression.addContextFunction("vars", 1, (c, t, lv) ->
        {
            String prefix = lv.get(0).getString();
            List<Value> values = new ArrayList<>();
            if (prefix.startsWith("global"))
            {
                c.host.globalVariableNames(expression.module, s -> s.startsWith(prefix)).forEach(s -> values.add(new StringValue(s)));
            }
            else
            {
                c.getAllVariableNames().stream().filter(s -> s.startsWith(prefix)).forEach(s -> values.add(new StringValue(s)));
            }
            return ListValue.wrap(values);
        });

    }

    public static String titleCase(String str) {
        if (str.isEmpty()) {
            return str;
        }
        str = str.toLowerCase(Locale.ROOT);
        char[] buffer = str.toCharArray();
        boolean capitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            char ch = buffer[i];
            if (Character.isWhitespace(ch)) {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer[i] = Character.toTitleCase(ch);
                capitalizeNext = false;
            }
        }
        return new String(buffer);
    }

}
