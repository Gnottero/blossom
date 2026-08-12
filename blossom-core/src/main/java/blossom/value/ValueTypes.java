package blossom.value;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * I nomi di tipo che si possono scrivere nella firma di una funzione.
 *
 * <p>Un tipo qui è solo un nome, e il confronto avviene contro {@link Value#getTypeString()}. Non è
 * una scorciatoia: è quello che permette al layer di gioco di aggiungere i propri tipi
 * ({@code entity}, {@code block}, {@code item}) senza che il core sappia che esistono — registra il
 * nome, e le firme lo accettano.
 *
 * <p>Gli alias esistono perché i nomi interni sono più lunghi di quelli che uno scrive: si dichiara
 * {@code num} e non {@code number}. Il tipo funzione si chiama {@code func} e non {@code fn} perché
 * {@code fn} è la parola chiave con cui una funzione si dichiara, e il tokenizer la riconosce come
 * tale anche dentro una firma.
 *
 * <p>Un nome non registrato è un errore <em>alla definizione</em> della funzione, non alla prima
 * chiamata: un tipo scritto male è un errore di battitura, e va detto subito.
 */
public final class ValueTypes
{
    /** nome scrivibile → tipo canonico, cioè quello che {@code getTypeString()} restituisce */
    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    /** accetta qualunque cosa: serve a documentare un parametro senza vincolarlo */
    public static final String ANY = "any";

    static
    {
        register("num", "number");
        register("str", "string");
        register("bool", "boolean");
        register("list", "list");
        register("map", "map");
        // 'func' e non 'fn': 'fn' e' la parola chiave con cui si dichiara una funzione, e il
        // tokenizer la riconosce come tale anche dentro una firma
        register("func", "function");
        register("null", "null");
        BY_NAME.put(ANY, ANY);
        // i nomi canonici valgono anche come nomi scrivibili: chi preferisce 'number' a 'num' non
        // deve ricordarsi quale dei due e' quello giusto
        register("number", "number");
        register("string", "string");
        register("boolean", "boolean");
        register("function", "function");
    }

    private ValueTypes()
    {
    }

    /**
     * Dichiara un nome di tipo usabile nelle firme.
     *
     * @param name      come lo si scrive
     * @param canonical quello che {@link Value#getTypeString()} restituisce per quei valori
     */
    public static void register(String name, String canonical)
    {
        BY_NAME.put(name, canonical);
    }

    /** Il tipo canonico dietro un nome scritto in una firma, o {@code null} se non esiste. */
    public static @Nullable String canonical(String name)
    {
        return BY_NAME.get(name);
    }

    public static Set<String> names()
    {
        return new LinkedHashSet<>(BY_NAME.keySet());
    }

    /**
     * @return se {@code value} è accettabile per il tipo canonico {@code canonical}
     */
    public static boolean matches(String canonical, Value value)
    {
        return canonical.equals(ANY) || canonical.equals(value.getTypeString());
    }
}
