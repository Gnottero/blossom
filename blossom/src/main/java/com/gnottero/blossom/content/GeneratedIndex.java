package com.gnottero.blossom.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of files that the generators have produced.
 *
 * <p>It serves a single purpose: knowing, at regeneration time, which files are ours. That way a
 * json written by hand in the same folder is never touched, and a generated one that is no longer
 * needed does not stay there as a ghost. Without this list the two would be indistinguishable, and
 * the only choice would be between deleting too much and never deleting at all.
 *
 * <p>A text file, one line per path: it can be read, put under version control, and if someone
 * deletes it the worst that happens is that cleanup skips a round.
 */
public final class GeneratedIndex
{
    public static final String FILE_NAME = ".blossom-generated";

    private static final String HEADER =
            "# The files below are written by '/blossom data generate'.\n"
            + "# Regenerating rewrites them, and removes the ones no longer needed.\n"
            + "# Whatever is not listed here is never touched: that is the place for hand-written files.\n";

    private GeneratedIndex()
    {
    }

    public static List<String> read(Path data)
    {
        Path file = data.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            return List.of();
        }
        try
        {
            List<String> paths = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
            {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
                {
                    paths.add(trimmed);
                }
            }
            return paths;
        }
        catch (IOException e)
        {
            // an unreadable index must not stop a generation: we start over from scratch, and at
            // worst some previously generated file stays behind
            return List.of();
        }
    }

    public static void write(Path data, List<String> paths) throws IOException
    {
        paths.sort(String::compareTo);
        Files.writeString(data.resolve(FILE_NAME), HEADER + String.join("\n", paths) + "\n",
                StandardCharsets.UTF_8);
    }
}
