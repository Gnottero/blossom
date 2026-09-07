package blossom.interpreter;

/**
 * The environment hosting the interpreters: the boundaries that apply to everyone.
 *
 * <p>The core does not know what a Minecraft world is; the game layer implements one version and the
 * tests another, and the functions that leave the process ({@code core.io}, {@code core.http}) ask
 * here what they can reach.
 */
public abstract class ScriptServer
{
    /**
     * The directories {@code core.io} can reach (see {@link blossom.language.IoRoots}).
     *
     * <p>Empty by default: a server that declared nothing gives scripts no disk access at all.
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
