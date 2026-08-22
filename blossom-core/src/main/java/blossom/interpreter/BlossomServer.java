package blossom.interpreter;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * The core's {@link ScriptServer}: it only knows where the files sit on disk.
 *
 * <p>The Minecraft layer will provide one that resolves inside the world folder; here the root is
 * explicit, so tests can run against a temporary directory with no game involved.
 */
public class BlossomServer extends ScriptServer
{
    private final @Nullable Path root;
    private blossom.language.IoRoots ioRoots = blossom.language.IoRoots.NONE;
    private blossom.language.HttpAccess httpAccess = blossom.language.HttpAccess.NONE;

    public BlossomServer(@Nullable Path root)
    {
        this.root = root;
    }

    /** Declares the roots of {@code core.io}: it's the mod (or a test) that decides which they are. */
    public void setIoRoots(blossom.language.IoRoots roots)
    {
        this.ioRoots = roots;
    }

    @Override
    public blossom.language.IoRoots ioRoots()
    {
        return ioRoots;
    }

    /** Declares the hosts of {@code core.http}: it's the mod (or a test) that decides which they are. */
    public void setHttpAccess(blossom.language.HttpAccess access)
    {
        this.httpAccess = access;
    }

    @Override
    public blossom.language.HttpAccess httpAccess()
    {
        return httpAccess;
    }

    @Override
    public Path resolveResource(String suffix)
    {
        if (root == null)
        {
            throw new UnsupportedOperationException("this interpreter has no file storage attached");
        }
        return root.resolve(suffix);
    }
}
