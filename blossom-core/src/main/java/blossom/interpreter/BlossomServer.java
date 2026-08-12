package blossom.interpreter;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Lo {@link ScriptServer} del core: sa solo dove stanno i file su disco.
 *
 * <p>Il layer Minecraft ne fornirà uno che risolve dentro la cartella del mondo; qui la radice è
 * esplicita, così i test girano su una directory temporanea e senza gioco.
 */
public class BlossomServer extends ScriptServer
{
    private final @Nullable Path root;
    private blossom.language.IoRoots ioRoots = blossom.language.IoRoots.NONE;

    public BlossomServer(@Nullable Path root)
    {
        this.root = root;
    }

    /** Dichiara le radici di {@code core.io}: e' il mod - o un test - a decidere quali sono. */
    public void setIoRoots(blossom.language.IoRoots roots)
    {
        this.ioRoots = roots;
    }

    @Override
    public blossom.language.IoRoots ioRoots()
    {
        return ioRoots;
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
