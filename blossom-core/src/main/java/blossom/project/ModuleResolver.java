package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Da nome di modulo a file su disco.
 *
 * <p>Un resolver rappresenta lo spazio dei nomi di <em>un</em> progetto: la sua cartella sorgenti, e
 * un resolver figlio per ogni dipendenza. Il primo segmento del nome decide dove cercare —
 * {@code utils.vector} cerca in una dipendenza chiamata {@code utils} se esiste, altrimenti nel
 * progetto stesso. Nessuna ricerca "a tentativi" su più percorsi: un nome ha una sola origine
 * possibile, così un modulo non può essere ombreggiato da un altro senza che nessuno se ne accorga.
 *
 * <p>I figli sono resolver e non semplici cartelle perché una dipendenza ha a sua volta le proprie
 * dipendenze, con i propri alias: dentro {@code utils}, {@code math.vec} significa quello che dice
 * il manifesto di {@code utils}, non quello che dice il nostro. Senza questa separazione due
 * progetti che chiamano {@code math} due librerie diverse non potrebbero convivere — che è
 * esattamente il problema che gli alias obbligatori risolvono fra moduli, portato un livello sopra.
 */
public final class ModuleResolver
{
    private final Path sourceRoot;
    private final Map<String, ModuleResolver> dependencies;

    private ModuleResolver(Path sourceRoot, Map<String, ModuleResolver> dependencies)
    {
        this.sourceRoot = sourceRoot;
        this.dependencies = dependencies;
    }

    static ModuleResolver of(Path sourceRoot, Map<String, ModuleResolver> dependencies)
    {
        return new ModuleResolver(sourceRoot, Map.copyOf(dependencies));
    }

    /**
     * Il resolver di un progetto, dipendenze comprese, tenendo conto del suo {@code blossom.lock} e
     * riscrivendolo se le scelte sono cambiate.
     *
     * @param refreshing se ri-risolvere ignorando quello che il lock aveva scelto
     */
    public static ModuleResolver of(Path projectRoot, ProjectManifest manifest, PackEnvironment environment,
            boolean refreshing) throws IOException
    {
        return new DependencyResolver(environment, refreshing).resolveRoot(projectRoot, manifest);
    }

    /** Un resolver per una sola cartella di sorgenti, senza progetto intorno. */
    public static ModuleResolver ofSourceRoot(Path sourceRoot)
    {
        return new ModuleResolver(sourceRoot, Map.of());
    }

    /**
     * Dove sta un modulo, come si chiama a casa sua, e in che spazio dei nomi vanno letti i suoi
     * import.
     *
     * @param localName il nome che il file dichiara: dentro una dipendenza è il nome senza il
     *                  prefisso della dipendenza, perché quel modulo non sa di essere una dipendenza
     *                  di qualcun altro
     * @param scope     il resolver in cui vanno risolti gli import di <em>quel</em> file
     * @param prefix    la catena di alias attraversata per arrivarci, con il punto finale: serve a
     *                  ricostruire il nome globale del modulo, che è quello con cui l'host lo
     *                  registra
     */
    public record Resolution(Path file, ModuleName localName, ModuleResolver scope, String prefix)
    {
        /** Il nome del modulo visto dalla radice del progetto. */
        public ModuleName globalName()
        {
            return prefix.isEmpty() ? localName : new ModuleName(prefix + localName.value());
        }
    }

    public Resolution resolve(ModuleName name)
    {
        String value = name.value();
        int dot = value.indexOf('.');
        if (dot > 0)
        {
            ModuleResolver dependency = dependencies.get(value.substring(0, dot));
            if (dependency != null)
            {
                Resolution nested = dependency.resolve(new ModuleName(value.substring(dot + 1)));
                return new Resolution(nested.file(), nested.localName(), nested.scope(),
                        value.substring(0, dot + 1) + nested.prefix());
            }
        }
        return new Resolution(name.toPath(sourceRoot), name, this, "");
    }

    /** Tutti i moduli effettivamente presenti, per suggerire un nome quando quello scritto non esiste. */
    public List<String> knownModuleNames()
    {
        List<String> names = new ArrayList<>(namesUnder(sourceRoot, ""));
        dependencies.forEach((alias, resolver) -> resolver.collectNames(alias + ".", names));
        return names;
    }

    private void collectNames(String prefix, List<String> into)
    {
        into.addAll(namesUnder(sourceRoot, prefix));
        dependencies.forEach((alias, resolver) -> resolver.collectNames(prefix + alias + ".", into));
    }

    private static List<String> namesUnder(Path root, String prefix)
    {
        if (!Files.isDirectory(root))
        {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root))
        {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(ModuleName.EXTENSION))
                    .map(path -> prefix + ModuleName.ofPath(root, path).value())
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
