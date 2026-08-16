package blossom.value;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.LazyValue;
import blossom.exception.IndexOutOfBoundsError;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jspecify.annotations.Nullable;


import static java.lang.Math.abs;

public class ListValue extends AbstractListValue implements ContainerValueInterface, Guardable
{
    protected final List<Value> items;
    /** null unless this list became reachable from a shared global, see ValueGuard */
    private volatile @Nullable ValueGuard guard = null;

    @Override
    public @Nullable ValueGuard guard()
    {
        return guard;
    }

    @Override
    public void setGuard(ValueGuard guard)
    {
        this.guard = guard;
    }

    private <T> T guardedRead(Supplier<T> op)
    {
        return ValueGuard.guarded(guard, op);
    }

    private void guardedWrite(Runnable op)
    {
        ValueGuard.guardedRun(guard, op);
    }

    /**
     * The backing list itself, for {@link ValueGuard} to walk. Callers must hold the guard.
     */
    List<Value> rawItems()
    {
        return items;
    }

    /**
     * an element stored into a guarded list has to be guarded too, or mutating it through the
     * list would race even though the list itself is protected
     */
    private void adopt(Value value)
    {
        ValueGuard current = guard;
        if (current != null)
        {
            ValueGuard.attach(value, current);
        }
    }

    @Override
    public String getString()
    {
        return guardedRead(() -> "[" + items.stream().map(Value::getString).collect(Collectors.joining(", ")) + "]");
    }

    @Override
    public String getPrettyString()
    {
        return guardedRead(() -> items.size() < 8
                ? "[" + items.stream().map(Value::getPrettyString).collect(Collectors.joining(", ")) + "]"
                : "[" + items.get(0).getPrettyString() + ", " + items.get(1).getPrettyString() + ", ..., " + items.get(items.size() - 2).getPrettyString() + ", " + items.get(items.size() - 1).getPrettyString() + "]");
    }

    @Override
    public boolean getBoolean()
    {
        return !guardedRead(items::isEmpty);
    }

    @Override
    public Value clone()
    {
        // shares the backing list on purpose, so the copy has to share the guard as well
        ListValue copy = new ListValue(items);
        copy.guard = guard;
        return copy;
    }

    @Override
    public Value deepcopy()
    {
        return guardedRead(() ->
        {
            List<Value> copyItems = new ArrayList<>(items.size());
            for (Value entry : items)
            {
                copyItems.add(entry.deepcopy());
            }
            return new ListValue(copyItems); // a fresh structure, nothing else can see it yet
        });
    }

    public ListValue(Collection<? extends Value> list)
    {
        items = new ArrayList<>(list);
    }

    protected ListValue(List<Value> list)
    {
        items = list;
    }

    public static Value fromTriple(double a, double b, double c)
    {
        return ListValue.of(new NumericValue(a), new NumericValue(b), new NumericValue(c));
    }

    public static Value fromTriple(int a, int b, int c)
    {
        return fromTriple((double) a, b, c);
    }


    public static ListValue wrap(Stream<Value> stream)
    {
        return wrap(stream.collect(Collectors.toList()));
    }

    public static ListValue wrap(List<Value> list)
    {
        return new ListValue(list);
    }

    public static ListValue of(Value... list)
    {
        return new ListValue(new ArrayList<>(Arrays.asList(list)));
    }

    public static ListValue ofNums(Number... list)
    {
        List<Value> valList = new ArrayList<>();
        for (Number i : list)
        {
            valList.add(new NumericValue(i.doubleValue()));
        }
        return new ListValue(valList);
    }

    public static LazyValue lazyEmpty()
    {
        Value ret = new ListValue();
        return (c, t) -> ret;
    }

    private ListValue()
    {
        items = new ArrayList<>();
    }

