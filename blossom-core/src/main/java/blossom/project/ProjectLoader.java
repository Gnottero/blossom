package blossom.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import blossom.exception.ImportError;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Legge un progetto dal disco: manifesto, dipendenze, entrypoint, e tutti i moduli raggiungibili
 * dagli import.
 *
 * <p>Fa tutto prima che una sola riga venga eseguita, che è il punto del §4 del piano: modulo
 * mancante, nome dichiarato che non combacia col percorso, livello di API sbagliato e dipendenza a
 * una versione incompatibile sono errori di caricamento, non incidenti che saltano fuori tre
 * settimane dopo perché quel ramo veniva eseguito di rado.
 *
 * <p>Gli import ciclici invece <b>sono ammessi</b>: i moduli vengono registrati tutti prima che il
 * primo corpo giri, e l'ordine di esecuzione è quello in cui la ricorsione rientra. Quello che resta
 * vietato è <em>leggere</em>, mentre un modulo si carica, un simbolo di un modulo dello stesso ciclo
 * che non ha ancora girato: lì l'errore lo dice, e la soluzione è spostare l'uso dentro una funzione.
 */
public final class ProjectLoader
{
    private ProjectLoader()
    {
    }

    /** Il risultato del caricamento: i moduli in ordine di dipendenza, il primo importato per primo. */
    public record LoadedProject(ProjectManifest manifest, ModuleName entrypoint, Map<ModuleName, ModuleUnit> modules)
    {
        /** I moduli in ordine di caricamento: un modulo compare dopo tutti quelli che importa. */
        public List<ModuleUnit> inDependencyOrder()
        {
            return new ArrayList<>(modules.values());
        }
    }

    /**
     * Un modulo letto, con il preambolo già separato dal corpo.
     *
     * @param name           il nome globale, cioè quello con cui l'host registra il modulo
     * @param importedByAlias alias dichiarato nel preambolo → nome globale del modulo importato.
     *                        Non basta il preambolo: dentro una dipendenza {@code import "vector"}
     *                        punta al modulo che di fuori si chiama {@code utils.vector}, e la
     *                        traduzione la sa solo chi ha risolto il percorso
     */
    public record ModuleUnit(ModuleName name, Path file, ModuleSource source, Map<String, String> importedByAlias)
    {
    }

    public static LoadedProject load(Path root) throws IOException
    {
        return load(root, PackEnvironment.NONE);
    }

    /**
     * Come sopra, ma sapendo cosa c'è intorno: gli altri pack installati e il registry, da cui le
     * dipendenze non a percorso vengono prese.
     */
    public static LoadedProject load(Path root, PackEnvironment environment) throws IOException
    {
        return load(root, environment, false);
    }

    /**
     * @param refreshing se ri-risolvere le dipendenze ignorando quello che {@code blossom.lock}
     *                   aveva scelto la volta scorsa
     */
    public static LoadedProject load(Path root, PackEnvironment environment, boolean refreshing) throws IOException
    {
        ProjectManifest manifest = ProjectManifest.read(root);
        SourcePosition manifestPosition = new SourcePosition(root.resolve(ProjectManifest.FILE_NAME).toString(), 1, 1);
        // prima di tutto il resto: se l'app è scritta per un'altra API, leggerne i moduli non serve
        manifest.apiLevel().requireSupportedBy(ApiLevel.CURRENT, manifest.name(), manifestPosition);

        ModuleResolver resolver = ModuleResolver.of(root, manifest, environment, refreshing);
        Path entryFile = manifest.entrypoint();
        if (!Files.isRegularFile(entryFile))
        {
            throw new IOException("The entrypoint declared in " + ProjectManifest.FILE_NAME + " does not exist: " + entryFile);
        }
        ModuleName entrypoint = ModuleName.ofPath(manifest.sourceRoot(root), entryFile);

        Map<ModuleName, ModuleUnit> loaded = new LinkedHashMap<>();
        loadInto(entrypoint, resolver, "", loaded, new ArrayDeque<>(), new LinkedHashSet<>());
        return new LoadedProject(manifest, entrypoint, Map.copyOf(loaded));
    }

