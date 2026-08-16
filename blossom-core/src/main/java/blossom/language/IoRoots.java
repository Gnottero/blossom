package blossom.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Le cartelle a cui {@code core.io} può arrivare, e con che permessi.
 *
 * <p>Negli script non esistono percorsi assoluti: un amministratore dichiara delle <em>radici</em>
 * con un nome e le chiamate partono da quello - {@code io.read_text('condiviso', 'note/oggi.txt')}.
 * Ne seguono tre proprietà volute: senza dichiarazioni non si apre niente, la radice si vede nel
 * punto della chiamata, e la scrittura è un permesso a parte da concedere radice per radice.
 *
 * <p>Il percorso viene normalizzato e poi risolto davvero prima del confronto, perché {@code ..} non
 * è l'unico modo di uscire da una cartella: un link simbolico dentro la radice punta fuori senza che
 * nessun controllo testuale se ne accorga. Un {@link #contains confine} esterno facoltativo - sul
 * mod, la cartella del mondo - limita anche dove le radici stesse possono stare.
 *
 * @see Io il modulo che le usa
 */
public final class IoRoots
{
    /** Nessuna radice: {@code core.io} c'è ma non arriva da nessuna parte. */
    public static final IoRoots NONE = new IoRoots(Map.of(), Set.of());

    private final Map<String, Path> roots;
    private final Set<String> writable;
    private final @Nullable Path confinement;

    public IoRoots(Map<String, Path> roots, Set<String> writable)
    {
        this(roots, writable, null);
    }

    /**
     * @param confinement la cartella oltre la quale nessuna radice può portare, o {@code null} se non
     *     c'è un limite esterno. Sul mod è la cartella del mondo: le radici si dichiarano dentro
     *     quella e il resto del disco del server non è raggiungibile per nessuna configurazione.
     */
    public IoRoots(Map<String, Path> roots, Set<String> writable, @Nullable Path confinement)
    {
        Map<String, Path> normalized = new LinkedHashMap<>();
        roots.forEach((name, path) -> normalized.put(name, path.toAbsolutePath().normalize()));
        this.roots = Map.copyOf(normalized);
        this.writable = Set.copyOf(writable);
        this.confinement = confinement == null ? null : confinement.toAbsolutePath().normalize();
    }

    /** @return se {@code directory} sta dentro il confine, o se non c'è nessun confine da rispettare */
    public boolean contains(Path directory)
    {
        return confinement == null || directory.toAbsolutePath().normalize().startsWith(confinement);
    }

    public List<String> names()
    {
        return roots.keySet().stream().sorted().toList();
    }

    public boolean isWritable(String name)
    {
        return writable.contains(name);
    }

    /**
     * Il file indicato da {@code relative} dentro la radice {@code name}.
     *
     * @param forWriting se l'operazione modifica qualcosa: una radice di sola lettura fallisce qui
     * @throws BlossomRuntimeError se la radice non esiste, non è scrivibile, o il percorso esce
     */
    public Path resolve(String name, String relative, boolean forWriting)
    {
        Path root = roots.get(name);
        if (root == null)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    roots.isEmpty()
                            ? "No io root is configured on this server: core.io cannot open anything."
                                    + " Roots are declared in the mod configuration."
                            : "Unknown io root: '" + name + "'. The configured ones are " + names() + ".",
                    Suggestions.closestTo(name, names()));
        }
        if (forWriting && !writable.contains(name))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Root '" + name + "' is read-only: to write to it, declare it writable in the mod"
                            + " configuration.");
        }
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || !contains(candidate))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Path '" + relative + "' escapes root '" + name + "'.");
        }
        return checkReal(root, candidate, name, relative);
    }

    /**
     * Ricontrolla dopo aver seguito i link simbolici.
     *
     * <p>Il confronto sul percorso normalizzato ferma i {@code ..}, non un link piazzato dentro la
     * radice che punta fuori. Se il file non esiste ancora - il caso di una scrittura - si controlla
     * la cartella che lo conterrà, che invece esiste.
     */
    private Path checkReal(Path root, Path candidate, String name, String relative)
    {
        try
        {
            Path realRoot = root.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing))
            {
                existing = existing.getParent();
            }
            Path real = existing == null ? null : existing.toRealPath();
            if (real != null && (!real.startsWith(realRoot) || !contains(real) || !contains(realRoot)))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                        "Path '" + relative + "' escapes root '" + name + "' through a symbolic link.");
            }
        }
        catch (IOException e)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "io",
                    "Cannot verify '" + relative + "' in root '" + name + "': " + e.getMessage());
        }
        return candidate;
    }
}
