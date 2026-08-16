package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Un registry che e' una cartella: {@code <radice>/<nome>/<versione>/}, e dentro un progetto normale.
 *
 * <pre>
 * registry/
 * ├── vector/
 * │   ├── 1.0.0/   (blossom.json, src/)
 * │   └── 1.4.0/
 * └── torre/2.1.0/
 * </pre>
 *
 * <p>Non e' un ripiego per i test: e' la forma per chi tiene le proprie librerie su una cartella
 * condivisa o un disco di rete - nessun server da mantenere, nessuna porta aperta, contenuto
 * ispezionabile con {@code ls}. Non scarica niente, quindi {@link #fetch} restituisce la cartella
 * pubblicata; il lock ne registra comunque l'impronta, perche' una cartella condivisa la puo'
 * cambiare anche qualcun altro.
 */
public record DirectoryRegistry(Path root) implements PackRegistry
{
    @Override
    public List<Version> availableVersions(String name)
    {
        Path packDirectory = root.resolve(name);
        if (!safelyUnder(packDirectory) || !Files.isDirectory(packDirectory))
        {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(packDirectory))
        {
            List<Version> versions = new ArrayList<>();
            entries.filter(Files::isDirectory).forEach(entry ->
            {
                Version version = Version.tryParse(entry.getFileName().toString());
                // una cartella con un nome che non è una versione non è un errore: può essere
                // qualunque cosa che l'amministratore ha lasciato lì
                if (version != null && Files.isRegularFile(entry.resolve(ProjectManifest.FILE_NAME)))
                {
                    versions.add(version);
                }
            });
            return List.copyOf(versions);
        }
        catch (IOException e)
        {
            return List.of();
        }
    }

    @Override
    public Path fetch(String name, Version version) throws IOException
    {
        Path published = root.resolve(name).resolve(version.toString());
        if (!safelyUnder(published) || !Files.isDirectory(published))
        {
            throw new IOException("no '" + name + "' at version " + version + " in " + root);
        }
        return published;
    }

    /** Un nome con dentro {@code ..} uscirebbe dal registry: non è un pack, è un tentativo. */
    private boolean safelyUnder(Path candidate)
    {
        return candidate.normalize().startsWith(root.normalize());
    }

    @Override
    public String describe()
    {
        return "directory " + root;
    }
}
