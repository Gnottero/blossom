package blossom.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Le variabili locali di un frame di esecuzione.
 *
 * <p>Non una {@link HashMap}, perché un frame è quasi sempre minuscolo: i parametri di una funzione
 * più le poche variabili che il corpo si scrive. Su misure di questa dimensione una mappa a hash
 * paga più di quanto rende — l'oggetto, la tabella, e un nodo per ogni voce, allocati ad ogni
 * chiamata — mentre due array paralleli e una scansione lineare stanno tutti in una riga di cache.
 *
 * <p>Sopra {@link #SPILL_THRESHOLD} voci la scansione lineare comincia a costare più dell'hash, e a
 * quel punto il frame passa a una {@link HashMap}. Succede al corpo di un modulo lungo, non alle
 * chiamate: è il caso raro che non deve degradare, non quello da ottimizzare.
 *
 * <p>Gli array nascono alla prima scrittura: un frame che non dichiara niente - il caso di una
 * funzione senza parametri - non alloca nulla.
 */
final class VariableFrame
{
    /** Oltre questo numero di variabili si passa alla mappa a hash. */
    private static final int SPILL_THRESHOLD = 16;

    private static final int INITIAL_CAPACITY = 4;

    private String @Nullable [] names;
    private LazyValue @Nullable [] values;
    private int size;

    /** non nulla solo dopo lo spill: da lì in poi è lei la sola depositaria delle variabili */
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
            // i nomi arrivano dai token, che sono internati al parsing: il confronto per identità
            // decide quasi sempre, e equals resta per chi costruisce un nome a runtime (var(...))
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
                // l'ordine non conta: al posto liberato va l'ultima voce
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
