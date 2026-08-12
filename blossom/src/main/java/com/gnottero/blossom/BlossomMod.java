package com.gnottero.blossom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;

/**
 * Il punto di ingresso del mod.
 *
 * <p>Tiene un solo pezzo di stato: il {@link ScriptRuntime} del server attualmente caricato. In
 * singleplayer un server viene creato e distrutto ad ogni mondo aperto, quindi il runtime va
 * ricreato ogni volta e non può essere un singleton immortale.
 */
public final class BlossomMod
{
    public static final String MOD_ID = "blossomscript";
    public static final Logger LOG = LoggerFactory.getLogger("blossom");

    private static ScriptRuntime runtime = null;

    /**
     * Le forme in attesa di essere disegnate (vedi {@link com.gnottero.blossom.shape.ShapeDispatcher}).
     *
     * <p>Vive quanto il gioco e non quanto un mondo: un client che si e' annunciato resta annunciato
     * anche cambiando mondo in singleplayer, e le forme di un mondo chiuso vengono buttate a mano.
     */
    private static final com.gnottero.blossom.shape.ShapeDispatcher SHAPES = new com.gnottero.blossom.shape.ShapeDispatcher();

    public static com.gnottero.blossom.shape.ShapeDispatcher shapes()
    {
        return SHAPES;
    }

    /**
     * Le righe di testo fisse sopra la hotbar (vedi {@link com.gnottero.blossom.hud.HudBoard}).
     *
     * <p>Vive quanto il gioco e non quanto un mondo, come le forme, e per lo stesso motivo: e' uno
     * stato per giocatore, e un giocatore sopravvive al cambio di mondo in singleplayer.
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
     * Controlla che ogni simbolo dichiarato in un modulo {@code core.mc.*} esista davvero.
     *
     * <p>Dichiarare un simbolo e non registrarlo produrrebbe il peggior errore possibile: un import
     * che passa e una chiamata che fallisce piu' tardi, dicendo che la funzione non esiste. Il
     * controllo costa un'espressione vuota all'avvio, una volta sola.
     */
    private static void checkApiIsComplete()
    {
        blossom.interpreter.Expression probe = new blossom.interpreter.Expression("0");
        BlossomApi.apply(probe, "<controllo>");
        java.util.Set<String> registered = probe.getFunctionNames();
        java.util.List<String> missing = blossom.language.StdLib.moduleNames().stream()
                .filter(module -> module.startsWith("core.mc."))
                .flatMap(module -> blossom.language.StdLib.symbolsOf(module).stream()
                        .filter(symbol -> !registered.contains(symbol))
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
        // le app con "autoload": true entrano qui, prima che venga generato server_starts: se
        // arrivassero dopo, quell'evento sarebbe gia' passato e nessuna di loro potrebbe ascoltarlo
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
     * @return il runtime del server corrente, o {@code null} se nessun mondo è caricato
     */
    public static ScriptRuntime runtime()
    {
        return runtime;
    }
}
