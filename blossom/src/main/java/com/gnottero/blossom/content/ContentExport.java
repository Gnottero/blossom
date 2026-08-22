package com.gnottero.blossom.content;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * What is in a pack's {@code data/}, and how to take it away.
 *
 * <p>Exporting exists because {@code data/} is a real datapack, not a format of our own: someone
 * without the mod can still receive the content, and someone with it can check that what it
 * generates is what they expect. A zip and nothing else.
 */
public final class ContentExport
{
    /** Where the zips end up: outside the packs, so nobody mistakes them for content to load. */
    public static final String EXPORT_DIRECTORY = ".export";

    private ContentExport()
    {
    }

    /** An entry of {@code data/}: the path, and whether a generator or a person wrote it. */
    public record Entry(String path, boolean generated)
    {
    }

    public static List<Entry> list(Path packRoot) throws IOException
    {
        Path data = packRoot.resolve(BlossomRepositorySource.DATA_DIRECTORY);
        if (!Files.isDirectory(data))
        {
            return List.of();
        }
        List<String> generated = GeneratedIndex.read(data);
        List<Entry> entries = new ArrayList<>();
        try (var walk = Files.walk(data))
        {
            walk.filter(Files::isRegularFile).sorted().forEach(file ->
            {
                String relative = data.relativize(file).toString().replace('\\', '/');
                if (relative.equals(GeneratedIndex.FILE_NAME))
                {
                    return;   // the index talks about itself, and is not content
                }
                entries.add(new Entry(relative, generated.contains(relative)));
            });
        }
        return entries;
    }

    /**
     * Zips a pack's {@code data/} into a standalone datapack.
     *
     * @return the path of the zip
     */
    public static Path export(Path packsDirectory, String app) throws IOException
    {
        Path data = packsDirectory.resolve(app).resolve(BlossomRepositorySource.DATA_DIRECTORY);
        if (!Files.isDirectory(data))
        {
            throw new IOException("'" + app + "' has no data/ folder, so there is no content to export.");
        }
        if (!Files.isRegularFile(data.resolve("pack.mcmeta")))
        {
            throw new IOException("'" + app + "' has a data/ folder without pack.mcmeta: without it the "
                    + "game does not know it is a datapack. Run '/blossom data generate " + app + "' to write one.");
        }
        Path exports = packsDirectory.resolve(EXPORT_DIRECTORY);
        Files.createDirectories(exports);
        Path zip = exports.resolve(app + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip)))
        {
            Files.walkFileTree(data, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
                {
                    String name = data.relativize(file).toString().replace('\\', '/');
                    if (name.equals(GeneratedIndex.FILE_NAME))
                    {
                        return FileVisitResult.CONTINUE;   // this is ours, not part of the datapack
                    }
                    out.putNextEntry(new ZipEntry(name));
                    Files.copy(file, out);
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return zip;
    }
}
