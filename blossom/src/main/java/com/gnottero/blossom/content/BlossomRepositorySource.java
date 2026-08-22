package com.gnottero.blossom.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * The game content that a Blossom pack brings along with it.
 *
 * <p>A pack can have a {@code data/} folder with the tree of a real datapack. This source offers it
 * to the world's {@code PackRepository}, and from there on Minecraft's own loaders do all the work:
 * recipes, loot tables, tags, advancements, dynamic registries and worldgen all go through the same
 * path, validated by the real codecs. One hook instead of six, and no content category to maintain
 * by hand.
 *
 * <p>Whoever installs a pack just copies it: there is nothing to put in {@code <world>/datapacks/}.
 *
 * <p>Packs are at {@link Pack.Position#BOTTOM} and are {@code required}. At the bottom because a real
 * datapack, placed there by whoever administers the server, must be able to fix a broken recipe from
 * an app whose source they do not have: it is the only way out left to them. Required because the
 * app's code assumes its own content exists, and a {@code /datapack disable} would leave it half
 * broken.
 */
public final class BlossomRepositorySource implements RepositorySource
{
    private static final Logger LOG = LoggerFactory.getLogger("blossom");

    /** The packs folder inside the world, the same one {@code ScriptRuntime} reads. */
    private static final String SCRIPT_DIRECTORY = "blossom";

    /** The folder, inside a pack, that the game reads as a datapack. */
    public static final String DATA_DIRECTORY = "data";

    private final Path worldRoot;

    public BlossomRepositorySource(Path worldRoot)
    {
        this.worldRoot = worldRoot;
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer)
    {
        Path packs = worldRoot.resolve(SCRIPT_DIRECTORY);
        if (!Files.isDirectory(packs))
        {
            return;
        }
        try (Stream<Path> candidates = Files.list(packs))
        {
            candidates.filter(Files::isDirectory).sorted().forEach(directory ->
            {
                // service folders (the dependency cache, exported zips) are not packs, and must not
                // become content just because they happen to sit in there
                if (directory.getFileName().toString().startsWith("."))
                {
                    return;
                }
                Path data = directory.resolve(DATA_DIRECTORY);
                if (!Files.isDirectory(data))
                {
                    return;
                }
                Pack pack = create(directory.getFileName().toString(), data);
                if (pack != null)
                {
                    consumer.accept(pack);
                }
            });
        }
        catch (Exception e)
        {
            // the world still opens: a pack with broken content must not block entry, and whoever
            // checks the log finds out why
            LOG.error("Could not read the content of the Blossom packs in {}", packs, e);
        }
    }

    private static @org.jetbrains.annotations.Nullable Pack create(String name, Path data)
    {
        String id = "blossom/" + name;
        PackLocationInfo location = new PackLocationInfo(
                id, Component.literal(name), PackSource.BUILT_IN, java.util.Optional.empty());
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);
        Pack.ResourcesSupplier resources = new PathPackResources.PathResourcesSupplier(data);
        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.SERVER_DATA, selection);
        if (pack == null)
        {
            LOG.error("The pack '{}' has a data/ folder without a valid pack.mcmeta, so its content is ignored", name);
        }
        return pack;
    }

    /** A world's repository sources, with ours appended at the end. */
    public static net.minecraft.server.packs.repository.RepositorySource[] appendTo(
            net.minecraft.server.packs.repository.RepositorySource[] sources, Path datapacksDirectory)
    {
        Path world = datapacksDirectory.getParent();
        List<RepositorySource> all = new java.util.ArrayList<>(List.of(sources));
        all.add(new BlossomRepositorySource(world));
        return all.toArray(RepositorySource[]::new);
    }
}
