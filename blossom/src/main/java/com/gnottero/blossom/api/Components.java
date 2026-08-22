package com.gnottero.blossom.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.value.NBTSerializableValue;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.MapValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * An item's data components (inventory entries SC-1 and SC-2).
 *
 * <p>Since 1.20.5 an item is an id plus a map of typed components, each with its own codec. Reading
 * them from raw NBT works for whatever is in the NBT, but it doesn't say which components exist,
 * doesn't distinguish an absent value from one equal to the default, and doesn't validate anything
 * on write. Here the codec is available: a value that doesn't make sense for that component is
 * rejected instead of producing a broken item.
 *
 * <p>For each component, the value the item has "of its own" (the max damage of an axe) is kept
 * distinct from the one carried by that particular stack: without that, there is no way to tell
 * whether an enchantment is really on that axe or on all of them.
 */
public final class Components
{
    /** The symbols added to {@code core.mc.item} */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "item_components", "item_component", "set_item_component", "component_list");

    private Components()
    {
    }

    public static void apply(Expression expression)
    {
        /** component_list() - the ids of every component this game knows about. */
        expression.addContextFunction("component_list", 0, (c, t, lv) ->
                blossom.value.ListValue.wrap(registry((ScriptContext) c).listElements()
                        .map(reference -> StringValue.of(reference.key().identifier().toString()))));

        /*
         * item_components(item, default?) - all components, id -> value.
         *
         * With 'default' true, the components read are those of the item as such rather than of this
         * particular stack: that's the comparison that tells whether a value was set on purpose or is
         * just what that item always has.
         */
        expression.addContextFunction("item_components", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new ArityError(SourcePosition.UNKNOWN, "'item_components' wants an item.");
            }
            ScriptContext cc = (ScriptContext) c;
            ItemStack stack = stackOf(cc, lv.get(0));
            if (stack.isEmpty())
            {
                return Value.NULL;
            }
            boolean defaults = lv.size() > 1 && lv.get(1).getBoolean();
            DataComponentMap components = defaults ? stack.getItem().components() : stack.getComponents();
            Registry<DataComponentType<?>> registry = registry(cc);
            Map<Value, Value> result = new LinkedHashMap<>();
            for (TypedDataComponent<?> component : components)
            {
                Identifier id = registry.getKey(component.type());
                if (id == null)
                {
                    continue; // an unregistered component has no name to give the caller
                }
                Tag encoded = encode(cc, component.type(), component.value(), id, false);
                if (encoded != null)
                {
                    result.put(StringValue.of(id.toString()), new NBTSerializableValue(encoded));
                }
            }
            return MapValue.wrap(result);
        });

        /** item_component(item, name, default?) - a single component, or null if it's not there. */
        expression.addContextFunction("item_component", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN, "'item_component' wants an item and a component name.");
            }
            ScriptContext cc = (ScriptContext) c;
            ItemStack stack = stackOf(cc, lv.get(0));
            if (stack.isEmpty())
            {
                return Value.NULL;
            }
            DataComponentType<?> type = typeOf(cc, lv.get(1));
            boolean defaults = lv.size() > 2 && lv.get(2).getBoolean();
            Object value = defaults ? stack.getItem().components().get(type) : stack.get(type);
            if (value == null)
            {
                return Value.NULL;
            }
            Tag encoded = encode(cc, type, value, registry(cc).getKey(type), true);
            return encoded == null ? Value.NULL : new NBTSerializableValue(encoded);
        });

        /*
         * set_item_component(item, name, value) - returns the item with the component changed;
         * with 'value' null it removes it.
         *
         * Returns a copy and does not modify the original: an item read from an inventory is already
         * a copy, and pretending that writing into it changes the world would be the worst lie of all.
         */
        expression.addContextFunction("set_item_component", 3, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ItemStack stack = stackOf(cc, lv.get(0));
            if (stack.isEmpty())
            {
                return Value.NULL;
            }
            DataComponentType<?> type = typeOf(cc, lv.get(1));
            Value newValue = lv.get(2);
            if (newValue.isNull())
            {
                stack.remove(type);
                return ValueConversions.of(stack, cc.registryAccess());
            }
            Tag tag = ((NBTSerializableValue) NBTSerializableValue.fromValue(newValue)).getTag();
            set(stack, type, decode(cc, type, tag, lv.get(1).getString()));
            return ValueConversions.of(stack, cc.registryAccess());
        });
    }

    /**
     * Writes the value into the component.
     *
     * <p>The raw cast is unavoidable: the component's type is only known at runtime, but the codec
     * has just verified that the value is the right one for it, so the write is safe even though the
     * compiler cannot know that.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(ItemStack stack, DataComponentType<?> type, Object value)
    {
        stack.set((DataComponentType) type, value);
    }

    private static ItemStack stackOf(ScriptContext cc, Value value)
    {
        return ValueConversions.getItemStackFromValue(value, true, cc.registryAccess());
    }

    private static Registry<DataComponentType<?>> registry(ScriptContext cc)
    {
        return cc.registryAccess().lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
    }

    private static DataComponentType<?> typeOf(ScriptContext cc, Value name)
    {
        String text = name.getString().toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(text);
        if (id == null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'" + text + "' is not a valid component id (it must look like 'namespace:name').");
        }
        return registry(cc).get(id)
                .orElseThrow(() -> new blossom.exception.UndefinedSymbolError(SourcePosition.UNKNOWN,
                        "Unknown component: '" + text + "'. The full list is component_list().",
                        blossom.exception.Suggestions.closestTo(text, knownNames(cc.registryAccess()))))
                .value();
    }

    /**
     * Encodes a component's value into NBT through its codec.
     *
     * @param strict whether to fail when the component isn't serializable. For a single component it
     *               fails (that's what the caller asked for), while listing them all it is skipped:
     *               an internal component without a codec must not make the others unreadable
     */
    private static Tag encode(ScriptContext cc, DataComponentType<?> type, Object value, Identifier id, boolean strict)
    {
        try
        {
            @SuppressWarnings("unchecked")
            Codec<Object> codec = (Codec<Object>) type.codecOrThrow();
            return codec.encodeStart(cc.registryAccess().createSerializationContext(NbtOps.INSTANCE), value)
                    .getOrThrow(error -> new IllegalStateException(error));
        }
        catch (RuntimeException e)
        {
            if (!strict)
            {
                return null;
            }
            throw new TypeMismatchError(SourcePosition.UNKNOWN, "Component '" + id
                    + "' cannot be read as data: " + e.getMessage());
        }
    }

    private static Object decode(ScriptContext cc, DataComponentType<?> type, Tag tag, String name)
    {
        try
        {
            @SuppressWarnings("unchecked")
            Codec<Object> codec = (Codec<Object>) type.codecOrThrow();
            return codec.parse(cc.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
                    .getOrThrow(error -> new IllegalStateException(error));
        }
        catch (RuntimeException e)
        {
            // this is where typed components pay off: a value that doesn't make sense is
            // rejected now, instead of producing a broken item discovered much later
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "The given value is not valid for component '" + name + "': " + e.getMessage());
        }
    }

    /** @return the names of known components, to suggest one to whoever gets it wrong */
    public static List<String> knownNames(RegistryAccess access)
    {
        return access.lookupOrThrow(Registries.DATA_COMPONENT_TYPE).listElements()
                .map(reference -> reference.key().identifier().toString())
                .sorted()
                .toList();
    }
}
