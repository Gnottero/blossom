package com.gnottero.blossom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;

/**
 * The entry point of the mod.
 *
 * <p>It keeps one single piece of state: the {@link ScriptRuntime} of the currently loaded server.
 * In singleplayer a server is created and destroyed with every world opened, so the runtime has to
 * be rebuilt every time and cannot be an immortal singleton.
 */
public final class BlossomMod
{
    public static final String MOD_ID = "blossomscript";
    public static final Logger LOG = LoggerFactory.getLogger("blossom");

    private static ScriptRuntime runtime = null;

    /**
     * The shapes waiting to be drawn (see {@link com.gnottero.blossom.shape.ShapeDispatcher}).
     *
     * <p>It lives as long as the game and not as long as a world: a client that announced itself
     * stays announced across a world change in singleplayer, and the shapes of a closed world are
     * dropped by hand.
     */
    private static final com.gnottero.blossom.shape.ShapeDispatcher SHAPES = new com.gnottero.blossom.shape.ShapeDispatcher();

    public static com.gnottero.blossom.shape.ShapeDispatcher shapes()
    {
        return SHAPES;
    }

    /**
     * The fixed text lines above the hotbar (see {@link com.gnottero.blossom.hud.HudBoard}).
     *
     * <p>It lives as long as the game and not as long as a world, like the shapes and for the same
     * reason: it is per player state, and a player survives a world change in singleplayer.
     */
    private static final com.gnottero.blossom.hud.HudBoard HUD = new com.gnottero.blossom.hud.HudBoard();

    public static com.gnottero.blossom.hud.HudBoard hud()
    {
        return HUD;
    }

    private BlossomMod()
    {
    }

    /** Entrypoint dichiarato in {@code fabric.mod.json}, chiamato all'avvio del gioco. */
    public static void onGameStarted()
    {
        BlossomApi.declareModules();
        checkApiIsComplete();
        LOG.info("Blossom pronto");
    }

    /**
     * Checks that every symbol declared in a {@code core.mc.*} module really exists.
     *
     * <p>Declaring a symbol without registering it would produce the worst possible error: an import
     * that passes and a call that fails later, saying the function does not exist. The check costs
     * one empty expression at startup, once.
     */
    private static void checkApiIsComplete()
    {
        blossom.interpreter.Expression probe = new blossom.interpreter.Expression("0");
        BlossomApi.apply(probe, "<controllo>");
        java.util.Set<String> registered = probe.getFunctionNames();
        java.util.List<String> missing = blossom.language.StdLib.moduleNames().stream()
                .filter(module -> module.startsWith("core.mc."))
                .flatMap(module -> blossom.language.StdLib.symbolsOf(module).stream()
                        // the public name and the implementation name no longer match: the catalog
                        // says what the thing is called, the map says where it lives
                        .filter(symbol -> !registered.contains(
                                blossom.language.StdLib.implementationOf(module, symbol)))
                        .map(symbol -> module + "." + symbol))
                .sorted()
                .toList();
        if (!missing.isEmpty())
        {
            LOG.error("Game API incomplete, symbols declared but not registered: {}", missing);
        }
    }

    public static void onServerLoaded(MinecraftServer server)
    {
        runtime = new ScriptRuntime(server);
        // apps with "autoload": true enter here, before server_starts fires: arriving later, that
        // event would already be gone and none of them could listen to it
        runtime.loadAutoloadApps(server.createCommandSourceStack());
    }

    public static void onServerClosed()
    {
        SHAPES.clear();
        HUD.clear();
        com.gnottero.blossom.net.ServerNetwork.clear();
        if (runtime != null)
        {
            runtime.shutdown();
            runtime = null;
        }
    }

    /**
     * @return the runtime of the current server, or {@code null} if no world is loaded
     */
    public static ScriptRuntime runtime()
    {
        return runtime;
    }
}
