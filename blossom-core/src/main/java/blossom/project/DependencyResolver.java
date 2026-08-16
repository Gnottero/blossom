package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import blossom.exception.CompatibilityError;
import blossom.exception.ImportError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Da dipendenza dichiarata a cartella su disco, con la versione verificata.
 *
 * <p>Per ogni dipendenza, in quest'ordine: <b>trovarla</b> (una cartella indicata, un pack installato
 * nello stesso mondo, o il registry), <b>leggerne il manifesto</b> - che dice quale versione e'
 * davvero e quali dipendenze ha lei - e <b>verificare il vincolo</b>, fermandosi se non e'
 * soddisfatto.
 *
 * <p>Il terzo punto e' il motivo per cui la classe esiste: senza, "dipendo da utils ^1.2" e' un
 * commento, e il primo segnale che la cartella accanto e' passata alla 2.0 arriva da una funzione
 * che ha cambiato firma, tre schermate piu' in la'.
 *
 * <p>La risoluzione e' <b>transitiva</b>, dentro lo spazio dei nomi di ogni dipendenza; un ciclo fra
 * progetti e' un errore, come lo e' fra moduli.
 */
public final class DependencyResolver
{
    /** Una dipendenza risolta: dove sta e cosa è risultata essere. */
    public record Resolved(Dependency declared, Path root, @Nullable Version version, ModuleResolver modules)
    {
    }

    private final PackEnvironment environment;
    /** i progetti in corso di risoluzione, per riconoscere un ciclo fra pack */
    private final Deque<Path> onPath = new ArrayDeque<>();
    /** quello che il lock diceva la volta scorsa; vuoto se non c'era o se si sta aggiornando */
    private LockFile lock = LockFile.EMPTY;
    /** quello che il lock dirà la prossima volta */
    private final Map<String, LockFile.Entry> recorded = new LinkedHashMap<>();
    /** {@code true} quando si vuole ri-risolvere ignorando quello che il lock aveva scelto */
    private final boolean refreshing;

    public DependencyResolver(PackEnvironment environment)
    {
        this(environment, false);
    }

    public DependencyResolver(PackEnvironment environment, boolean refreshing)
    {
        this.environment = environment;
        this.refreshing = refreshing;
    }

    /**
     * Risolve le dipendenze del progetto radice, tenendo conto del suo {@code blossom.lock} e
     * riscrivendolo se è cambiato.
     *
     * <p>Solo la radice ha un lock. Una dipendenza è, per definizione, roba di qualcun altro: scrivere
     * un file dentro la sua cartella - che può stare nella cache del registry o essere di sola
     * lettura - sarebbe scorretto due volte. Le scelte fatte per le sue dipendenze finiscono comunque
     * nel lock della radice, sotto la catena di alias che porta fino a loro, quindi la
     * riproducibilità è completa lo stesso.
     */
    public ModuleResolver resolveRoot(Path root, ProjectManifest manifest) throws IOException
    {
        lock = refreshing ? LockFile.EMPTY : LockFile.read(root);
        ModuleResolver resolver = resolveFor(root, manifest, "");
        new LockFile(Map.copyOf(recorded)).writeIfChanged(root);
        return resolver;
    }

    /**
     * Risolve tutte le dipendenze del progetto in {@code root} e costruisce il suo resolver di
     * moduli.
     */
    public ModuleResolver resolveFor(Path root, ProjectManifest manifest)
    {
        return resolveFor(root, manifest, "");
    }

    private ModuleResolver resolveFor(Path root, ProjectManifest manifest, String keyPrefix)
    {
        if (onPath.contains(root))
        {
            List<String> cycle = new java.util.ArrayList<>(onPath.stream().map(Path::toString).toList());
            cycle.add(root.toString());
            throw new ImportError(SourcePosition.UNKNOWN, "circular pack dependency: "
                    + String.join(" → ", cycle) + ". A pack cannot depend on itself, not even indirectly.");
        }
        onPath.addLast(root);
        try
        {
            Map<String, ModuleResolver> dependencies = new LinkedHashMap<>();
            for (Dependency declared : manifest.dependencies().values())
            {
                Resolved resolved = resolve(declared, keyPrefix + declared.alias());
                dependencies.put(normalizeAlias(declared.alias()), resolved.modules());
            }
            return ModuleResolver.of(manifest.sourceRoot(root), dependencies);
        }
        finally
        {
            onPath.removeLast();
        }
    }

