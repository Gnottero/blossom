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
 * L'impronta di un albero di file, per dire se quello che c'è adesso è quello che c'era prima.
 *
 * <p>Serve al lock: una dipendenza presa dal registry viene scaricata una volta e poi riusata dalla
 * cache, e senza un'impronta non c'è modo di accorgersi che nel frattempo qualcuno ha cambiato quei
 * file. Non è una firma - non dice <em>chi</em> ha pubblicato quel codice - ma dice se è lo stesso
 * di prima, che è la domanda a cui un lock deve rispondere.
 *
 * <p>Nell'impronta entrano i percorsi relativi oltre al contenuto: due alberi con gli stessi file
 * ma con nomi diversi devono dare impronte diverse, altrimenti rinominare l'entrypoint passerebbe
 * inosservato.
 */
public final class Integrity
{
    private Integrity()
    {
    }

    /** L'impronta sha256 dell'albero radicato in {@code root}, in esadecimale minuscolo. */
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
            // ordinati per percorso relativo: l'ordine in cui il filesystem li elenca non e' stabile,
            // e un'impronta che dipende da quello cambierebbe da sola
            files = walk.filter(Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        }
        for (Path file : files)
        {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.equals(LockFile.FILE_NAME))
            {
                continue; // il lock non fa parte di quello che il lock descrive
            }
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** L'impronta sha256 di una sequenza di byte, in esadecimale minuscolo. */
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
