package com.gnottero.blossom.access;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;


/**
 * The small set of things the game API needs to ask the mod itself rather than Minecraft: its own
 * version, the per-thread state for fills without block updates, and the two bits of interface
 * that get sent to the client with vanilla packets.
 */
public final class Host
{
    private Host()
    {
    }

    /** The mod's version, read from the Fabric manifest. */
    public static String modVersion()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("blossomscript")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Blossom has no global rule for skipping block updates. */
    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates()
    {
        return SKIP_UPDATES;
    }

    private static final ThreadLocal<Boolean> SKIP_UPDATES = ThreadLocal.withInitial(() -> false);

    /** The formatted text from {@code format(...)}. */
    public static Component Messenger_compose(Object... parts)
    {
        return com.gnottero.blossom.util.Text.compose(parts);
    }

    /**
     * Header or footer of the player list (the Tab-key panel).
     *
     * <p>It is a vanilla packet: any client sees it, no mod needed on the player's side.
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
