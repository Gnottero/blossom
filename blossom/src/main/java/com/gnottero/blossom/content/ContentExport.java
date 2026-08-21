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
 * Cosa c'e' nella {@code data/} di un pack, e come portarla via.
 *
 * <p>L'esportazione esiste perche' {@code data/} e' un datapack vero, non un formato nostro: chi non
 * ha il mod puo' comunque ricevere il contenuto, e chi ce l'ha puo' controllare che quello che
 * genera sia quello che si aspetta. Uno zip e nient'altro.
 */
public final class ContentExport
{
    /** Dove finiscono gli zip: fuori dai pack, cosi' nessuno li scambia per contenuto da caricare. */
    public static final String EXPORT_DIRECTORY = ".export";

    private ContentExport()
    {
    }

    /** Una voce della {@code data/}: il percorso, e se l'ha scritta un generatore o una persona. */
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
                    return;   // l'elenco parla di se stesso, e non e' contenuto
                }
                entries.add(new Entry(relative, generated.contains(relative)));
            });
        }
        return entries;
    }

    /**
     * Zippa la {@code data/} di un pack in un datapack che sta in piedi da solo.
     *
     * @return il percorso dello zip
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
                        return FileVisitResult.CONTINUE;   // e' roba nostra, non del datapack
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