    /** L'alias come lo scrive un import: minuscolo, con i trattini diventati underscore. */
    static String normalizeAlias(String alias)
    {
        return alias.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private Resolved resolve(Dependency declared, String lockKey)
    {
        Path root = switch (declared.kind())
        {
            case PATH -> requirePath(declared);
            case PACK -> requireInstalledPack(declared);
            case REGISTRY -> fetchFromRegistry(declared, lock.of(lockKey));
        };

        ProjectManifest manifest = readManifest(root);
        Version found = manifest == null ? null : manifest.version();
        checkVersion(declared, found, root, manifest == null);
        record(lockKey, declared, root, found);

        // una dipendenza senza manifesto è solo una cartella di sorgenti: non ha dipendenze sue, e
        // non c'è niente da risolvere sotto
        ModuleResolver modules = manifest == null
                ? ModuleResolver.ofSourceRoot(root)
                : resolveFor(root, manifest, lockKey + '.');
        return new Resolved(declared, root, found, modules);
    }

    /**
     * Annota nel lock cosa è stato scelto, e verifica l'impronta di quello che viene da fuori.
     *
     * <p>Una dipendenza a percorso non entra nel lock: è la cartella accanto, che si sta modificando
     * adesso, e bloccarla vorrebbe dire far fallire il progetto ad ogni salvataggio.
     */
    private void record(String lockKey, Dependency declared, Path root, @Nullable Version found)
    {
        if (declared.kind() == Dependency.Kind.PATH || found == null)
        {
            return;
        }
        String fingerprint = null;
        if (declared.kind() == Dependency.Kind.REGISTRY)
        {
            try
            {
                fingerprint = Integrity.sha256Of(root);
            }
            catch (IOException e)
            {
                throw new ImportError(SourcePosition.UNKNOWN,
                        "Cannot compute the fingerprint of '" + declared.alias() + "' in " + root + ": " + e.getMessage());
            }
            LockFile.Entry locked = lock.of(lockKey);
            // solo a parità di versione: se la versione è cambiata - perché il vincolo è stato
            // riscritto, o perché il lock è stato aggiornato - un'impronta diversa è la normalità,
            // non una manomissione
            if (locked != null && locked.sha256() != null && locked.version().equals(found)
                    && !locked.sha256().equals(fingerprint))
            {
                throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                        + "' " + found + " is no longer what " + LockFile.FILE_NAME + " recorded:"
                        + " its content changed after it was downloaded. If the change is intended, update the"
                        + " lock file; otherwise someone has tampered with the cache.");
            }
        }
        recorded.put(lockKey, new LockFile.Entry(
                declared.kind().name().toLowerCase(java.util.Locale.ROOT), found, fingerprint));
    }

    private Path requirePath(Dependency declared)
    {
        Path path = java.util.Objects.requireNonNull(declared.path());
        if (!Files.isDirectory(path))
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' points at " + path + ", which is not a directory.");
        }
        return path;
    }

    private Path requireInstalledPack(Dependency declared)
    {
        String name = java.util.Objects.requireNonNull(declared.pack());
        Optional<Path> installed = environment.installedPack(name);
        if (installed.isEmpty())
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' wants the pack '" + name + "' installed in this world, but it is not there."
                    + " Install it under the script directory, or declare it with a 'path'.",
                    Suggestions.closestTo(name, environment.installedPackNames()));
        }
        return installed.get();
    }

    private Path fetchFromRegistry(Dependency declared, LockFile.@Nullable Entry locked)
    {
        PackRegistry registry = environment.registry();
        if (registry == null)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' comes from the registry, but no registry is configured on this server."
                    + " Configure one, or declare the dependency with a 'path' or a 'pack'.");
        }
        String name = declared.targetName();
        List<Version> available = registry.availableVersions(name);
        if (available.isEmpty())
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Registry " + registry.describe()
                    + " knows no pack named '" + name + "'.");
        }
        Version best = pinned(declared, locked, available);
        if (best == null)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Registry " + registry.describe()
                    + " has '" + name + "' in versions " + available.stream().map(Version::toString).sorted().toList()
                    + ", none of which satisfies the constraint " + declared.version() + ".");
        }
        try
        {
            return registry.fetch(name, best);
        }
        catch (IOException e)
        {
            throw new ImportError(SourcePosition.UNKNOWN, "Cannot obtain '" + name + "' "
                    + best + " from " + registry.describe() + ": " + e.getMessage());
        }
    }

    /**
     * La versione da prendere: quella che il lock aveva scelto, se c'è ancora e soddisfa ancora il
     * vincolo; altrimenti la più alta compatibile.
     *
     * <p>Un lock che punta a una versione che il manifesto non accetta più non è un errore: vuol dire
     * che il vincolo è stato cambiato apposta, e la scelta va rifatta.
     */
    private @Nullable Version pinned(Dependency declared, LockFile.@Nullable Entry locked, List<Version> available)
    {
        if (locked != null && available.contains(locked.version()) && declared.version().accepts(locked.version()))
        {
            return locked.version();
        }
        return declared.version().best(available);
    }

    private static @Nullable ProjectManifest readManifest(Path root)
    {
        try
        {
            return ProjectManifest.read(root);
        }
        catch (IOException e)
        {
            return null; // niente manifesto: è una cartella di sorgenti e basta
        }
    }

    private static void checkVersion(Dependency declared, @Nullable Version found, Path root, boolean withoutManifest)
    {
        if (declared.version().isAny())
        {
            return;
        }
        if (withoutManifest)
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' requires version " + declared.version() + ", but " + root + " has no "
                    + ProjectManifest.FILE_NAME + " and therefore declares no version."
                    + " Add one, or drop the constraint.");
        }
        if (!declared.version().accepts(found))
        {
            throw new CompatibilityError(SourcePosition.UNKNOWN, "Dependency '" + declared.alias()
                    + "' (" + declared.describe() + ") is at version " + found
                    + ", which does not satisfy the constraint " + declared.version() + " declared in the manifest.");
        }
    }
}