    /**
     * Carica {@code name} e, prima di lui, tutto quello che importa.
     *
     * @param name     il nome del modulo nello spazio dei nomi di {@code scope}
     * @param scope    il resolver del progetto a cui il modulo appartiene
     * @param prefix   la catena di alias che porta da radice a {@code scope}, con il punto finale
     * @param chain    la catena di import che ha portato fin qui, per raccontare il ciclo
     * @param onPath   gli stessi moduli, per riconoscere il ciclo in tempo costante
     */
    private static void loadInto(
            ModuleName name,
            ModuleResolver scope,
            String prefix,
            Map<ModuleName, ModuleUnit> loaded,
            Deque<ModuleName> chain,
            Set<ModuleName> onPath)
    {
        if (blossom.language.StdLib.isBuiltinModule(name.value()))
        {
            return; // i moduli core.* non stanno su disco: sono il linguaggio
        }
        ModuleResolver.Resolution resolution = scope.resolve(name);
        ModuleName globalName = new ModuleName(prefix + resolution.prefix() + resolution.localName().value());
        if (loaded.containsKey(globalName))
        {
            return;
        }
        if (!onPath.add(globalName))
        {
            // ciclo: questo modulo lo sta già caricando qualcuno più in alto nella catena, e sarà
            // lui a inserirlo quando avrà finito. Non è un errore - vedi DECISIONI.md D-052 - ma
            // qualcuno dei due deve girare per primo, e l'ordine è quello in cui la ricorsione
            // rientra: il più interno del ciclo per primo
            return;
        }
        chain.addLast(globalName);

        ModuleUnit unit = read(globalName, resolution, scope);
        // gli import di questo file si leggono nel suo spazio dei nomi, non nel nostro: dentro una
        // dipendenza, 'math.vec' significa quello che dice il manifesto di quella dipendenza
        String nestedPrefix = prefix + resolution.prefix();
        Map<String, String> importedByAlias = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleSource.ImportDeclaration> entry : unit.source().imports().entrySet())
        {
            ModuleName target = entry.getValue().target();
            importedByAlias.put(entry.getKey(), globalNameOf(target, resolution.scope(), nestedPrefix));
            loadInto(target, resolution.scope(), nestedPrefix, loaded, chain, onPath);
        }
        unit = new ModuleUnit(unit.name(), unit.file(), unit.source(), Map.copyOf(importedByAlias));

        chain.removeLast();
        onPath.remove(globalName);
        loaded.put(globalName, unit);
    }

    /** Come si chiama, visto dalla radice, il modulo che {@code name} indica dentro {@code scope}. */
    private static String globalNameOf(ModuleName name, ModuleResolver scope, String prefix)
    {
        if (blossom.language.StdLib.isBuiltinModule(name.value()))
        {
            return name.value(); // i core.* hanno lo stesso nome ovunque: non appartengono a un progetto
        }
        ModuleResolver.Resolution resolution = scope.resolve(name);
        return prefix + resolution.prefix() + resolution.localName().value();
    }

    private static ModuleUnit read(ModuleName globalName, ModuleResolver.Resolution resolution, ModuleResolver scope)
    {
        Path file = resolution.file();
        if (!Files.isRegularFile(file))
        {
            throw new ImportError(SourcePosition.UNKNOWN,
                    "Module '" + globalName + "' not found.",
                    Suggestions.closestTo(globalName.value(), scope.knownModuleNames()));
        }
        String code;
        try
        {
            code = Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Cannot read module '" + globalName + "': " + e.getMessage());
        }
        ModuleSource source = ModuleSource.parse(file.toString(), code);
        ModuleName declared = source.declaredName();
        if (declared == null)
        {
            throw new ParseError(new SourcePosition(file.toString(), 1, 1),
                    "Missing the 'module " + resolution.localName() + ";' declaration at the top of the file.");
        }
        if (!declared.equals(resolution.localName()))
        {
            throw new ParseError(new SourcePosition(file.toString(), 1, 1),
                    "The file declares 'module " + declared + ";' but its path corresponds to module '" + resolution.localName()
                            + "'. The name must match the file's position inside the source directory.");
        }
        return new ModuleUnit(globalName, file, source, Map.of());
    }
}
