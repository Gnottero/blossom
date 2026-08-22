package blossom.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * The local variables of an execution frame.
 *
 * <p>Not a {@link HashMap}, because a frame is almost always tiny: the parameters of a function plus
 * the few variables the body writes. At that size a hash map costs more than it returns, since the
 * object, the table and one node per entry are allocated on every call, while two parallel arrays
 * and a linear scan all fit in one cache line.
 *
 * <p>Above {@link #SPILL_THRESHOLD} entries the linear scan starts costing more than hashing, and at
 * that point the frame switches to a {@link HashMap}. That happens to the body of a long module and
 * not to calls: it is the rare case that must not degrade, not the one to optimise.
 *
 * <p>The arrays are born on the first write: a frame that declares nothing, the case of a function
 * without parameters, allocates nothing.
 */
final class VariableFrame
{
    /** Above this number of variables the frame switches to the hash map. */
    private static final int SPILL_THRESHOLD = 16;

    private static final int INITIAL_CAPACITY = 4;

    private String @Nullable [] names;
    private LazyValue @Nullable [] values;
    private int size;

    /** non null only after the spill: from then on it is the sole holder of the variables */
    private @Nullable Map<String, LazyValue> spilled;

    @Nullable
    LazyValue get(String name)
    {
        Map<String, LazyValue> map = spilled;
        if (map != null)
        {
            return map.get(name);
        }
        String[] keys = names;
        for (int i = 0; i < size; i++)
        {
            // the names come from the tokens, interned while parsing: identity comparison decides
            // almost always, and equals stays for names built at run time (var(...))
            String key = keys[i];
            if (key == name || key.equals(name))
            {
                return values[i];
            }
        }
        return null;
    }

    void put(String name, LazyValue value)
    {
        Map<String, LazyValue> map = spilled;
        if (map != null)
        {
            map.put(name, value);
            return;
        }
        if (names == null)
        {
            names = new String[INITIAL_CAPACITY];
            values = new LazyValue[INITIAL_CAPACITY];
        }
        String[] keys = names;
        for (int i = 0; i < size; i++)
        {
            String key = keys[i];
            if (key == name || key.equals(name))
            {
                values[i] = value;
                return;
            }
        }
        if (size == keys.length)
        {
            if (size >= SPILL_THRESHOLD)
            {
                spill().put(name, value);
                return;
            }
            grow();
        }
        names[size] = name;
        values[size] = value;
        size++;
    }

    void remove(String name)
    {
        Map<String, LazyValue> map = spilled;
        if (map != null)
        {
            map.remove(name);
            return;
        }
        String[] keys = names;
        for (int i = 0; i < size; i++)
        {
            String key = keys[i];
            if (key == name || key.equals(name))
            {
                // order does not matter: the last entry takes the freed slot
                size--;
                names[i] = names[size];
                values[i] = values[size];
                names[size] = null;
                values[size] = null;
                return;
            }
        }
    }

    void removeMatching(Predicate<String> doomed)
    {
        Map<String, LazyValue> map = spilled;
        if (map != null)
        {
            map.keySet().removeIf(doomed);
            return;
        }
        for (int i = size - 1; i >= 0; i--)
        {
            if (doomed.test(names[i]))
            {
                size--;
                names[i] = names[size];
                values[i] = values[size];
                names[size] = null;
                values[size] = null;
            }
        }
    }

    void forEach(BiConsumer<String, LazyValue> action)
    {
        Map<String, LazyValue> map = spilled;
        if (map != null)
        {
            map.forEach(action);
            return;
        }
        for (int i = 0; i < size; i++)
        {
            action.accept(names[i], values[i]);
        }
    }

    private void grow()
    {
        int capacity = Math.min(names.length * 2, SPILL_THRESHOLD);
        String[] newNames = new String[capacity];
        LazyValue[] newValues = new LazyValue[capacity];
        System.arraycopy(names, 0, newNames, 0, size);
        System.arraycopy(values, 0, newValues, 0, size);
        names = newNames;
        values = newValues;
    }

    private Map<String, LazyValue> spill()
    {
        Map<String, LazyValue> map = new HashMap<>(size * 2);
        for (int i = 0; i < size; i++)
        {
            map.put(names[i], values[i]);
        }
        names = null;
        values = null;
        size = 0;
        spilled = map;
        return map;
    }
}