    @Override
    public Value add(Value other)
    {
        // the other list is snapshot before taking our own guard: locking two lists at once could
        // deadlock against a thread doing the same addition the other way around
        List<Value> otherItems = other instanceof final ListValue list ? list.getItems() : null;
        return guardedRead(() ->
        {
            ListValue output = new ListValue();
            if (otherItems != null)
            {
                if (otherItems.size() == items.size())
                {
                    for (int i = 0, size = items.size(); i < size; i++)
                    {
                        output.items.add(items.get(i).add(otherItems.get(i)));
                    }
                }
                else
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot add two lists of uneven sizes");
                }
            }
            else
            {
                for (Value v : items)
                {
                    output.items.add(v.add(other));
                }
            }
            return output;
        });
    }

    @Override
    public void append(Value v)
    {
        adopt(v);
        guardedWrite(() -> items.add(v));
    }

    @Override
    public Value subtract(Value other)
    {
        List<Value> otherItems = other instanceof final ListValue list ? list.getItems() : null;
        return guardedRead(() ->
        {
            ListValue output = new ListValue();
            if (otherItems != null)
            {
                if (otherItems.size() == items.size())
                {
                    for (int i = 0, size = items.size(); i < size; i++)
                    {
                        output.items.add(items.get(i).subtract(otherItems.get(i)));
                    }
                }
                else
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot subtract two lists of uneven sizes");
                }
            }
            else
            {
                for (Value v : items)
                {
                    output.items.add(v.subtract(other));
                }
            }
            return output;
        });
    }


    @Override
    public Value multiply(Value other)
    {
        List<Value> otherItems = other instanceof final ListValue list ? list.getItems() : null;
        return guardedRead(() ->
        {
            ListValue output = new ListValue();
            if (otherItems != null)
            {
                if (otherItems.size() == items.size())
                {
                    for (int i = 0, size = items.size(); i < size; i++)
                    {
                        output.items.add(items.get(i).multiply(otherItems.get(i)));
                    }
                }
                else
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot multiply two lists of uneven sizes");
                }
            }
            else
            {
                for (Value v : items)
                {
                    output.items.add(v.multiply(other));
                }
            }
            return output;
        });
    }

    @Override
    public Value divide(Value other)
    {
        List<Value> otherItems = other instanceof final ListValue list ? list.getItems() : null;
        return guardedRead(() ->
        {
            ListValue output = new ListValue();
            if (otherItems != null)
            {
                if (otherItems.size() == items.size())
                {
                    for (int i = 0, size = items.size(); i < size; i++)
                    {
                        output.items.add(items.get(i).divide(otherItems.get(i)));
                    }
                }
                else
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot divide two lists of uneven sizes");
                }
            }
            else
            {
                for (Value v : items)
                {
                    output.items.add(v.divide(other));
                }
            }
            return output;
        });
    }

    @Override
    public int compareTo(Value o)
    {
        if (o instanceof final ListValue ol)
        {
            // both sides are snapshot through getItems(), which never holds two guards at once
            List<Value> mine = getItems();
            List<Value> theirs = ol.getItems();
            int size = mine.size();
            int otherSize = theirs.size();
            if (size != otherSize)
            {
                return size - otherSize;
            }
            if (size == 0)
            {
                return 0;
            }
            for (int i = 0; i < size; i++)
            {
                int res = mine.get(i).compareTo(theirs.get(i));
                if (res != 0)
                {
                    return res;
                }
            }
            return 0;
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof ListValue list && getItems().equals(list.getItems());
    }

    /**
     * The elements of this list. A guarded list hands out a snapshot instead of its backing list,
     * so that callers iterating it cannot trip over another thread's mutation - no caller in the
     * code base modifies a list through this method.
     */
    public List<Value> getItems()
    {
        return guard == null ? items : guardedRead(() -> new ArrayList<>(items));
    }

    @Override
    public Iterator<Value> iterator()
    {
        return guardedRead(() -> new ArrayList<>(items)).iterator();
    } // should be thread safe

    @Override
    public List<Value> unpack()
    {
        return guardedRead(() -> new ArrayList<>(items));
    }

    public void extend(List<Value> subList)
    {
        subList.forEach(this::adopt);
        guardedWrite(() -> items.addAll(subList));
    }

    /**
     * Finds a proper list index >=0 and < len that correspont to the rolling index value of idx
     *
     * @param idx
     * @param len
     * @return
     */
    public static int normalizeIndex(long idx, int len)
    {
        if (idx >= 0 && idx < len)
        {
            return (int) idx;
        }
        long range = abs(idx) / len;
        idx += (range + 2) * len;
        idx = idx % len;
        return (int) idx;
    }

    public static class ListConstructorValue extends ListValue
    {
        public ListConstructorValue(Collection<? extends Value> list)
        {
            super(list);
        }
    }

    @Override
    public int length()
    {
        return guardedRead(items::size);
    }

    @Override
    public Value in(Value value1)
    {
        List<Value> snapshot = getItems();
        for (int i = 0; i < snapshot.size(); i++)
        {
            Value v = snapshot.get(i);
            if (v.equals(value1))
            {
                return new NumericValue(i);
            }
        }
        return Value.NULL;
    }

    @Override
    public Value slice(long fromDesc, Long toDesc)
    {
        List<Value> snapshot = getItems();
        int size = snapshot.size();
        int from = normalizeIndex(fromDesc, size);
        if (toDesc == null)
        {
            return new ListValue(new ArrayList<>(snapshot.subList(from, size)));
        }
        int to = normalizeIndex(toDesc, size + 1);
        if (from > to)
        {
            return ListValue.of();
        }
        return new ListValue(new ArrayList<>(snapshot.subList(from, to)));
    }

    @Override
    public Value split(Value delimiter)
    {
        ListValue result = new ListValue();
        if (delimiter == null)
        {
            this.forEach(item -> result.items.add(of(item)));
            return result;
        }
        List<Value> snapshot = getItems();
        int startIndex = 0;
        int index = 0;
        for (Value val : snapshot)
        {
            index++;
            if (val.equals(delimiter))
            {
                result.items.add(new ListValue(new ArrayList<>(snapshot.subList(startIndex, index - 1))));
                startIndex = index;
            }
        }
        result.items.add(new ListValue(new ArrayList<>(snapshot.subList(startIndex, snapshot.size()))));
        return result;
    }

    @Override
    public double readDoubleNumber()
    {
        return guardedRead(items::size);
    }

    @Override
    public boolean put(Value where, Value value, Value conditionValue)
    {
        String condition = conditionValue.getString();
        if (condition.equalsIgnoreCase("insert"))
        {
            return put(where, value, false, false);
        }
        if (condition.equalsIgnoreCase("extend"))
        {
            return put(where, value, false, true);
        }
        if (condition.equalsIgnoreCase("replace"))
        {
            return put(where, value, true, false);
        }
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "list", "List 'put' modifier could be either 'insert', 'replace', or extend");
    }

    @Override
    public boolean put(Value ind, Value value)
    {
        return put(ind, value, true, false);
    }

    private boolean put(Value ind, Value value, boolean replace, boolean extend)
    {
        // whatever comes from the other list is read before we take our own guard, and elements
        // joining a guarded list are guarded on the way in
        List<Value> appendix = extend && value instanceof final AbstractListValue alv
                ? StreamSupport.stream(((Iterable<Value>) alv::iterator).spliterator(), false).collect(Collectors.toList())
                : null;
        if (appendix == null)
        {
            adopt(value);
        }
        else
        {
            appendix.forEach(this::adopt);
        }
        return guardedRead(() -> putUnguarded(ind, value, replace, appendix));
    }

    private boolean putUnguarded(Value ind, Value value, boolean replace, @Nullable List<Value> appendix)
    {
        if (ind.isNull())
        {
            if (appendix != null)
            {
                items.addAll(appendix);
            }
            else
            {
                items.add(value);
            }
        }
        else
        {
            int numitems = items.size();
            if (!(ind instanceof NumericValue))
            {
                return false;
            }
            int index = (int) ((NumericValue) ind).getLong();
            if (index < 0)
            {// only for values < 0
                index = normalizeIndex(index, numitems);
            }
            if (replace)
            {
                while (index >= items.size())
                {
                    items.add(Value.NULL);
                }
                items.set(index, value);
                return true;
            }
            while (index > items.size())
            {
                items.add(Value.NULL);
            }

            if (appendix != null)
            {
                items.addAll(index, appendix);
                return true;
            }
            items.add(index, value);
        }
        return true;
    }

    @Override
    public Value get(Value value)
    {
        long address = NumericValue.asNumber(value, "'address' to a list index").getLong();
        return guardedRead(() ->
        {
            int size = items.size();
            return size == 0 ? Value.NULL : items.get(normalizeIndex(address, size));
        });
    }

    @Override
    public boolean has(Value where)
    {
        long index = NumericValue.asNumber(where, "'address' to a list index").getLong();
        return index >= 0 && index < guardedRead(items::size);
    }

    @Override
    public boolean delete(Value where)
    {
        if (!(where instanceof final NumericValue numericValue))
        {
            return false;
        }
        long index = numericValue.getLong();
        return guardedRead(() ->
        {
            int size = items.size();
            if (size == 0)
            {
                throw new IndexOutOfBoundsError(SourcePosition.UNKNOWN,
                        "There is no element " + index + " to delete: the list is empty.");
            }
            if (index >= size || index < -size)
            {
                // gli indici di lettura si avvolgono, ma cancellare qualcosa che non c'e' e' un
                // errore vero: chi scrive si aspetta che dopo la delete quell'elemento non ci sia piu'
                throw new IndexOutOfBoundsError(SourcePosition.UNKNOWN,
                        "Index " + index + " is outside the list, which has " + size + " elements.");
            }
            items.remove(normalizeIndex(index, size));
            return true;
        });
    }

    @Override
    public String getTypeString()
    {
        return "list";
    }

    @Override
    public int hashCode()
    {
        return guardedRead(items::hashCode);
    }




    @Override
    public JsonElement toJson()
    {
        JsonArray array = new JsonArray();
        for (Value el : getItems())
        {
            array.add(el.toJson());
        }
        return array;
    }
}
