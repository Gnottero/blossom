package blossom.language;

import blossom.exception.SourcePosition;
import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.LazyValue;
import blossom.interpreter.VariableRead;
import blossom.value.AbstractListValue;
import blossom.value.BooleanValue;
import blossom.value.ContainerValueInterface;
import blossom.value.FunctionAnnotationValue;
import blossom.value.FunctionUnpackedArgumentsValue;
import blossom.value.FrameworkValue;
import blossom.value.Guardable;
import blossom.value.LContainerValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.Value;
import blossom.value.ValueGuard;
import blossom.value.VariableReferenceValue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Operators
{
    public static final Map<String, Integer> precedence = new HashMap<>()
    {{
        // values in steps of ten, to leave room for intermediate levels without
        // having to renumber everything every time the language grows
        put("attribute~:", 800);
        put("unary+-!...", 600);
        put("exponent^", 400);
        put("multiplication*/%", 300);
        put("addition+-", 200);
        put("compare>=><=<", 100);
        put("equal==!=", 70);
        put("and&&", 50);
        put("or||", 40);
        // the pipe binds tighter than assignment, so 'x = a |> f' puts the result into x,
        // and looser than '||', so '(a || b) |> f' is what it looks like
        put("pipe|>", 35);
        put("assign=<>", 30);
        // '->' binds looser than assignment, because the body of a function is often an
        // assignment: 'fn count() -> counter += 1' must be read with the assignment inside it.
        // The opposite case, 'f = fn(a) -> a + 1', where the lambda goes into a variable, is not
        // solved through precedence but in the tree, see RPNToParseTree
        put("def->", 20);
        // pub / fn / let: take the entire definition that follows them, so they bind looser than '->'
        put("declaration", 15);
        put("nextop;", 10);
    }};

    /**
     * The shift amount of a bitwise_shift_*, if it is a sensible number of bits.
     *
     * <p>It returns the width as it is, leaving the caller to decide what to do beyond 64 bits, and
     * stops only on a negative number, which is not a shorter shift but the opposite direction
     * written badly.
     */
    private static long asLong(Value v)
    {
        NumericValue number = NumericValue.asNumber(v);
        return number.isInteger() ? number.getLong() : (long) number.getDouble();
    }

    private static long shiftAmount(String function, long amount)
    {
        if (amount < 0)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "number", "'" + function + "' cannot shift by "
                    + amount + " bits: to shift the other way there is the function that does that.");
        }
        return amount;
    }

    public static void apply(Expression expression)
    {
        expression.addBinaryOperator("+", "sum", precedence.get("addition+-"), true, Value::add, lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator.add(v);
            }
            return accumulator;
        });

        expression.addBinaryOperator("-", "difference", precedence.get("addition+-"), true, Value::subtract, lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator.subtract(v);
            }
            return accumulator;
        });

        expression.addBinaryOperator("*", "product", precedence.get("multiplication*/%"), true, Value::multiply, lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator.multiply(v);
            }
            return accumulator;
        });

        expression.addBinaryOperator("/", "quotient", precedence.get("multiplication*/%"), true, Value::divide, lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            Value accumulator = lv.get(0);
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator.divide(v);
            }
            return accumulator;
        });

        expression.addBinaryOperator("%", "modulo", precedence.get("multiplication*/%"), true, (v1, v2) ->
                NumericValue.asNumber(v1).mod(NumericValue.asNumber(v2)));

        expression.addBinaryOperator("^", "exponent", precedence.get("exponent^"), false, (v1, v2) ->
                new NumericValue(java.lang.Math.pow(NumericValue.asNumber(v1).getDouble(), NumericValue.asNumber(v2).getDouble())));

        expression.addFunction("bitwise_and", lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator & NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        expression.addFunction("bitwise_xor", lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator ^ NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        expression.addFunction("bitwise_or", lv -> {
            int size = lv.size();
            if (size == 0)
            {
                return Value.NULL;
            }
            long accumulator = NumericValue.asNumber(lv.get(0)).getLong();
            for (Value v : lv.subList(1, size))
            {
                accumulator = accumulator | NumericValue.asNumber(v).getLong();
            }
            return new NumericValue(accumulator);
        });

        // lazy cause RHS is only conditional
        expression.addLazyBinaryOperator("&&", "and", precedence.get("and&&"), false, true, t -> Context.Type.BOOLEAN, (c, t, lv1, lv2) ->
        { // todo check how is optimizations going
            Value v1 = lv1.evalValue(c, Context.BOOLEAN);
            return v1.getBoolean() ? lv2 : ((cc, tt) -> v1);
        }, (c, t, lv) -> {
            int last = lv.size() - 1;
            if (last == -1)
            {
                return LazyValue.TRUE;
            }
            for (LazyValue l : lv.subList(0, last))
            {
                Value val = l.evalValue(c, Context.Type.BOOLEAN);
                if (val instanceof FunctionUnpackedArgumentsValue fuav)
                {
                    for (Value it : fuav)
                    {
                        if (!it.getBoolean())
                        {
                            return (cc, tt) -> it;
                        }
                    }
                }
                else
                {
                    if (!val.getBoolean())
                    {
                        return (cc, tt) -> val;
                    }
                }
            }
            return lv.get(last);
        });

        // lazy cause RHS is only conditional
        expression.addLazyBinaryOperator("||", "or", precedence.get("or||"), false, true, t -> Context.Type.BOOLEAN, (c, t, lv1, lv2) ->
        {
            Value v1 = lv1.evalValue(c, Context.BOOLEAN);
            return v1.getBoolean() ? ((cc, tt) -> v1) : lv2;
        }, (c, t, lv) -> {
            int last = lv.size() - 1;
            if (last == -1)
            {
                return LazyValue.FALSE;
            }
            for (LazyValue l : lv.subList(0, last))
            {
                Value val = l.evalValue(c, Context.Type.BOOLEAN);
                if (val instanceof final FunctionUnpackedArgumentsValue fuav)
                {
                    for (Value it : fuav)
                    {
                        if (it.getBoolean())
                        {
                            return (cc, tt) -> it;
                        }
                    }
                }
                else
                {
                    if (val.getBoolean())
                    {
                        return (cc, tt) -> val;
                    }
                }
            }
            return lv.get(last);
        });

        // the functional name is 'contains', not 'match': '~' is a membership test, not a shape
        // comparison, and 'match' is already used by the construct of the same name
        expression.addBinaryOperator("~", "contains", precedence.get("attribute~:"), true, Value::in);

        /*
         * a |> f(b)  ==  f(a, b)
         *
         * Execution never reaches here: the node disappears while the tree is being built, and
         * what remains is the call with one extra argument in front. The registration exists
         * because every operator must have a functional name, and for the case where it is used
         * as a value.
         */
        expression.addBinaryOperator("|>", "pipe", precedence.get("pipe|>"), true, (v1, v2) ->
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator",
                    "'|>' wants a call on its right, as in list |> list.map(f).");
        });

        expression.addBinaryOperator(">", "decreasing", precedence.get("compare>=><=<"), false, (v1, v2) -> BooleanValue.of(v1.compareTo(v2) > 0), lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) <= 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        expression.addBinaryOperator(">=", "nonincreasing", precedence.get("compare>=><=<"), false, (v1, v2) -> BooleanValue.of(v1.compareTo(v2) >= 0), lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) < 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        expression.addBinaryOperator("<", "increasing", precedence.get("compare>=><=<"), false, (v1, v2) -> BooleanValue.of(v1.compareTo(v2) < 0), lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) >= 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        expression.addBinaryOperator("<=", "nondecreasing", precedence.get("compare>=><=<"), false, (v1, v2) -> BooleanValue.of(v1.compareTo(v2) <= 0), lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (prev.compareTo(next) > 0)
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        // Java masks the shift to the low 6 bits, so '1 << 64' is 1 and '1 << 200' is 256: that is
        // the JVM behaviour showing through, not what the function name says. Here a shift beyond 64
        // bits empties the number, as one expects, and a negative shift is an error instead of
        // turning into a huge shift
        expression.addMathematicalBinaryIntFunction("bitwise_shift_left",
                (num, amount) -> shiftAmount("bitwise_shift_left", amount) >= Long.SIZE ? 0L : num << amount);
        expression.addMathematicalBinaryIntFunction("bitwise_shift_right",
                (num, amount) -> shiftAmount("bitwise_shift_right", amount) >= Long.SIZE ? 0L : num >>> amount);
        expression.addMathematicalBinaryIntFunction("bitwise_arithmetic_shift_right",
                (num, amount) -> shiftAmount("bitwise_arithmetic_shift_right", amount) >= Long.SIZE
                        ? (num < 0 ? -1L : 0L) : num >> amount);
        expression.addMathematicalBinaryIntFunction("bitwise_roll_left", (num, amount) -> Long.rotateLeft(num, (int)amount));
        expression.addMathematicalBinaryIntFunction("bitwise_roll_right", (num, amount) -> Long.rotateRight(num, (int)amount));
        // read as a long, not through a double: past 2^53 a double drops the low bits
        expression.addUnaryFunction("bitwise_not", v -> new NumericValue(~asLong(v)));
        expression.addUnaryFunction("bitwise_popcount", v -> new NumericValue(Long.bitCount(asLong(v))));
        expression.addMathematicalUnaryIntFunction("double_to_long_bits", Double::doubleToLongBits);
        expression.addUnaryFunction("long_to_double_bits", v ->
                new NumericValue(Double.longBitsToDouble(NumericValue.asNumber(v).getLong())));
        expression.addBinaryOperator("==", "equal", precedence.get("equal==!="), false, (v1, v2) -> v1.equals(v2) ? Value.TRUE : Value.FALSE, lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (!prev.equals(next))
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        expression.addBinaryOperator("!=", "unique", precedence.get("equal==!="), false, (v1, v2) -> v1.equals(v2) ? Value.FALSE : Value.TRUE, lv -> {
            int size = lv.size();
            if (size < 2)
            {
                return Value.TRUE;
            }
            // ordered by hash, so equal values sit next to each other
            lv.sort(Comparator.comparingInt(Value::hashCode));
            Value previous = lv.get(0);
            int previousHash = previous.hashCode();
            int collisionStart = 0;
            for (int i = 1; i < size; i++)
            {
                Value next = lv.get(i);
                // the neighbour is checked anyway: values equal for the language can hash differently
                if (previous.equals(next))
                {
                    return Value.FALSE;
                }
                int hash = next.hashCode();
                if (hash == previousHash)
                {
                    // different values with the same hash: the duplicate may be further back in the group
                    for (int j = collisionStart; j < i - 1; j++)
                    {
                        if (lv.get(j).equals(next))
                        {
                            return Value.FALSE;
                        }
                    }
                }
                else
                {
                    collisionStart = i;
                    previousHash = hash;
                }
                previous = next;
            }
            return Value.TRUE;
        });

        // lazy cause of assignment which is non-trivial
        expression.addLazyBinaryOperator("=", "assign", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            // the normal case: on the left there is a variable name, and the node that reads it
            // already knows it from parsing. Nothing to evaluate on the left, and nothing to copy
            // on the right
            if (lv1 instanceof final VariableRead target)
            {
                Value v2 = lv2.evalValue(c);
                expression.setAnyVariable(c, target.variableName(), v2);
                // inside a declaration ('let x = 0') whatever sits above needs the name
                return t == Context.LVALUE ? new VariableReferenceValue(target.variableName(), v2) : v2;
            }
            Value v1 = lv1.evalValue(c, Context.LVALUE);
            Value v2 = lv2.evalValue(c);
            if (v1 instanceof final ListValue.ListConstructorValue lcv && v2 instanceof final ListValue list)
            {
                List<Value> ll = lcv.getItems();
                List<Value> rl = list.getItems();
                if (ll.size() < rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too few values to unpack");
                }
                Iterator<Value> li = ll.iterator();
                Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    expression.setAnyVariable(c, assignableName(li.next()), ri.next());
                }
                return Value.TRUE;
            }
            if (v1 instanceof final LContainerValue lcv)
            {
                ContainerValueInterface container = lcv.container();
                if (container == null)
                {
                    return Value.NULL;
                }
                Value address = lcv.address();
                if (!(container.put(address, v2)))
                {
                    return Value.NULL;
                }
                return v2;
            }
            String varname = assignableName(v1);
            expression.setAnyVariable(c, varname, v2);
            return t == Context.LVALUE ? new VariableReferenceValue(varname, v2) : v2;
        });

        // lazy due to assignment
        expression.addLazyBinaryOperator("+=", "append", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            if (lv1 instanceof final VariableRead target)
            {
                return appendToVariable(expression, c, target.variableName(), lv2.evalValue(c));
            }
            Value v1 = lv1.evalValue(c, Context.LVALUE);
            Value v2 = lv2.evalValue(c);
            if (v1 instanceof final ListValue.ListConstructorValue lcv && v2 instanceof final ListValue list)
            {
                List<Value> ll = lcv.getItems();
                List<Value> rl = list.getItems();
                if (ll.size() < rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too few values to unpack");
                }
                Iterator<Value> li = ll.iterator();
                Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    appendToVariable(expression, c, assignableName(li.next()), ri.next());
                }
                return Value.TRUE;
            }
            if (v1 instanceof final LContainerValue lcv)
            {
                ContainerValueInterface cvi = lcv.container();
                if (cvi == null)
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Failed to resolve left hand side of the += operation");
                }
                Value key = lcv.address();
                // reading the element and storing the combined one back is one step, otherwise two
                // threads appending to the same entry of a shared container lose an update
                return ValueGuard.guarded(cvi instanceof final Guardable guardable ? guardable.guard() : null, () ->
                {
                    Value value = cvi.get(key);
                    if (value instanceof ListValue || value instanceof MapValue)
                    {
                        ((AbstractListValue) value).append(v2);
                        return (cc, tt) -> value;
                    }
                    Value res = value.add(v2);
                    cvi.put(key, res);
                    return (cc, tt) -> res;
                });
            }
            return appendToVariable(expression, c, assignableName(v1), v2);
        });

        // lazy like the other assignments: the names of the two variables are known by the nodes
        // on the left and right, and asking them is the only way to swap the variables without
        // having a value tell us what variable it lives in
        expression.addLazyBinaryOperator("<>", "swap", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            if (lv1 instanceof final VariableRead left && lv2 instanceof final VariableRead right)
            {
                return swap(expression, c, left.variableName(), right.variableName());
            }
            Value v1 = lv1.evalValue(c, Context.LVALUE);
            Value v2 = lv2.evalValue(c, Context.LVALUE);
            if (v1 instanceof final ListValue.ListConstructorValue lcv1 && v2 instanceof final ListValue.ListConstructorValue lcv2)
            {
                List<Value> ll = lcv1.getItems();
                List<Value> rl = lcv2.getItems();
                if (ll.size() < rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too many values to unpack");
                }
                if (ll.size() > rl.size())
                {
                    throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Too few values to unpack");
                }
                Iterator<Value> li = ll.iterator();
                Iterator<Value> ri = rl.iterator();
                while (li.hasNext())
                {
                    swap(expression, c, assignableName(li.next()), assignableName(ri.next()));
                }
                return Value.TRUE;
            }
            return swap(expression, c, assignableName(v1), assignableName(v2));
        });

        expression.addUnaryOperator("-", "opposite", false, v -> NumericValue.asNumber(v).opposite());

        expression.addUnaryOperator("+", "identity", false, NumericValue::asNumber);

        // could be non-lazy, but who cares - its a small one.
        expression.addLazyUnaryOperator("!", "not", precedence.get("unary+-!..."), false, true, x -> Context.Type.BOOLEAN, (c, t, lv) ->
                lv.evalValue(c, Context.BOOLEAN).getBoolean() ? (cc, tt) -> Value.FALSE : (cc, tt) -> Value.TRUE
        ); // might need context boolean

        // lazy because of typed evaluation of the argument
        // inside a signature '...rest' declares a name: the localization context must reach the
        // operand, which there evaluates to its own reference instead of its own value
        expression.addLazyUnaryOperator("...", "unpack", Operators.precedence.get("unary+-!..."), false, true, t -> t, (c, t, lv) ->
        {
            if (t == Context.LOCALIZATION)
            {
                Value annotation = new FunctionAnnotationValue(lv.evalValue(c, Context.LOCALIZATION), FunctionAnnotationValue.Type.VARARG);
                return (cc, tt) -> annotation;
            }
            if (!(lv.evalValue(c, t) instanceof final AbstractListValue alv))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "operator", "Unable to unpack a non-list");
            }
            FunctionUnpackedArgumentsValue fuaval = new FunctionUnpackedArgumentsValue(alv.unpack());
            return (cc, tt) -> fuaval;
        });

    }

    /**
     * The name of the variable that {@code value} represents.
     *
     * <p>What arrives here is whatever the left side of an assignment produced when evaluated as
     * an l-value: it is either a reference to a variable, or there is nothing to assign to.
     */
    private static String assignableName(Value value)
    {
        if (value instanceof final VariableReferenceValue reference)
        {
            return reference.name();
        }
        if (value instanceof LContainerValue)
        {
            // 'l:0 <> l:1': the access produced a slot inside a container, not a variable. Asking it
            // for its own text would throw the interpreter's internal error, which says nothing to
            // whoever wrote the line
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value",
                    "'<>' swaps two variables, not two positions inside a list or a map."
                            + " Read the two values, then write them back the other way round.");
        }
        if (value instanceof FrameworkValue)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", "This is not a variable");
        }
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", value.getPrettyString() + " is not a variable");
    }

    /**
     * {@code variable += value}: reads, combines and writes back in a single step.
     *
     * <p>On a global, that step happens under the variable's monitor, so two tasks appending to
     * the same global don't lose an update to each other.
     */
    private static Value appendToVariable(Expression expression, Context c, String name, Value value)
    {
        return expression.updateAnyVariable(c, name, current ->
        {
            if (current instanceof ListValue || current instanceof MapValue)
            {
                ((AbstractListValue) current).append(value);
                return current;
            }
            return current.add(value);
        }).evalValue(c, Context.NONE);
    }

    /** {@code a <> b}: swaps the contents of two variables, and evaluates to the new content of {@code a}. */
    private static Value swap(Expression expression, Context c, String left, String right)
    {
        Value leftValue = expression.getOrSetAnyVariable(c, left).evalValue(c, Context.NONE);
        Value rightValue = expression.getOrSetAnyVariable(c, right).evalValue(c, Context.NONE);
        expression.setAnyVariable(c, left, rightValue);
        expression.setAnyVariable(c, right, leftValue);
        return rightValue;
    }
}
