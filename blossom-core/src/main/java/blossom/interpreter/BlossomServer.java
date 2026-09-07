package blossom.interpreter;

/**
 * The core's {@link ScriptServer}: it only holds the io and http permissions granted to scripts.
 *
 * <p>The Minecraft layer provides its own with the roots of the world folder; this one starts with
 * no access at all, so tests declare exactly what they want reachable.
 */
public class BlossomServer extends ScriptServer
{
    private blossom.language.IoRoots ioRoots = blossom.language.IoRoots.NONE;
    private blossom.language.HttpAccess httpAccess = blossom.language.HttpAccess.NONE;

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
}
