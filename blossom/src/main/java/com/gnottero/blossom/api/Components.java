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
 * I data component di un oggetto (voci SC-1 e SC-2 dell'inventario).
 *
 * <p>Dalla 1.20.5 un oggetto non è più "id + un blocco di NBT libero": è un id più una mappa di
 * componenti tipizzati, ognuno con il proprio codec. Leggere quei dati passando dal NBT grezzo
 * funziona ancora per quello che nel NBT c'è, ma non dice quali componenti esistono, non distingue
 * un valore assente da uno uguale al default, e non valida niente quando si scrive.
 *
 * <p>Qui il codec c'è: un componente si legge e si scrive come il gioco lo intende, e un valore che
 * non ha senso per quel componente viene rifiutato al momento della scrittura invece di produrre un
 * oggetto rotto.
 *
 * <h2>Il default</h2>
 *
 * <p>Ogni componente ha un valore che l'oggetto ha "di suo" - il danno massimo di un'ascia, la
 * pila massima di una torcia - e uno che quella specifica pila porta. La distinzione serve: senza,
 * non c'è modo di sapere se un'ascia ha davvero un incantesimo o se è quello che tutte le asce di
 * quel tipo hanno.
 */
public final class Components
{
    /** i simboli aggiunti a {@code core.mc.item} */
    public static final java.util.Set<String> MODULE = java.util.Set.of(
            "item_components", "item_component", "set_item_component", "component_list");

    private Components()
    {
    }

    public static void apply(Expression expression)
    {
        /** component_list() - gli id di tutti i componenti che questo gioco conosce. */
        expression.addContextFunction("component_list", 0, (c, t, lv) ->
                blossom.value.ListValue.wrap(registry((ScriptContext) c).listElements()
                        .map(reference -> StringValue.of(reference.key().identifier().toString()))));

        /*
         * item_components(oggetto, default?) - tutti i componenti, id -> valore.
         *
         * Con 'default' a vero si leggono quelli dell'oggetto in quanto tale invece che di questa
         * pila: e' il confronto che dice se un valore e' stato messo apposta o se e' quello che
         * quell'oggetto ha sempre.
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
                    continue; // un componente non registrato non ha un nome da dare a chi legge
                }
                Tag encoded = encode(cc, component.type(), component.value(), id, false);
                if (encoded != null)
                {
                    result.put(StringValue.of(id.toString()), new NBTSerializableValue(encoded));
                }
            }
            return MapValue.wrap(result);
        });

        /** item_component(oggetto, nome, default?) - un componente solo, o null se non c'e'. */
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
         * set_item_component(oggetto, nome, valore) - restituisce l'oggetto con il componente
         * cambiato; con 'valore' null lo toglie.
         *
         * Restituisce una copia e non modifica l'originale: un oggetto letto da un inventario e'
         * gia' una copia, e far finta che scriverci dentro cambi il mondo sarebbe la bugia peggiore
         * di tutte.
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
     * Scrive il valore nel componente.
     *
     * <p>Il cast crudo e' inevitabile: il tipo del componente si conosce solo a runtime, ma il codec
     * ha appena verificato che il valore sia quello giusto per lui, quindi la scrittura e' sicura
     * anche se il compilatore non lo puo' sapere.
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
     * Codifica il valore di un componente in NBT tramite il suo codec.
     *
     * @param strict se fallire quando il componente non è serializzabile. Su un componente solo si
     *               fallisce - chi l'ha chiesto vuole quello - mentre elencandoli tutti si salta:
     *               un componente interno senza codec non deve rendere illeggibili gli altri
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
            // e' qui che i componenti tipizzati valgono la pena: un valore che non ha senso viene
            // rifiutato adesso, invece di produrre un oggetto rotto che si scopre molto dopo
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "The given value is not valid for component '" + name + "': " + e.getMessage());
        }
    }

    /** @return i nomi dei componenti noti, per suggerirne uno a chi lo sbaglia */
    public static List<String> knownNames(RegistryAccess access)
    {
        return access.lookupOrThrow(Registries.DATA_COMPONENT_TYPE).listElements()
                .map(reference -> reference.key().identifier().toString())
                .sorted()
                .toList();
    }
}
