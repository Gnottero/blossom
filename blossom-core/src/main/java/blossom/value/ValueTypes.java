package blossom.value;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The type names that can be written in a function's signature.
 *
 * <p>A type here is just a name, and matching is done against {@link Value#getTypeString()}. This
 * isn't a shortcut: it's what lets the game layer add its own types ({@code entity}, {@code block},
 * {@code item}) without the core knowing they exist, it just registers the name, and signatures
 * accept it.
 *
 * <p>Aliases exist because internal names are longer than what you'd want to type: you write
 * {@code num}, not {@code number}. The function type is called {@code func}, not {@code fn},
 * because {@code fn} is the keyword a function is declared with, and the tokenizer recognizes it as
 * such even inside a signature.
 *
 * <p>An unregistered name is an error <em>at the function's definition</em>, not on its first call:
 * a misspelled type is a typo, and it should be reported right away.
 */
public final class ValueTypes
{
    /** writable name → canonical type, i.e. what {@code getTypeString()} returns */
    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    /** accepts anything: used to document a parameter without constraining it */
    public static final String ANY = "any";

    static
    {
        register("num", "number");
        register("str", "string");
        register("bool", "boolean");
        register("list", "list");
        register("map", "map");
        // 'func' and not 'fn': 'fn' is the keyword a function is declared with, and the
        // tokenizer recognizes it as such even inside a signature
        register("func", "function");
        register("null", "null");
        BY_NAME.put(ANY, ANY);
        // canonical names are also valid writable names: whoever prefers 'number' to 'num'
        // doesn't have to remember which of the two is the right one
        register("number", "number");
        register("string", "string");
        register("boolean", "boolean");
        register("function", "function");
    }

    private ValueTypes()
    {
    }

    /**
     * Declares a type name usable in signatures.
     *
     * @param name      how it is written
     * @param canonical what {@link Value#getTypeString()} returns for those values
     */
    public static void register(String name, String canonical)
    {
        BY_NAME.put(name, canonical);
    }

    /** The canonical type behind a name written in a signature, or {@code null} if it doesn't exist. */
    public static @Nullable String canonical(String name)
    {
        return BY_NAME.get(name);
    }

    public static Set<String> names()
    {
        return new LinkedHashSet<>(BY_NAME.keySet());
    }

    /**
     * @return whether {@code value} is acceptable for the canonical type {@code canonical}
     */
    public static boolean matches(String canonical, Value value)
    {
        return canonical.equals(ANY) || canonical.equals(value.getTypeString());
    }
}
