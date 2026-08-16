package blossom.interpreter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * I pochi agganci globali del core verso l'ambiente che lo ospita.
 *
 * <p>Il core non conosce né Minecraft né il mod che lo incorpora: quando gli serve un logger o
 * sapere se l'host è in modalità debug passa da qui, e l'host configura questa classe una volta
 * all'avvio invece di essere importato dal core (che è esattamente l'accoppiamento che l'estrazione
 * elimina).
 */
public final class Blossom
{
    public static final Logger LOG = LoggerFactory.getLogger("blossom");

    /** volatile: l'host la imposta dal thread di avvio, gli script la leggono dai loro */
    private static volatile boolean debug = false;

    private Blossom()
    {
    }

    /**
     * In debug le eccezioni interne del linguaggio riempiono lo stack trace java, che costa e serve
     * solo a chi sta debuggando l'interprete stesso.
     */
    public static boolean isDebugEnabled()
    {
        return debug;
    }
}
