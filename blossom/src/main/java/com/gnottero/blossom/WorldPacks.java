package com.gnottero.blossom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import blossom.project.PackEnvironment;
import blossom.project.PackRegistry;
import blossom.project.ProjectManifest;

/**
 * What surrounds a pack, seen from the world: the other packs installed under
 * {@code <world>/blossom/}, and the registry when the server has one configured.
 *
 * <p>It is the answer to the "dependencies between packs" roadmap entry. A pack used to be able to
 * depend only on a relative path, which works for two projects living in the same developer
 * directory and not for two packs installed by an administrator, which sit next to each other but
 * know nothing about each other. With {@code {"pack": "name"}} the dependency is declared by name,
 * and whoever installs it does not have to invent paths.
 *
 * <p>A pack is a directory holding a {@code blossom.json}: the same definition
 * {@code /blossom script load} uses, so there are not two different ideas of what a pack is.
 */
public record WorldPacks(Path scriptDirectory, @Nullable PackRegistry registry) implements PackEnvironment
{
    @Override
    public Optional<Path> installedPack(String name)
    {
        Path candidate = scriptDirectory.resolve(name.toLowerCase(java.util.Locale.ROOT));
        // resolve() with a name like "../other" would leave the script directory: a pack can depend
        // on another pack of the world, not on any directory of the server disk
        if (!candidate.normalize().startsWith(scriptDirectory.normalize()))
        {
            return Optional.empty();
        }
        return Files.isRegularFile(candidate.resolve(ProjectManifest.FILE_NAME))
                ? Optional.of(candidate)
                : Optional.empty();
    }

    @Override
    public List<String> installedPackNames()
    {
        try (Stream<Path> entries = Files.list(scriptDirectory))
        {
            return entries
                    .filter(entry -> Files.isRegularFile(entry.resolve(ProjectManifest.FILE_NAME)))
                    .map(entry -> entry.getFileName().toString())
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
