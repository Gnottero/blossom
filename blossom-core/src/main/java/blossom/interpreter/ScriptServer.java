package blossom.interpreter;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import blossom.value.Value;

// WIP
public abstract class ScriptServer
{
    public final Map<Value, Value> systemGlobals = new ConcurrentHashMap<>();

    public abstract Path resolveResource(String suffix);

    /**
     * Le cartelle a cui {@code core.io} puo' arrivare (vedi {@link blossom.language.IoRoots}).
     *
     * <p>Vuote per default: un server che non ha dichiarato niente non da' agli script nessun
     * accesso al disco fuori dalla cartella della propria app.
     */
    public blossom.language.IoRoots ioRoots()
    {
        return blossom.language.IoRoots.NONE;
    }
}
