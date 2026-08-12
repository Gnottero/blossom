package com.gnottero.blossom.access;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import blossom.value.MapValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * Quello che in Scarpet arrivava dal mod Carpet, riscritto per un mod che Carpet non ce l'ha.
 *
 * <p>Le funzioni che chiedevano regole di Carpet, HUD di Carpet e giocatori finti di Carpet non
 * possono restituire quei dati, perche' quei dati non esistono: qui rispondono con il vuoto giusto e
 * documentato, invece di far finta che il mod sia installato.
 */
public final class Host
{
    private Host()
    {
    }

    /** Manda un messaggio a chi ha lanciato lo script. */
    public static void message(CommandSourceStack source, Component message)
    {
        source.sendSuccess(() -> message, false);
    }

    /**
     * Le regole di Carpet: qui non ce ne sono. Restituisce una mappa vuota, cosi' uno script che
     * cerca una regola trova "niente" e non un errore.
     */
    public static MapValue getAllCarpetRules()
    {
        return MapValue.wrap(Map.of());
    }

    /** La versione del mod, letta dal manifesto Fabric. */
    public static String getCarpetVersion()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("blossomscript")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Blossom non parla con nessun client modificato: ogni giocatore e' un giocatore vanilla. */
    public static @Nullable String isModdedPlayer(Player player)
    {
        return null;
    }

    /** Lo stato di connessione: senza protocollo di rete proprio, e' sempre 'vanilla'. */
    public static String getPlayerStatus(ServerPlayer player)
    {
        return "vanilla";
    }

    public static boolean isValidPlayer(ServerPlayer player)
    {
        return !player.isRemoved();
    }

    /** Nessun profiler esterno: il blocco viene eseguito e basta. */
    public static Runnable startProfilerSection(String name)
    {
        return () ->
        {
        };
    }

    /** Blossom non ha una regola globale per saltare gli aggiornamenti di blocco. */
    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates()
    {
        return SKIP_UPDATES;
    }

    private static final ThreadLocal<Boolean> SKIP_UPDATES = ThreadLocal.withInitial(() -> false);

    public static boolean getFillUpdates()
    {
        return true;
    }

    public static void updateHuds(MinecraftServer server, List<ServerPlayer> players)
    {
        // gli HUD in cima allo schermo richiedono un pacchetto verso il client: vedi ROADMAP_FUTURA
    }

    /** Il testo formattato di {@code format(...)}. */
    public static Component Messenger_compose(Object... parts)
    {
        return com.gnottero.blossom.util.Text.compose(parts);
    }

    /**
     * Intestazione o pie' della lista giocatori (il riquadro del tasto Tab).
     *
     * <p>E' un pacchetto vanilla: lo vede qualunque client, senza mod dalla parte del giocatore.
     */
    public static void setTabList(ServerPlayer player, boolean header, @Nullable Component text)
    {
        Component value = text == null ? Component.empty() : text;
        if (header)
        {
            TAB_HEADER.put(player.getScoreboardName(), value);
        }
        else
        {
            TAB_FOOTER.put(player.getScoreboardName(), value);
        }
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(
                TAB_HEADER.getOrDefault(player.getScoreboardName(), Component.empty()),
                TAB_FOOTER.getOrDefault(player.getScoreboardName(), Component.empty())));
    }

    private static final Map<String, Component> TAB_HEADER = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Component> TAB_FOOTER = new java.util.concurrent.ConcurrentHashMap<>();

    public static Value versionValue()
    {
        return StringValue.of(getCarpetVersion());
    }
}
