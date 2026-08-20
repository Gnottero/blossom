package blossom.interpreter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import blossom.project.ModuleSource;
import blossom.project.ProjectLoader;
import blossom.value.Value;

/**
 * La facciata del core: dato del codice, restituisce un valore.
 *
 * <p>È il solo punto di ingresso che un chiamante esterno deve conoscere per eseguire codice
 * Blossom senza Minecraft. Un'istanza tiene il proprio host, quindi le variabili globali e le
 * funzioni definite sopravvivono tra le chiamate a {@link #eval(String)} - che è quello che serve a
 * un REPL, a un test e alla riga di comando del mod.
 */
public final class Interpreter
{
    private final BlossomHost host;

    private Interpreter(BlossomHost host)
    {
        this.host = host;
    }

    /**
     * Un interprete su un host già costruito.
     *
     * <p>Serve a chi deve tenere un riferimento all'app mentre il suo codice sta ancora girando: il
     * corpo di un modulo può già registrare un evento, e chi lo registra deve poter trovare l'app a
     * cui appartiene.
     */
    public static Interpreter of(ScriptHost host)
    {
        return new Interpreter((BlossomHost) host);
    }

    /**
     * Un interprete isolato: nessun accesso al disco, nessuna libreria importabile, solo il
     * linguaggio.
     */
    public static Interpreter standalone()
    {
        return builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Carica un progetto: manifesto, entrypoint e tutti i moduli raggiungibili dagli import.
     *
     * <p>I moduli vengono eseguiti in ordine di dipendenza - un modulo gira dopo tutti quelli che
     * importa - così quando il corpo di un modulo usa {@code alias.simbolo} quel simbolo esiste già.
     * Modulo mancante, nome che non combacia col percorso e import ciclico sono gia' stati
     * intercettati dal loader, prima che una riga venisse eseguita.
     */
    public static Interpreter forProject(Path root) throws IOException
    {
        return forProject(root, UnaryOperator.identity());
    }

    /**
     * Come {@link #forProject(Path)}, ma con altre funzioni built-in installate su ogni espressione
     * del progetto: è così che il layer Minecraft dà la propria API anche ai progetti multi-file, e
     * non solo alle app in un file solo.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup) throws IOException
    {
        return forProject(root, setup, Context::new);
    }

    /**
     * Come sopra, ma il contesto in cui i moduli girano lo decide il chiamante: al layer di gioco
     * serve per dire agli script chi li ha caricati, visto che il corpo di un modulo può già
     * chiamare funzioni che hanno bisogno di un mondo.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory) throws IOException
    {
        return forProject(root, setup, contextFactory, blossom.project.PackEnvironment.NONE);
    }

    /**
     * Come sopra, sapendo cosa c'è intorno al progetto: gli altri pack installati e il registry, da
     * cui vengono le dipendenze che non sono percorsi locali.
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory,
            blossom.project.PackEnvironment environment) throws IOException
    {
        return forProject(root, setup, contextFactory, environment, false);
    }

    /**
     * @param refreshingLock se rifare la scelta delle versioni delle dipendenze invece di riusare
     *                       quella scritta in {@code blossom.lock}
     */
    public static Interpreter forProject(Path root, UnaryOperator<Expression> setup,
            java.util.function.Function<ScriptHost, Context> contextFactory,
            blossom.project.PackEnvironment environment, boolean refreshingLock) throws IOException
    {
        ProjectLoader.LoadedProject project = ProjectLoader.load(root, environment, refreshingLock);
        Map<String, Module> modules = new LinkedHashMap<>();
        Map<String, Map<String, String>> importAliases = new LinkedHashMap<>();
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            String name = unit.name().value();
            modules.put(name, new Module(name, unit.source().body(), !unit.name().equals(project.entrypoint())));
            importAliases.put(name, unit.importedByAlias());
        }
        Module entry = modules.get(project.entrypoint().value());
        // le mappe restano ordinate: l'ordine di dipendenza e' quello con cui i moduli devono
        // esistere e girare, e Map.copyOf lo perderebbe
        BlossomHost host = new BlossomHost(entry, new BlossomServer(root),
                Collections.unmodifiableMap(new LinkedHashMap<>(modules)),
                Collections.unmodifiableMap(new LinkedHashMap<>(importAliases)), setup);
        Interpreter interpreter = new Interpreter(host);
        Context context = contextFactory.apply(host);
        // l'ordine conta: la factory è il posto in cui chi incorpora il core installa i propri
        // default (nel gioco, i limiti del server), e il manifesto dell'app li sovrascrive
        if (project.manifest().watchdog() != null)
        {
            host.setWatchdog(project.manifest().watchdog());
        }
        // prima esistono tutti, con i loro alias installati, poi girano: e' quello che rende
        // possibili gli import ciclici, dove non c'e' un ordine in cui ogni modulo veda gia'
        // registrato tutto quello che importa
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            host.preRegisterModule(unit.name().value());
        }
        for (ProjectLoader.ModuleUnit unit : project.inDependencyOrder())
        {
            if (unit.name().equals(project.entrypoint()))
            {
                continue; // l'entrypoint gira per ultimo, e per intero
            }
            context.beginExecution(); // ogni modulo ha il suo budget: il corpo di uno non paga per l'altro
            host.importModule(context, unit.name().value());
        }
        interpreter.evalIn(context, entry.code());
        return interpreter;
    }

    /**
     * Valuta {@code code} nell'host di questo interprete, mantenendo lo stato tra una chiamata e
     * l'altra.
     */
    public Value eval(String code)
    {
        return evalIn(new Context(host), code);
    }

    /**
     * Come {@link #eval(String)}, ma nel contesto dato: è così che il layer di gioco passa allo
     * script chi lo ha lanciato, senza che il core debba sapere cosa sia un giocatore.
     *
     * @param context deve appartenere all'host di questo interprete
     */
    public Value evalIn(Context context, String code)
    {
        return evalIn(context, code, host.watchdog());
    }

    /**
     * Come sopra, ma con limiti di esecuzione decisi dal chiamante.
     *
     * <p>Serve a distinguere il codice che gira per conto del gioco - un evento dentro il tick, dove
     * il budget è stretto - da quello che un utente ha lanciato a mano e sta aspettando.
     */
    public Value evalIn(Context context, String code, ExecutionBudget.Policy policy)
    {
        if (context.host != host)
        {
            throw new IllegalArgumentException("the context belongs to another host");
        }
        context.beginExecution(policy); // qui comincia un'esecuzione: da qui parte il cronometro
        // anche uno snippet può avere un preambolo: senza questo, importare un modulo da riga di
        // comando sarebbe impossibile e l'unico modo di usare la libreria sarebbe scrivere un file
        ModuleSource source = ModuleSource.parse(host.main == null ? "<code>" : host.main.name(), code);
        ModuleSymbols symbols = host.symbolsOf(host.main);
        source.imports().forEach((alias, declaration) -> symbols.declareAlias(alias, declaration.target().value()));
        for (blossom.project.ModuleName imported : source.importedModules())
        {
            if (!blossom.language.StdLib.isBuiltinModule(imported.value()))
            {
                host.importModule(context, imported.value());
            }
        }
        return host.prepare(host.main, source.body()).executeAndEvaluate(context, true, host.loadOverrides, null).getLeft();
    }

    public ScriptHost host()
    {
        return host;
    }

    public static final class Builder
    {
        private final Map<String, Module> libraries = new HashMap<>();
        /** nome del modulo → (alias → modulo importato): fuori da un progetto i nomi non hanno prefissi */
        private final Map<String, Map<String, String>> importAliases = new HashMap<>();
        private @Nullable Path storageRoot = null;
        private @Nullable Module main = null;
        private UnaryOperator<Expression> setup = UnaryOperator.identity();

        private Builder()
        {
        }

        /** Il modulo principale, se l'interprete ne esegue uno; altrimenti il codice gira anonimo. */
        public Builder main(Module module)
        {
            this.main = module;
            return this;
        }

        /**
         * Rende {@code module} importabile con il suo nome, leggendone il preambolo.
         *
         * <p>Il corpo registrato è quello ripulito dal preambolo, così il modulo vede i propri alias
         * e il parser non incontra dichiarazioni che non sa valutare.
         */
        public Builder library(String name, String code)
        {
            ModuleSource source = ModuleSource.parse(name, code);
            libraries.put(name, new Module(name, source.body(), true));
            importAliases.put(name, aliasesOf(source));
            return this;
        }

        /** Dove l'interprete può leggere e scrivere file; senza, ogni accesso al disco fallisce. */
        public Builder storage(Path root)
        {
            this.storageRoot = root;
            return this;
        }

        /**
         * Registra funzioni built-in aggiuntive su ogni espressione dell'host: è l'aggancio con cui
         * il layer Minecraft installa la propria API.
         */
        public Builder withApi(UnaryOperator<Expression> setup)
        {
            UnaryOperator<Expression> previous = this.setup;
            this.setup = expression -> setup.apply(previous.apply(expression));
            return this;
        }

        public Interpreter build()
        {
            return new Interpreter(new BlossomHost(main, new BlossomServer(storageRoot),
                    Collections.unmodifiableMap(new LinkedHashMap<>(libraries)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(importAliases)), setup));
        }

        private static Map<String, String> aliasesOf(ModuleSource source)
        {
            Map<String, String> aliases = new HashMap<>();
            source.imports().forEach((alias, declaration) -> aliases.put(alias, declaration.target().value()));
            return Map.copyOf(aliases);
        }
    }
}
