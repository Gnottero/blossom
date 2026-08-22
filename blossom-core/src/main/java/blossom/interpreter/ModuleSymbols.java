package blossom.interpreter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * The symbol table of a module: what it declares, what it exports, and the aliases it sees the
 * others under.
 *
 * <p>It sits beside the values (which live in {@code ModuleData}) and not inside them because it
 * answers different questions: not "what is {@code count} worth" but "is {@code count} public? is it
 * reassignable? which module does the alias {@code vec} point to?". Those are load time questions,
 * asked once, and the answers never change, so there is no per call run time cost here.
 */
public final class ModuleSymbols
{
    /** alias declared in the imports to the name of the target module */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();
    /** the symbols marked {@code pub}: functions and bindings together, since the namespace is one */
    private final Set<String> exported = ConcurrentHashMap.newKeySet();
    /** the module bindings declared with {@code let}, and whether they are {@code mut} */
    private final Map<String, Boolean> bindings = new ConcurrentHashMap<>();

    public void declareAlias(String alias, String moduleName)
    {
        aliases.put(alias, moduleName);
    }

    public @Nullable String moduleOfAlias(String alias)
    {
        return aliases.get(alias);
    }

    public Set<String> aliases()
    {
        return aliases.keySet();
    }

    public void export(String symbol)
    {
        exported.add(symbol);
    }

    public boolean isExported(String symbol)
    {
        return exported.contains(symbol);
    }

    public Set<String> exportedSymbols()
    {
        return exported;
    }

    /**
     * Registers a module binding.
     *
     * @param mutable whether declared {@code let mut}, that is reassignable after initialization
     */
    public void declareBinding(String name, boolean mutable)
    {
        bindings.put(name, mutable);
    }

    public boolean isBinding(String name)
    {
        return bindings.containsKey(name);
    }

    /** @return {@code true} if the binding exists and is reassignable */
    public boolean isMutableBinding(String name)
    {
        return Boolean.TRUE.equals(bindings.get(name));
    }
}
