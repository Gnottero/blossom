package blossom.language;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * La standard library organizzata a pacchetti (PIANO_LINGUAGGIO.md §5).
 *
 * <p>Le funzioni built-in restano registrate una volta sola, in una mappa piatta: è la loro
 * *visibilità* a passare per i moduli. Chi scrive vede `math.sqrt(...)` e sa da dove viene, ma la
 * chiamata non paga nulla in più, perché l'associazione simbolo → implementazione viene risolta
 * mentre il modulo si carica e non ad ogni invocazione.
 *
 * <p>Fuori dai moduli resta solo un prelude minuscolo: quello che è sintassi più che libreria
 * ({@code if}, {@code loop}, {@code try}, {@code return}) e {@code print}. Tutto il resto è opt-in.
 */
public final class StdLib
{
    /** i moduli built-in, nell'ordine in cui vale la pena leggerli */
    private static final Map<String, Set<String>> MODULES = new LinkedHashMap<>();
    /** simbolo → modulo che lo esporta, per dire a chi sbaglia da dove viene il nome che ha usato */
    private static final Map<String, String> HOME_MODULE = new LinkedHashMap<>();
    /**
     * {@code modulo.simbolo} → nome dell'implementazione, per i pochi casi in cui i due differiscono.
     *
     * <p>Le built-in vivono in una mappa piatta e il nome pubblico di un modulo è la chiave di quella
     * mappa: {@code core.list} e {@code core.map} esportano entrambi {@code get} perché è la stessa
     * implementazione, ma {@code core.http} non può chiamare {@code get} la sua, che è un'altra cosa.
     * Invece di storpiare il nome che si legge nel codice ({@code http.http_get}) il modulo dichiara
     * {@code get} e lo si risolve qui, a caricamento, sull'implementazione {@code http_get}.
     */
    private static final Map<String, String> IMPLEMENTATION = new LinkedHashMap<>();

    /**
     * Quello che si può usare senza importare niente: costrutti di controllo, che sono grammatica
     * travestita da funzione, e le poche cose davvero universali.
     */
    public static final Set<String> PRELUDE = Set.of(
            "print", "if", "try", "throw", "return", "break", "continue",
            "loop", "while", "for", "c_for", "exit", "call", "outer", "not",
            // 'fn(...)' senza nome: e' la funzione anonima, e come 'call' e' grammatica
            "fn",
            // costruiti dal tokenizer per [ ... ] e { ... }: non si scrivono a mano
            "l", "m");

    static
    {
        module("core.math",
                "abs", "sign", "round", "floor", "ceil", "sqrt", "exp", "ln", "ln1p", "log", "log10", "log1p",
                "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "sinh", "cosh", "tanh", "asinh", "acosh",
                "atanh", "sec", "sech", "csc", "csch", "cot", "coth", "deg", "rad",
                "max", "min", "fact", "relu", "number", "rand", "reset_seed",
                "perlin", "simplex", "mandelbrot",
                "acot",
                "bitwise_and", "bitwise_or", "bitwise_xor", "bitwise_not", "bitwise_popcount",
                "bitwise_shift_left", "bitwise_shift_right", "bitwise_arithmetic_shift_right",
                "bitwise_roll_left", "bitwise_roll_right",
                "long_to_double_bits", "double_to_long_bits");

        module("core.string",
                "str", "lower", "upper", "title", "replace", "replace_first", "split", "join",
                "encode_b64", "decode_b64", "hash_code");

        module("core.list",
                "range", "map", "filter", "reduce", "sort", "sort_key", "first", "all",
                "slice", "get", "put", "delete", "has", "length", "join", "split", "reverse");

        module("core.map",
                "keys", "values", "pairs", "get", "put", "delete", "has", "length");

        module("core.json",
                "encode_json", "decode_json");

        module("core.task",
                "task", "task_join", "task_await", "task_completed", "task_count", "task_dock",
                "task_ready", "task_send", "task_value", "synchronize", "sleep", "yield");

        module("core.io", Io.MODULE.toArray(String[]::new));

        module("core.http", Http.MODULE);

        module("core.system",
                "time", "unix_time", "convert_date", "profile_expr", "type", "var", "vars",
                "system_variable_get", "system_variable_set", "undef", "bool", "copy");
    }

