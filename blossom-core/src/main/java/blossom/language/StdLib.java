package blossom.language;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The standard library organised in packages (PIANO_LINGUAGGIO.md §5).
 *
 * <p>Built-in functions stay registered once, in a flat map: it is their *visibility* that goes
 * through modules. The author writes `math.sqrt(...)` and knows where it comes from, but the call
 * pays nothing extra, because the symbol to implementation binding is resolved while the module
 * loads and not on every invocation.
 *
 * <p>Outside the modules only a tiny prelude remains: what is syntax rather than library
 * ({@code if}, {@code loop}, {@code try}, {@code return}) and {@code print}. Everything else is
 * opt-in.
 */
public final class StdLib
{
    /** the built-in modules, in the order worth reading them */
    private static final Map<String, Set<String>> MODULES = new LinkedHashMap<>();
    /** symbol to the module that exports it, to tell whoever is wrong where their name comes from */
    private static final Map<String, String> HOME_MODULE = new LinkedHashMap<>();
    /**
     * {@code module.symbol} to implementation name, for the few cases where the two differ.
     *
     * <p>Built-ins live in a flat map and the public name of a module is the key of that map:
     * {@code core.list} and {@code core.map} both export {@code get} because it is the same
     * implementation, but {@code core.http} cannot call its own {@code get}, which is another thing.
     * Instead of mangling the name read in the code ({@code http.http_get}) the module declares
     * {@code get} and it is resolved here, at load time, onto the {@code http_get} implementation.
     */
    private static final Map<String, String> IMPLEMENTATION = new LinkedHashMap<>();

    /**
     * What can be used without importing anything: control constructs, which are grammar disguised
     * as functions, and the few genuinely universal things.
     */
    public static final Set<String> PRELUDE = Set.of(
            "print", "if", "match", "try", "throw", "return", "break", "continue",
            "loop", "while", "for", "c_for", "exit", "call", "outer", "not",
            // 'fn(...)' without a name: the anonymous function, and like 'call' it is grammar
            "fn",
            // built by the tokenizer for [ ... ], { ... } and "...${...}...": never written by hand
            "l", "m", "concat");

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

        module("core.string", java.util.Map.ofEntries(
                java.util.Map.entry("str", "str"),
                java.util.Map.entry("lower", "lower"),
                java.util.Map.entry("upper", "upper"),
                java.util.Map.entry("title", "title"),
                // one single function: how many occurrences is said with a number, not a name
                java.util.Map.entry("replace", "replace"),
                java.util.Map.entry("split", "split"),
                java.util.Map.entry("encode_base64", "encode_b64"),
                java.util.Map.entry("decode_base64", "decode_b64"),
                java.util.Map.entry("hash_code", "hash_code")));

        module("core.list", java.util.Map.ofEntries(
                java.util.Map.entry("range", "range"),
                java.util.Map.entry("map", "map"),
                java.util.Map.entry("filter", "filter"),
                java.util.Map.entry("reduce", "reduce"),
                // 'sort(l)' sorts by value, 'sort(l, key)' by what the key computes: one single
                // function, with an optional second argument
                java.util.Map.entry("sort", "sort_key"),
                java.util.Map.entry("find", "first"),
                java.util.Map.entry("all", "all"),
                java.util.Map.entry("slice", "slice"),
                java.util.Map.entry("get", "get"),
                java.util.Map.entry("put", "put"),
                java.util.Map.entry("remove", "delete"),
                java.util.Map.entry("has", "has"),
                java.util.Map.entry("size", "length"),
                java.util.Map.entry("join", "join"),
                java.util.Map.entry("reverse", "reverse")));

        module("core.map", java.util.Map.of(
                "keys", "keys",
                "values", "values",
                "pairs", "pairs",
                "get", "get",
                "put", "put",
                "remove", "delete",
                "has", "has",
                "size", "length"));

        module("core.json", java.util.Map.of(
                "encode", "encode_json",
                "decode", "decode_json"));

        // 'task_join' and 'task_await' looked like two ways of waiting, and they are not: the
        // first waits for the work to finish and gives the result, the second takes the next value
        // a coroutine produced with 'yield'. The new names say so, instead of leaving it to be
        // discovered by whoever gets it wrong
        module("core.task", java.util.Map.ofEntries(
                java.util.Map.entry("start", "task"),
                java.util.Map.entry("start_in", "task_thread"),
                java.util.Map.entry("wait", "task_join"),
                java.util.Map.entry("next", "task_await"),
                java.util.Map.entry("has_next", "task_ready"),
                java.util.Map.entry("done", "task_completed"),
                java.util.Map.entry("count", "task_count"),
                java.util.Map.entry("dock", "task_dock"),
                java.util.Map.entry("send", "task_send"),
                java.util.Map.entry("value", "task_value"),
                java.util.Map.entry("synchronize", "synchronize"),
                java.util.Map.entry("sleep", "sleep"),
                java.util.Map.entry("yield", "yield")));

