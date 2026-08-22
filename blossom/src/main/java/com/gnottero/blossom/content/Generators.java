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
 * The generation of a pack's content.
 *
 * <p>The model is the same as data generators: it is launched by hand with
 * {@code /blossom data generate <app>}, and what it produces are real json files inside
 * {@code data/}, which stay there and can be viewed with an editor or with {@code git diff}.
 *
 * <p>Why on command and not when the world opens: this way the generator is normal Blossom code,
 * with the server running and the whole API available, instead of running at a moment when the
 * world does not exist yet; and a slow or broken generator does not delay a world's opening.
 *
 * <p>Whoever writes a json by hand inside {@code data/} does not see it touched: the list of what
 * has been generated lives in {@code data/.blossom-generated}, and regeneration only rewrites and
 * cleans up that.
 */
public final class Generators
{
    /** The session open on the thread that is generating, or {@code null} outside a generation. */
    private static final ThreadLocal<Session> OPEN = new ThreadLocal<>();

    private Generators()
    {
    }

    /** What a generation has written, to report it to whoever issued the command. */
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

    /** The open session, or the error explaining why there is none. */
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
     * Writes a resource. {@code id} is {@code namespace:folder/name}, as in the game's commands.
     *
     * @return the relative path written
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

    /** {@code prova:recipe/spada} becomes {@code data/prova/recipe/spada.json}. */
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
     * Opens a generation, runs {@code work}, and cleans up what had been generated before and
     * this time is not any more.
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

    /** The generation writes {@code pack.mcmeta}: it is what makes {@code data/} a datapack. */
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

    /** Folders left empty after a cleanup are of no use to anyone. */
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
