package com.gnottero.blossom.access;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import blossom.value.MapValue;

/**
 * Il poco che l'API di gioco deve chiedere al mod stesso invece che a Minecraft: la propria
 * versione, lo stato per thread del riempimento senza aggiornamenti, e i due pezzi di interfaccia
 * che si mandano al client con pacchetti vanilla.
 */
public final class Host
{
    private Host()
    {
    }

    /** La versione del mod, letta dal manifesto Fabric. */
    public static String modVersion()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("blossomscript")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Blossom non ha una regola globale per saltare gli aggiornamenti di blocco. */
    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates()
    {
        return SKIP_UPDATES;
    }

    private static final ThreadLocal<Boolean> SKIP_UPDATES = ThreadLocal.withInitial(() -> false);

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
}
