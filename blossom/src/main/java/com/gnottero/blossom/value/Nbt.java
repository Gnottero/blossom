package com.gnottero.blossom.value;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import blossom.value.LazyListValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NullValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * La conversione da valore Blossom a NBT.
 *
 * <p>Sta qui e non dentro i valori del core apposta: NBT e' un formato di Minecraft, e il core non
 * deve conoscerlo (PIANO_LINGUAGGIO.md §1). I valori del layer di gioco che sanno serializzarsi da
 * soli implementano {@link Serializable}; tutti gli altri passano da {@link #toTag}.
 */
public final class Nbt
{
    private Nbt()
    {
    }

    /** Un valore del layer di gioco che ha una forma NBT propria. */
    public interface Serializable
    {
        Tag toTag(boolean force, RegistryAccess registries);
    }

    /**
     * @param force se {@code true}, quello che non ha una forma NBT diventa la sua stringa invece di
     *        far fallire la conversione
     */
    public static Tag toTag(Value value, boolean force, RegistryAccess registries)
    {
        if (value instanceof final Serializable serializable)
        {
            return serializable.toTag(force, registries);
        }
        if (value instanceof final NumericValue number)
        {
            return ofNumber(number);
        }
        if (value instanceof final ListValue list)
        {
            return ofList(list, force, registries);
        }
        if (value instanceof final MapValue map)
        {
            return ofMap(map, force, registries);
        }
        if (value instanceof final LazyListValue lazy)
        {
            if (!force)
            {
                throw new NBTSerializableValue.IncompatibleTypeException(value);
            }
            return StringTag.valueOf(lazy.getString());
        }
        if (value instanceof NullValue)
        {
            if (!force)
            {
                throw new NBTSerializableValue.IncompatibleTypeException(value);
            }
            return StringTag.valueOf("null");
        }
        if (value instanceof StringValue)
        {
            return StringTag.valueOf(value.getString());
        }
        if (!force)
        {
            throw new NBTSerializableValue.IncompatibleTypeException(value);
        }
        return StringTag.valueOf(value.getString());
    }

    private static Tag ofNumber(NumericValue number)
    {
        if (number.isInteger())
        {
            long asLong = number.getLong();
            return Math.abs(asLong) < Integer.MAX_VALUE - 2
                    ? IntTag.valueOf((int) asLong)
                    : LongTag.valueOf(asLong);
        }
        return DoubleTag.valueOf(number.getDouble());
    }

    private static Tag ofMap(MapValue map, boolean force, RegistryAccess registries)
    {
        CompoundTag tag = new CompoundTag();
        map.getMap().forEach((key, item) ->
        {
            if (!force && !(key instanceof StringValue))
            {
                throw new NBTSerializableValue.IncompatibleTypeException(key);
            }
            tag.put(key.getString(), toTag(item, force, registries));
        });
        return tag;
    }

    /**
     * Una lista NBT vuole elementi dello stesso tipo, mentre una lista Blossom no: se i tipi non
     * coincidono, o si allargano al piu' generale che li contiene tutti (numeri), o - solo con
     * {@code force} - diventano stringhe.
     */
    private static Tag ofList(ListValue list, boolean force, RegistryAccess registries)
    {
        List<Value> items = list.getItems();
        ListTag tag = new ListTag();
        if (items.isEmpty())
        {
            return tag;
        }
        if (items.size() == 1)
        {
            tag.add(toTag(items.get(0), force, registries));
            return tag;
        }
        List<Tag> tags = new ArrayList<>(items.size());
        items.forEach(item -> tags.add(toTag(item, force, registries)));
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        tags.forEach(item -> kinds.add(Kind.of(item)));
        if (kinds.size() == 1)
        {
            tag.addAll(tags);
            return tag;
        }
        if (kinds.contains(Kind.LIST) || kinds.contains(Kind.MAP) || kinds.contains(Kind.STRING))
        {
            if (!force)
            {
                throw new NBTSerializableValue.IncompatibleTypeException(list);
            }
            tags.forEach(item -> tag.add(StringTag.valueOf(item.asString().orElseThrow())));
            return tag;
        }
        tags.forEach(kinds.contains(Kind.DOUBLE)
                ? item -> tag.add(DoubleTag.valueOf(((NumericTag) item).doubleValue()))
                : item -> tag.add(LongTag.valueOf(((NumericTag) item).longValue())));
        return tag;
    }

    private enum Kind
    {
        INT, LONG, DOUBLE, LIST, MAP, STRING;

        private static Kind of(Tag tag)
        {
            if (tag instanceof IntTag)
            {
                return INT;
            }
            if (tag instanceof LongTag)
            {
                return LONG;
            }
            if (tag instanceof DoubleTag)
            {
                return DOUBLE;
            }
            if (tag instanceof ListTag)
            {
                return LIST;
            }
            if (tag instanceof CompoundTag)
            {
                return MAP;
            }
            return STRING;
        }
    }
}
