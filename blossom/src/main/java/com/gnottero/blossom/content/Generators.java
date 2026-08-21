package com.gnottero.blossom.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;

/**
 * La generazione del contenuto di un pack.
 *
 * <p>Il modello e' quello dei data generator: si lancia a mano con
 * {@code /blossom data generate <app>}, e quello che produce sono json veri dentro {@code data/},
 * che restano li' e si guardano con un editor o con {@code git diff}.
 *
 * <p>Perche' su comando e non all'apertura del mondo: cosi' il generatore e' codice Blossom
 * normale, con il server acceso e l'API tutta disponibile, invece di girare in un momento in cui il
 * mondo non c'e' ancora; e un generatore lento o rotto non ritarda l'apertura di un mondo.
 *
 * <p>Chi scrive a mano un json dentro {@code data/} non se lo vede toccare: l'elenco di cosa e' stato
 * generato sta in {@code data/.blossom-generated}, e la rigenerazione riscrive e ripulisce solo
 * quello.
 */
public final class Generators
{
    /** La sessione aperta sul thread che sta generando, o {@code null} fuori da una generazione. */
    private static final ThreadLocal<Session> OPEN = new ThreadLocal<>();

    private Generators()
    {
    }

    /** Quello che una generazione ha scritto, per raccontarlo a chi ha dato il comando. */
    public record Report(int written, int removed)
    {
    }

    private static final class Session
    {
        private final Path data;
        private final Set<String> written = new LinkedHashSet<>();

        private Session(Path data)
        {
            this.data = data;
        }
    }

    /** La sessione aperta, o l'errore che spiega perche' non c'e'. */
    private static Session open()
    {
        Session session = OPEN.get();
        if (session == null)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                    "Content can only be written while generating it: run '/blossom data generate <app>', "
                            + "which calls the app's generate() with this module available.");
        }
        return session;
    }

    /**
     * Scrive una risorsa. {@code id} e' {@code spazio:cartella/nome}, come nei comandi del gioco.
     *
     * @return il percorso relativo scritto
     */
    public static String write(String id, String contents, String extension) throws IOException
    {
        Session session = open();
        String relative = pathOf(id, extension);
        Path file = session.data.resolve(relative);
        if (!file.normalize().startsWith(session.data.normalize()))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                    "'" + id + "' would end up outside the pack's data folder.");
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        session.written.add(relative);
        return relative;
    }

    /** {@code prova:recipe/spada} diventa {@code data/prova/recipe/spada.json}. */
    private static String pathOf(String id, String extension)
    {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1)
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                    "'" + id + "' is not a resource id: it is written 'namespace:folder/name', "
                            + "as in 'my_pack:recipe/sword'.");
        }
        String namespace = id.substring(0, colon);
        String rest = id.substring(colon + 1);
        if (rest.contains("..") || namespace.contains("/") || namespace.contains(".."))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content", "'" + id + "' is not a valid resource id.");
        }
        return "data/" + namespace + "/" + rest + extension;
    }

    /**
     * Apre una generazione, esegue {@code work}, e ripulisce cio' che era stato generato prima e
     * questa volta non lo e' piu'.
     */
    public static Report generate(Path packRoot, Runnable work) throws IOException
    {
        Path data = packRoot.resolve(BlossomRepositorySource.DATA_DIRECTORY);
        Files.createDirectories(data);
        Session session = new Session(data);
        OPEN.set(session);
        try
        {
            work.run();
        }
        finally
        {
            OPEN.remove();
        }
        writeMetadata(data);
        session.written.add("pack.mcmeta");

        List<String> before = GeneratedIndex.read(data);
        int removed = 0;
        for (String stale : before)
        {
            if (!session.written.contains(stale))
            {
                if (Files.deleteIfExists(data.resolve(stale)))
                {
                    removed++;
                }
            }
        }
        pruneEmptyDirectories(data);
        GeneratedIndex.write(data, new ArrayList<>(session.written));
        return new Report(session.written.size(), removed);
    }

    /** Il {@code pack.mcmeta} lo scrive la generazione: e' cio' che rende {@code data/} un datapack. */
    private static void writeMetadata(Path data) throws IOException
    {
        int format = net.minecraft.SharedConstants.getCurrentVersion()
                .packVersion(net.minecraft.server.packs.PackType.SERVER_DATA).major();
        String json = """
                {
                  "pack": {
                    "description": "generated by blossom",
                    "min_format": %d,
                    "max_format": %d
                  }
                }
                """.formatted(format, format);
        Files.writeString(data.resolve("pack.mcmeta"), json, StandardCharsets.UTF_8);
    }

    /** Le cartelle rimaste vuote dopo una ripulitura non servono a nessuno. */
    private static void pruneEmptyDirectories(Path data) throws IOException
    {
        try (var walk = Files.walk(data))
        {
            List<Path> directories = walk.filter(Files::isDirectory)
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
            for (Path directory : directories)
            {
                if (directory.equals(data))
                {
                    continue;
                }
                try (var entries = Files.list(directory))
                {
                    if (entries.findAny().isEmpty())
                    {
                        Files.delete(directory);
                    }
                }
            }
        }
    }
}
