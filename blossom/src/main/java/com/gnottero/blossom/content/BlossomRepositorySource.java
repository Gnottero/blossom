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
 * I contenuti di gioco che un pack Blossom porta con se'.
 *
 * <p>Un pack puo' avere una cartella {@code data/} con l'albero di un datapack vero. Questa sorgente
 * la offre al {@code PackRepository} del mondo, e da li' in poi sono i loader di Minecraft a fare
 * tutto il lavoro: ricette, loot table, tag, advancement, registri dinamici e worldgen passano tutti
 * dalla stessa strada, con i codec veri che validano. Un aggancio solo invece di sei, e nessuna
 * categoria di contenuto da sostenere a mano.
 *
 * <p>Chi installa copia un pack solo: non c'e' niente da mettere in {@code <mondo>/datapacks/}.
 *
 * <p>I pack sono in {@link Pack.Position#BOTTOM} e sono {@code required}. In fondo perche' un
 * datapack vero, messo li' da chi amministra, deve poter correggere una ricetta rotta di un'app di
 * cui non ha il sorgente: e' l'unica via d'uscita che gli resta. Obbligatori perche' il codice
 * dell'app da' per esistente il proprio contenuto, e un {@code /datapack disable} lo lascerebbe a
 * meta'.
 */
public final class BlossomRepositorySource implements RepositorySource
{
    private static final Logger LOG = LoggerFactory.getLogger("blossom");

    /** La cartella dei pack dentro il mondo, la stessa che legge {@code ScriptRuntime}. */
    private static final String SCRIPT_DIRECTORY = "blossom";

    /** La cartella, dentro un pack, che il gioco legge come datapack. */
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
            // il mondo si apre lo stesso: un pack con il contenuto rotto non deve impedire di
            // entrare, e chi guarda il log trova il perche'
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

    /** Le sorgenti del repository di un mondo, con la nostra in coda. */
    public static net.minecraft.server.packs.repository.RepositorySource[] appendTo(
            net.minecraft.server.packs.repository.RepositorySource[] sources, Path datapacksDirectory)
    {
        Path world = datapacksDirectory.getParent();
        List<RepositorySource> all = new java.util.ArrayList<>(List.of(sources));
        all.add(new BlossomRepositorySource(world));
        return all.toArray(RepositorySource[]::new);
    }
}