    private StdLib()
    {
    }

    /**
     * Dichiara un modulo di libreria fornito da chi incorpora il core - il layer Minecraft registra
     * così i suoi {@code core.mc.*}.
     *
     * <p>Le implementazioni restano quelle registrate sull'espressione: qui si dichiara solo cosa
     * il modulo esporta, che è quello che serve al resolver.
     */
    public static void registerModule(String name, Set<String> symbols)
    {
        module(name, symbols.toArray(String[]::new));
    }

    /**
     * Aggiunge simboli a un modulo gia' dichiarato, invece di sostituirlo.
     *
     * <p>Serve a un modulo che sta a cavallo dei due strati: {@code core.io} e' del core, ma leggere
     * un file NBT o scrivere un datapack ha bisogno di Minecraft e quindi vive nel mod. Il posto in
     * cui il simbolo si importa e' uno solo - chi legge non deve sapere quale meta' del programma lo
     * implementa - e questa e' la funzione che lo tiene tale.
     *
     * @throws IllegalStateException se il modulo non e' stato dichiarato prima: estendere un nome che
     *         non esiste vuol dire quasi sempre averlo scritto male, e crearlo in silenzio
     *         nasconderebbe l'errore fino al primo import che non risolve
     */
    public static void extendModule(String name, Set<String> symbols)
    {
        Set<String> exported = MODULES.get(name);
        if (exported == null)
        {
            throw new IllegalStateException("Cannot extend '" + name + "': no such built-in module.");
        }
        exported.addAll(symbols);
        symbols.forEach(symbol -> HOME_MODULE.putIfAbsent(symbol, name));
    }

    /** Dichiara un modulo i cui nomi pubblici non coincidono con quelli delle implementazioni. */
    private static void module(String name, Map<String, String> symbols)
    {
        module(name, symbols.keySet().toArray(String[]::new));
        symbols.forEach((symbol, implementation) -> IMPLEMENTATION.put(name + '.' + symbol, implementation));
    }

    private static void module(String name, String... symbols)
    {
        Set<String> exported = new LinkedHashSet<>(List.of(symbols));
        MODULES.put(name, exported);
        // il primo modulo che esporta un simbolo e' quello "di casa": e' il nome che compare nel
        // suggerimento quando qualcuno lo usa senza importarlo
        exported.forEach(symbol -> HOME_MODULE.putIfAbsent(symbol, name));
    }

    public static boolean isBuiltinModule(String moduleName)
    {
        return MODULES.containsKey(moduleName);
    }

    /** @return se {@code moduleName} esporta {@code symbol} */
    public static boolean exports(String moduleName, String symbol)
    {
        Set<String> exported = MODULES.get(moduleName);
        return exported != null && exported.contains(symbol);
    }

    /**
     * Il nome con cui cercare fra le built-in il simbolo {@code symbol} di {@code moduleName}.
     *
     * @return il nome dell'implementazione, che per quasi tutti i moduli è il simbolo stesso
     */
    public static String implementationOf(String moduleName, String symbol)
    {
        return IMPLEMENTATION.getOrDefault(moduleName + '.' + symbol, symbol);
    }

    public static Set<String> symbolsOf(String moduleName)
    {
        return MODULES.getOrDefault(moduleName, Set.of());
    }

    public static Set<String> moduleNames()
    {
        return MODULES.keySet();
    }

    /** Il modulo da importare per usare {@code symbol}, o {@code null} se non è di libreria. */
    public static @Nullable String homeModuleOf(String symbol)
    {
        return HOME_MODULE.get(symbol);
    }

    public static boolean isInPrelude(String symbol)
    {
        return PRELUDE.contains(symbol);
    }
}
