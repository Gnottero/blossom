package blossom.project;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Una dipendenza dichiarata in {@code blossom.json}, prima che venga risolta in una cartella.
 *
 * <p>Ci sono tre modi di dire da dove viene, e sono esclusivi - una dipendenza non può essere due
 * cose insieme, perché in caso di conflitto non ci sarebbe modo di sapere quale ha vinto:
 *
 * <pre>
 * "utils":  { "path": "../utils" }                    // una cartella, di solito accanto al progetto
 * "torre":  { "pack": "torre", "version": "^1.2" }    // un pack gia' installato nello stesso mondo
 * "vector": { "version": "^2.0" }                     // dal registry configurato sul server
 * </pre>
 *
 * <p>Il vincolo di versione vale per tutte e tre. Su una dipendenza a percorso è facoltativo, e
 * quando c'è viene comunque verificato contro il manifesto trovato lì: serve ad accorgersi che la
 * cartella accanto è stata aggiornata a una versione che rompe, invece di scoprirlo da un errore
 * a runtime tre schermate più in là.
 *
 * @param alias   il nome con cui il progetto la chiama, cioè la chiave in {@code dependencies}
 * @param path    la cartella, per le dipendenze a percorso
 * @param pack    il nome del pack installato, per quelle a pack
 * @param version il vincolo di versione, {@link VersionRange#ANY} se non dichiarato
 */
public record Dependency(String alias, Kind kind, @Nullable Path path, @Nullable String pack, VersionRange version)
{
    public enum Kind
    {
        /** una cartella indicata direttamente */
        PATH,
        /** un altro pack installato nello stesso mondo */
        PACK,
        /** scaricata da un registry */
        REGISTRY
    }

    public static Dependency ofPath(String alias, Path path, VersionRange version)
    {
        return new Dependency(alias, Kind.PATH, path, null, version);
    }

    public static Dependency ofPack(String alias, String pack, VersionRange version)
    {
        return new Dependency(alias, Kind.PACK, null, pack, version);
    }

    public static Dependency ofRegistry(String alias, String name, VersionRange version)
    {
        return new Dependency(alias, Kind.REGISTRY, null, name, version);
    }

    /** Il nome con cui la dipendenza si conosce fuori dal progetto che la usa. */
    public String targetName()
    {
        return pack != null ? pack : alias;
    }

    /** Come si legge in un messaggio di errore. */
    public String describe()
    {
        return switch (kind)
        {
            case PATH -> "path " + path;
            case PACK -> "pack '" + pack + "' installed in this world" + (version.isAny() ? "" : " " + version);
            case REGISTRY -> "registry, '" + targetName() + "' " + version;
        };
    }
}
