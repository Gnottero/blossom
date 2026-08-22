package blossom.interpreter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The core's few global hooks into the environment that hosts it.
 *
 * <p>The core knows neither Minecraft nor the mod that embeds it: when it needs a logger or to
 * know whether the host is in debug mode, it goes through here, and the host configures this
 * class once at startup instead of being imported by the core (which is exactly the coupling the
 * extraction removes).
 */
public final class Blossom
{
    public static final Logger LOG = LoggerFactory.getLogger("blossom");

    /** Volatile: the host sets it from the startup thread, scripts read it from theirs. */
    private static volatile boolean debug = false;

    private Blossom()
    {
    }

    /**
     * In debug mode, the language's internal exceptions fill in the Java stack trace, which costs
     * performance and is only useful to whoever is debugging the interpreter itself.
     */
    public static boolean isDebugEnabled()
    {
        return debug;
    }
}
