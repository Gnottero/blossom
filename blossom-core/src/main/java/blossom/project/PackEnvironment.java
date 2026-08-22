package blossom.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * What surrounds a project: the other packs installed and, if configured, a registry.
 *
 * <p>The core doesn't know what a Minecraft world is. It knows there is a place where packs are
 * already installed and that someone can list them: that someone is the mod, which passes in the
 * {@code <world>/blossom/} directory here. Outside the game (in tests, or embedding the core
 * elsewhere) there is {@link #NONE}, and then only path dependencies exist.
 */
public interface PackEnvironment
{
    // No packs installed around, no registry: only local path dependencies.
    PackEnvironment NONE = new PackEnvironment()
    {
        @Override
        public Optional<Path> installedPack(String name)
        {
            return Optional.empty();
        }

        @Override
        public List<String> installedPackNames()
        {
            return List.of();
        }

        @Override
        public @Nullable PackRegistry registry()
        {
            return null;
        }
    };

    /** The directory of pack {@code name} if it's already installed. */
    Optional<Path> installedPack(String name);

    /** The installed packs, to suggest a name to whoever wrote one that doesn't exist. */
    List<String> installedPackNames();

    /** The configured registry, or {@code null} if this server has none. */
    @Nullable PackRegistry registry();
}
