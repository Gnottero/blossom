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
        // valori a passi di dieci, per lasciare spazio a livelli intermedi senza
        // dover rinumerare tutto ogni volta che il linguaggio cresce
        put("attribute~:", 800);
        put("unary+-!...", 600);
        put("exponent^", 400);
        put("multiplication*/%", 300);
        put("addition+-", 200);
        put("compare>=><=<", 100);
        put("equal==!=", 70);
        put("and&&", 50);
        put("or||", 40);
        // il tubo lega piu' dell'assegnamento, cosi' 'x = a |> f' mette in x il risultato,
        // e meno di '||', cosi' '(a || b) |> f' e' quello che sembra
        put("pipe|>", 35);
        put("assign=<>", 30);
        // '->' lega meno dell'assegnamento, perche' il corpo di una funzione e' spesso un
        // assegnamento: 'fn conta() -> contatore += 1' deve leggersi con l'assegnamento dentro.
        // Il caso opposto - 'f = fn(a) -> a + 1', dove la lambda va in una variabile - non si
        // risolve con la precedenza ma nell'albero, vedi RPNToParseTree
        put("def->", 20);
        // pub / fn / let: prendono tutta la definizione che li segue, quindi legano meno di '->'
        put("declaration", 15);
        put("nextop;", 10);
    }};

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

        expression.addBinaryOperator("~", "match", precedence.get("attribute~:"), true, Value::in);

        /*
         * a |> f(b)  ==  f(a, b)
         *
         * Non arriva mai qui: il nodo sparisce mentre si costruisce l'albero, e quello che resta e'
         * la chiamata con un argomento in piu' davanti. La registrazione serve perche' ogni
         * operatore deve avere un nome funzionale, e per il caso in cui uno lo usi come valore.
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

        expression.addMathematicalBinaryIntFunction("bitwise_shift_left", (num, amount) -> num << amount);
        expression.addMathematicalBinaryIntFunction("bitwise_shift_right", (num, amount) -> num >>> amount);
        expression.addMathematicalBinaryIntFunction("bitwise_arithmetic_shift_right", (num, amount) -> num >> amount);
        expression.addMathematicalBinaryIntFunction("bitwise_roll_left", (num, amount) -> Long.rotateLeft(num, (int)amount));
        expression.addMathematicalBinaryIntFunction("bitwise_roll_right", (num, amount) -> Long.rotateRight(num, (int)amount));
        expression.addMathematicalUnaryIntFunction("bitwise_not", d -> {
            long num = (long) d;
            return ~num;
        });
        expression.addMathematicalUnaryIntFunction("bitwise_popcount", d -> (long) Long.bitCount((long)d));
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
            // need to order them so same obejects will be next to each other.
            lv.sort(Comparator.comparingInt(Value::hashCode));
            Value prev = lv.get(0);
            for (Value next : lv.subList(1, size))
            {
                if (prev.equals(next))
                {
                    return Value.FALSE;
                }
                prev = next;
            }
            return Value.TRUE;
        });

        // lazy cause of assignment which is non-trivial
        expression.addLazyBinaryOperator("=", "assign", precedence.get("assign=<>"), false, false, t -> Context.Type.LVALUE, (c, t, lv1, lv2) ->
        {
            // il caso normale: a sinistra c'e' il nome di una variabile, e il nodo che lo legge lo
            // sa dal parsing. Niente da valutare a sinistra, e niente da copiare a destra
            if (lv1 instanceof final VariableRead target)
            {
                Value v2 = lv2.evalValue(c);
                expression.setAnyVariable(c, target.variableName(), v2);
                // dentro una dichiarazione ('let x = 0') chi sta sopra ha bisogno del nome
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

        // pigro come gli altri assegnamenti: i nomi delle due variabili li sanno i nodi a sinistra e
        // a destra, e chiederglieli e' l'unico modo di scambiarle senza farsi dire da un valore come
        // si chiama la variabile in cui sta
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
        // dentro una firma '...rest' dichiara un nome: il contesto di localizzazione deve arrivare
        // all'operando, che li' si valuta al proprio riferimento invece che al proprio valore
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
     * Il nome della variabile che {@code value} rappresenta.
     *
     * <p>Ci arriva quello che ha prodotto la parte sinistra di un assegnamento valutata come
     * l-value: o è il riferimento a una variabile, o non c'è niente a cui assegnare.
     */
    private static String assignableName(Value value)
    {
        if (value instanceof final VariableReferenceValue reference)
        {
            return reference.name();
        }
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "value", value.getPrettyString() + " is not a variable");
    }

    /**
     * {@code variable += value}: legge, combina e riscrive in un passo solo.
     *
     * <p>Su un global quel passo avviene sotto il monitor della variabile, così due task che
     * accodano allo stesso global non si perdono un aggiornamento a vicenda.
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

    /** {@code a <> b}: scambia il contenuto di due variabili, e vale il nuovo contenuto di {@code a}. */
    private static Value swap(Expression expression, Context c, String left, String right)
    {
        Value leftValue = expression.getOrSetAnyVariable(c, left).evalValue(c, Context.NONE);
        Value rightValue = expression.getOrSetAnyVariable(c, right).evalValue(c, Context.NONE);
        expression.setAnyVariable(c, left, rightValue);
        expression.setAnyVariable(c, right, leftValue);
        return rightValue;
    }
}
