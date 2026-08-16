package blossom.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import blossom.exception.ImportError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * L'host minimo del core: esegue codice senza alcun ambiente di gioco intorno.
 *
 * <p>Serve a due cose - i test JUnit puri e il benchmark - ed è la base da cui il mod deriva il
 * proprio host. Conosce i moduli del progetto per nome, e per ciascuno il preambolo già letto, così
 * da poter installare gli alias di un modulo prima che il suo corpo giri.
 */
public class BlossomHost extends ScriptHost
{
    private final Map<String, Module> libraries;
    /**
     * nome del modulo → (alias → nome globale del modulo importato).
     *
     * <p>Non il preambolo così com'è: dentro una dipendenza {@code import "vector" as v} punta al
     * modulo che questo host registra come {@code utils.vector}, e la traduzione la sa solo chi ha
     * risolto i percorsi. Tenere qui il preambolo grezzo significherebbe rifare quella risoluzione
     * ad ogni accesso, e sbagliarla.
     */
    private final Map<String, Map<String, String>> importAliases;
    /** applicato ad ogni espressione appena creata: è il punto in cui il layer di gioco inietta la sua API */
    private final UnaryOperator<Expression> setup;

    public BlossomHost(@Nullable Module code, BlossomServer server, Map<String, Module> libraries,
                       Map<String, Map<String, String>> importAliases, UnaryOperator<Expression> setup)
    {
        super(code, server, Expression.LoadOverride.DEFAULT);
        this.libraries = libraries;
        this.importAliases = importAliases;
        this.setup = setup;
        if (code != null)
        {
            installAliases(code, symbolsOf(code));
        }
    }

    /**
     * Prepara un'espressione come la vede questo host: sorgente testuale, modulo di appartenenza e
     * funzioni built-in del layer che ci sta sopra.
     */
    public Expression prepare(@Nullable Module module, String code)
    {
        Expression expression = new Expression(code);
        expression.asAModule(module);
        expression.asATextSource();
        return setup.apply(expression);
    }

    @Override
    protected void onModuleRegistered(Module module, ModuleData data)
    {
        installAliases(module, data.symbols);
    }

    private void installAliases(Module module, ModuleSymbols symbols)
    {
        Map<String, String> aliases = importAliases.get(module.name());
        if (aliases == null)
        {
            return;
        }
        aliases.forEach(symbols::declareAlias);
    }

    @Override
    protected Module getModuleOrLibraryByName(String name)
    {
        if (blossom.language.StdLib.isBuiltinModule(name))
        {
            // un modulo di libreria non ha corpo da eseguire: esiste solo come spazio dei nomi
            return new Module(name, "null", true);
        }
        Module module = libraries.get(name);
        if (module == null)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Module '" + name + "' not found.",
                    Suggestions.closestTo(name, libraries.keySet()));
        }
        return module;
    }

    @Override
    protected void runModuleCode(Context c, Module module)
    {
        prepare(module, module.code()).executeAndEvaluate(c, true, loadOverrides, null);
    }

    /** Copia difensiva delle librerie note, per chi vuole costruire un host imparentato. */
    public Map<String, Module> libraries()
    {
        return new HashMap<>(libraries);
    }
}
