package blossom.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A registry that is just a directory: {@code <root>/<name>/<version>/}, holding a normal project.
 *
 * <pre>
 * registry/
 * ├── vector/
 * │   ├── 1.0.0/   (blossom.json, src/)
 * │   └── 1.4.0/
 * └── torre/2.1.0/
 * </pre>
 *
 * <p>This is not a fallback for tests: it's the right shape for anyone keeping their own libraries
 * on a shared directory or a network drive, no server to maintain, no port to open, content you can
 * inspect with {@code ls}. Nothing is downloaded, so {@link #fetch} returns the published directory
 * as-is; the lock still records its fingerprint, because a shared directory can be changed by
 * someone else too.
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
                // a directory whose name is not a version is not an error: it can be anything
                // the administrator left there
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

    /** A name containing {@code ..} would escape the registry: it's not a pack, it's an attempt. */
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
