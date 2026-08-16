package blossom.interpreter;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import blossom.value.Value;

/**
 * L'ambiente che ospita gli interpreti: dove stanno i file, e i confini che valgono per tutti.
 *
 * <p>Il core non sa cosa sia un mondo Minecraft; ne implementa una versione il layer di gioco e una
 * i test, e le funzioni che escono dal processo ({@code core.io}, {@code core.http}) chiedono qui
 * cosa possono raggiungere.
 */
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

    /**
     * Gli host a cui {@code core.http} puo' parlare (vedi {@link blossom.language.HttpAccess}).
     *
     * <p>Vuoti per default, come le radici di {@code core.io}: la rete e' un permesso che un
     * amministratore concede, non qualcosa che un'app installata trova gia' acceso.
     */
    public blossom.language.HttpAccess httpAccess()
    {
        return blossom.language.HttpAccess.NONE;
    }
}
