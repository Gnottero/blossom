package blossom.interpreter;

import java.nio.file.Path;


/**
 * The environment hosting the interpreters: where the files are, and the boundaries that apply to
 * everyone.
 *
 * <p>The core does not know what a Minecraft world is; the game layer implements one version and the
 * tests another, and the functions that leave the process ({@code core.io}, {@code core.http}) ask
 * here what they can reach.
 */
public abstract class ScriptServer
{
    public abstract Path resolveResource(String suffix);

    /**
     * The directories {@code core.io} can reach (see {@link blossom.language.IoRoots}).
     *
     * <p>Empty by default: a server that declared nothing gives scripts no disk access outside their
     * own app directory.
     */
    public blossom.language.IoRoots ioRoots()
    {
        return blossom.language.IoRoots.NONE;
    }

    /**
     * The hosts {@code core.http} can talk to (see {@link blossom.language.HttpAccess}).
     *
     * <p>Empty by default, like the {@code core.io} roots: the network is a permission an
     * administrator grants, not something an installed app finds already on.
     */
    public blossom.language.HttpAccess httpAccess()
    {
        return blossom.language.HttpAccess.NONE;
    }
}
