package blossom.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * Le cartelle a cui {@code core.io} può arrivare, e con che permessi.
 *
 * <h2>Perché non basta "una cartella per app"</h2>
 *
 * <p>{@code core.mc.file} dà a ogni app la propria cartella dati, e per quasi tutto basta. Quello
 * che non copre è il caso in cui gli script devono parlare con qualcosa che sul server c'è già: una
 * cartella condivisa fra app, i log di un altro strumento, una cartella di scambio con un processo
 * esterno. Senza un modo di farlo, chi ne ha bisogno smette di usare il linguaggio.
 *
 * <h2>Come sono fatti i confini</h2>
 *
 * <p>Non c'è nessun percorso assoluto negli script. Un amministratore dichiara delle <em>radici</em>
 * con un nome, e gli script scrivono {@code io.read_text('condiviso', 'note/oggi.txt')}: il nome
 * dice quale radice, il resto è sempre relativo a quella. Tre conseguenze volute:
 *
 * <ul>
 *   <li>senza dichiarazione non si arriva da nessuna parte — {@code core.io} importato su un server
 *       che non ha configurato niente non apre nulla;</li>
 *   <li>la radice si vede nel punto della chiamata, quindi leggere il codice basta a sapere dove
 *       finiscono i dati;</li>
 *   <li>la scrittura è un permesso a parte: una radice è di sola lettura salvo che sia dichiarata
 *       scrivibile.</li>
 * </ul>
 *
 * <p>Il percorso relativo viene normalizzato e poi <em>risolto davvero</em> prima del confronto:
 * {@code ../../etc} non è l'unico modo di uscire da una cartella, e un link simbolico piazzato
 * dentro la radice punterebbe altrove senza che nessun controllo testuale se ne accorga.
 */
public final class IoRoots
{
    /** Nessuna radice: {@code core.io} c'è ma non arriva da nessuna parte. */
    public static final IoRoots NONE = new IoRoots(Map.of(), Set.of());

    private final Map<String, Path> roots;
    private final Set<String> writable;

    public IoRoots(Map<String, Path> roots, Set<String> writable)
    {
        Map<String, Path> normalized = new LinkedHashMap<>();
        roots.forEach((name, path) -> normalized.put(name, path.toAbsolutePath().normalize()));
        this.roots = Map.copyOf(normalized);
        this.writable = Set.copyOf(writable);
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
        if (!candidate.startsWith(root))
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
    private static Path checkReal(Path root, Path candidate, String name, String relative)
    {
        try
        {
            Path realRoot = root.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing))
            {
                existing = existing.getParent();
            }
            if (existing != null && !existing.toRealPath().startsWith(realRoot))
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
