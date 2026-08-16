package blossom.interpreter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * La tabella dei simboli di un modulo: cosa dichiara, cosa esporta, e con che alias vede gli altri.
 *
 * <p>Sta accanto ai valori (che vivono in {@code ModuleData}) e non dentro di essi perché risponde a
 * domande diverse: non "quanto vale {@code count}" ma "{@code count} è pubblico? è riassegnabile?
 * l'alias {@code vec} a che modulo punta?". Sono domande che si fanno a caricamento, una volta, e le
 * risposte non cambiano più - quindi qui non c'è nessun costo per chiamata a runtime.
 */
public final class ModuleSymbols
{
    /** alias dichiarato negli import → nome del modulo di destinazione */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();
    /** i simboli marcati {@code pub}: funzioni e binding insieme, perché lo spazio dei nomi è uno solo */
    private final Set<String> exported = ConcurrentHashMap.newKeySet();
    /** i binding di modulo dichiarati con {@code let}, e se sono {@code mut} */
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
     * Registra un binding di modulo.
     *
     * @param mutable se dichiarato {@code let mut}, cioè riassegnabile dopo l'inizializzazione
     */
    public void declareBinding(String name, boolean mutable)
    {
        bindings.put(name, mutable);
    }

    public boolean isBinding(String name)
    {
        return bindings.containsKey(name);
    }

    /** @return {@code true} se il binding esiste ed è riassegnabile */
    public boolean isMutableBinding(String name)
    {
        return Boolean.TRUE.equals(bindings.get(name));
    }
}