        module("core.io", Io.MODULE.toArray(String[]::new));

        module("core.http", Http.MODULE);

        // 'system_variable_get'/'system_variable_set' are gone: they were a global namespace shared
        // by every app of the same server, that is exactly what §1.1 of the plan removes from the
        // language. Passing data to another app is done with inter app events; keeping data is done
        // with core.io
        module("core.system", java.util.Map.ofEntries(
                java.util.Map.entry("time", "time"),
                java.util.Map.entry("unix_time", "unix_time"),
                java.util.Map.entry("convert_date", "convert_date"),
                java.util.Map.entry("measure", "profile_expr"),
                java.util.Map.entry("type", "type"),
                java.util.Map.entry("var", "var"),
                java.util.Map.entry("vars", "vars"),
                java.util.Map.entry("undef", "undef"),
                java.util.Map.entry("bool", "bool"),
                java.util.Map.entry("copy", "copy")));
    }

    private StdLib()
    {
    }

    /**
     * Declares a library module provided by whoever embeds the core; the Minecraft layer registers
     * its {@code core.mc.*} this way.
     *
     * <p>The implementations stay the ones registered on the expression: here only what the module
     * exports is declared, which is what the resolver needs.
     */
    public static void registerModule(String name, Set<String> symbols)
    {
        module(name, symbols.toArray(String[]::new));
    }

    /**
     * As above, for a module whose public names do not match the implementation names.
     *
     * <p>It is what allows renaming the surface of the language without touching a single registered
     * function: the catalog says what the thing is called, the map says where it lives.
     */
    public static void registerModule(String name, Map<String, String> symbols)
    {
        module(name, symbols);
    }

    /** Like {@link #extendModule(String, Set)}, with public names detached from implementations. */
    public static void extendModule(String name, Map<String, String> symbols)
    {
        extendModule(name, symbols.keySet());
        symbols.forEach((symbol, implementation) -> IMPLEMENTATION.put(name + '.' + symbol, implementation));
    }

    /**
     * Adds symbols to a module already declared, instead of replacing it.
     *
     * <p>Needed by a module that straddles the two layers: {@code core.io} belongs to the core, but
     * reading an NBT file or writing a datapack needs Minecraft and therefore lives in the mod. The
     * place a symbol is imported from stays a single one, since the reader should not have to know
     * which half of the program implements it, and this is the function that keeps it that way.
     *
     * @throws IllegalStateException if the module was not declared before: extending a name that
     *         does not exist almost always means it was misspelled, and creating it silently would
     *         hide the mistake until the first import that fails to resolve
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

    /** Declares a module whose public names do not match the implementation names. */
    private static void module(String name, Map<String, String> symbols)
    {
        module(name, symbols.keySet().toArray(String[]::new));
        symbols.forEach((symbol, implementation) -> IMPLEMENTATION.put(name + '.' + symbol, implementation));
    }

    private static void module(String name, String... symbols)
    {
        Set<String> exported = new LinkedHashSet<>(List.of(symbols));
        MODULES.put(name, exported);
        // the first module exporting a symbol is its "home" one: that is the name shown in the
        // suggestion when someone uses it without importing it
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
     * The name to look up symbol {@code symbol} of {@code moduleName} under among the built-ins.
     *
     * @return the implementation name, which for almost every module is the symbol itself
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

    /** The module to import in order to use {@code symbol}, or {@code null} if it is not library. */
    public static @Nullable String homeModuleOf(String symbol)
    {
        return HOME_MODULE.get(symbol);
    }

    /**
     * The prelude names the tokenizer builds on its own for {@code [...]}, {@code {...}} and
     * interpolated strings. Nobody writes them as a call, and {@code l} and {@code m} are natural
     * names for a list and a map, so they stay free as variable names.
     */
    private static final Set<String> TOKENIZER_BUILT = Set.of("l", "m", "concat");

    /**
     * {@code true} if {@code symbol} is a language name that cannot become a variable or a
     * parameter.
     *
     * <p>It is not the same set as {@link #PRELUDE}: {@code l}, {@code m} and {@code concat} are
     * tokenizer internal names, not words anyone writes, and forbidding them would take away two of
     * the handiest names there are for nothing in return.
     */
    public static boolean isReservedAsAName(String symbol)
    {
        return PRELUDE.contains(symbol) && !TOKENIZER_BUILT.contains(symbol);
    }

    public static boolean isInPrelude(String symbol)
    {
        return PRELUDE.contains(symbol);
    }
}
