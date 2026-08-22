package blossom.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * The fingerprint of a file tree, to say whether what is there now is what was there before.
 *
 * <p>Used by the lock: a dependency taken from the registry is downloaded once and then reused from
 * the cache, and without a fingerprint there is no way to notice that someone changed those files in
 * the meantime. It is not a signature (it does not say <em>who</em> published that code), but it
 * says whether it is the same as before, which is the question a lock has to answer.
 *
 * <p>The fingerprint includes the relative paths as well as the content: two trees with the same
 * files but different names must produce different fingerprints, otherwise renaming the entrypoint
 * would go unnoticed.
 */
public final class Integrity
{
    private Integrity()
    {
    }

    /** The sha256 fingerprint of the tree rooted at {@code root}, in lowercase hexadecimal. */
    public static String sha256Of(Path root) throws IOException
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("sha-256 is not available on this jvm", e);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root))
        {
            // sorted by relative path: the order the filesystem lists them in is not stable, and a
            // fingerprint depending on that would change on its own
            files = walk.filter(Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        }
        for (Path file : files)
        {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.equals(LockFile.FILE_NAME))
            {
                continue; // the lock is not part of what the lock describes
            }
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** The sha256 fingerprint of a byte sequence, in lowercase hexadecimal. */
    public static String sha256Of(byte[] content)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("sha-256 is not available on this jvm", e);
        }
    }
}
