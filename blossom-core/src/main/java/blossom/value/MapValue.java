package blossom.value;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;


public class MapValue extends AbstractListValue implements ContainerValueInterface, Guardable
{
    private final Map<Value, Value> map;
    /** null unless this map became reachable from a shared global, see ValueGuard */
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
     * The backing map itself, for {@link ValueGuard} to walk. Callers must hold the guard.
     */
    Map<Value, Value> rawMap()
    {
        return map;
    }

    /**
     * an entry stored into a guarded map has to be guarded too, or mutating it through the map
     * would race even though the map itself is protected
     */
    private void adopt(Value value)
    {
        ValueGuard current = guard;
        if (current != null)
        {
            ValueGuard.attach(value, current);
        }
    }

    private MapValue()
    {
        map = new HashMap<>();
    }

    public MapValue(List<Value> kvPairs)
    {
        this();
        kvPairs.forEach(this::put);
    }

    public MapValue(Set<Value> keySet)
    {
        this();
        keySet.forEach(v -> map.put(v, Value.NULL));
    }

    @Override
    public Iterator<Value> iterator()
    {
        return guardedRead(() -> new ArrayList<>(map.keySet())).iterator();
    }

    @Override
    public List<Value> unpack()
    {
        return guardedRead(() -> map.entrySet().stream().map(e -> ListValue.of(e.getKey(), e.getValue())).collect(Collectors.toList()));
    }

    @Override
    public String getString()
    {
        return guardedRead(() -> "{" + map.entrySet().stream().map(p -> p.getKey().getString() + ": " + p.getValue().getString()).collect(Collectors.joining(", ")) + "}");
    }

    @Override
    public String getPrettyString()
    {
        return guardedRead(() ->
        {
            if (map.size() < 6)
            {
                return "{" + map.entrySet().stream().map(p -> p.getKey().getPrettyString() + ": " + p.getValue().getPrettyString()).collect(Collectors.joining(", ")) + "}";
            }
            List<Value> keys = new ArrayList<>(map.keySet());
            int max = keys.size();
            return "{" + keys.get(0).getPrettyString() + ": " + map.get(keys.get(0)).getPrettyString() + ", " +
                    keys.get(1).getPrettyString() + ": " + map.get(keys.get(1)).getPrettyString() + ", ..., " +
                    keys.get(max - 2).getPrettyString() + ": " + map.get(keys.get(max - 2)).getPrettyString() + ", " +
                    keys.get(max - 1).getPrettyString() + ": " + map.get(keys.get(max - 1)).getPrettyString() + "}";
        });
    }

    @Override
    public boolean getBoolean()
    {
        return !guardedRead(map::isEmpty);
    }

    @Override
    public Value clone()
    {
        // shares the backing map on purpose, so the copy has to share the guard as well
        MapValue copy = new MapValue(map);
        copy.guard = guard;
        return copy;
    }

    @Override
    public Value deepcopy()
    {
        return guardedRead(() ->
        {
            Map<Value, Value> copyMap = new HashMap<>();
            map.forEach((key, value) -> copyMap.put(key.deepcopy(), value.deepcopy()));
            return new MapValue(copyMap); // a fresh structure, nothing else can see it yet
        });
    }

    private MapValue(Map<Value, Value> other)
    {
        map = other;
    }

    public static MapValue wrap(Map<Value, Value> other)
    {
        return new MapValue(other);
    }

    @Override
    public Value add(Value o)
    {
        // the other map is snapshot before taking our own guard: locking two maps at once could
        // deadlock against a thread doing the same addition the other way around
        Map<Value, Value> otherEntries = o instanceof final MapValue mapValue ? mapValue.getMap() : null;
        Map<Value, Value> newItems = guardedRead(() -> new HashMap<>(map));
        if (otherEntries != null)
        {
            newItems.putAll(otherEntries);
        }
        else if (o instanceof AbstractListValue alv)
        {
            for (Value value : alv)
            {
                newItems.put(value, Value.NULL);
            }
        }
        else
        {
            newItems.put(o, Value.NULL);
        }
        return MapValue.wrap(newItems);
    }

    @Override
    public Value subtract(Value v)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Cannot subtract from a map value");
    }

    @Override
    public Value multiply(Value v)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Cannot multiply with a map value");
    }

    @Override
    public Value divide(Value v)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Cannot divide a map value");
    }

    public void put(Value v)
    {
        if (!(v instanceof final ListValue pair))
        {
            append(v);
            return;
        }
        List<Value> kv = pair.getItems();
        if (kv.size() != 2)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Map constructor requires elements that have two items");
        }
        put(kv.get(0), kv.get(1));
    }

    @Override
    public void append(Value v)
    {
        adopt(v);
        guardedWrite(() -> map.put(v, Value.NULL));
    }

    @Override
    public int compareTo(Value o)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Cannot compare with a map value");
    }

    @Override
    public boolean equals(Object o)
    {
        // both sides are snapshot through getMap(), which never holds two guards at once
        return o instanceof final MapValue mapValue && getMap().equals(mapValue.getMap());
    }

    /**
     * The entries of this map. A guarded map hands out a snapshot instead of its backing map, so
     * that callers iterating it cannot trip over another thread's mutation - no caller in the code
     * base modifies a map through this method.
     */
    public Map<Value, Value> getMap()
    {
        return guard == null ? map : guardedRead(() -> new HashMap<>(map));
    }

    public void extend(List<Value> subList)
    {
        subList.forEach(this::put);
    }

    @Override
    public int length()
    {
        return guardedRead(map::size);
    }

    @Override
    public Value in(Value value)
    {
        return guardedRead(() -> map.containsKey(value)) ? value : Value.NULL;
    }

    @Override
    public Value slice(long from, Long to)
    {
        throw new TypeMismatchError(SourcePosition.UNKNOWN,"Cannot slice a map value");
    }

    @Override
    public Value split(Value delimiter)
    {
        throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "map", "Cannot split a map value");
    }

    @Override
    public double readDoubleNumber()
    {
        return guardedRead(map::size);
    }

    @Override
    public Value get(Value v2)
    {
        return guardedRead(() -> map.getOrDefault(v2, Value.NULL));
    }

    @Override
    public boolean has(Value where)
    {
        return guardedRead(() -> map.containsKey(where));
    }

    @Override
    public boolean delete(Value where)
    {
        return guardedRead(() -> map.remove(where) != null);
    }

    @Override
    public boolean put(Value key, Value value)
    {
        adopt(key);
        adopt(value);
        return guardedRead(() -> map.put(key, value) != null);
    }

    @Override
    public String getTypeString()
    {
        return "map";
    }

    @Override
    public int hashCode()
    {
        return guardedRead(map::hashCode);
    }


    @Override
    public JsonElement toJson()
    {
        JsonObject jsonMap = new JsonObject();
        Map<Value, Value> snapshot = getMap();
        List<Value> keys = new ArrayList<>(snapshot.keySet());
        Collections.sort(keys);
        keys.forEach(k -> jsonMap.add(k.getString(), snapshot.get(k).toJson()));
        return jsonMap;
    }
}
