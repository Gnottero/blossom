package com.gnottero.blossom.hud;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A line of text that stays above the hotbar until a script changes it.
 *
 * <p>The game has no packet that says "keep this text there": both titles and the action bar
 * fade out. Resending the action bar before it fades gives the same result on any client,
 * vanilla included: the freedom to position the text is lost, but it works for everyone. The
 * stated limit is that there is only one line.
 *
 * <p>The resend happens every {@link #REFRESH_TICKS} ticks, not every tick: the action bar uses
 * the timings of titles, which hold text for 70 ticks. The threshold is kept low on purpose
 * because a script can shorten those timings with {@code display_title}, and the HUD should not
 * flicker because of that. A text change, on the other hand, takes effect immediately.
 */
public final class HudBoard
{
    /** How many ticks between resends of the line, so it does not fade. */
    private static final int REFRESH_TICKS = 10;

    /**
     * A line and when it stops existing.
     *
     * @param expiresAtTick the tick it disappears at, or {@link #FOREVER} if it stays until
     *                      changed. These are two different uses and both are normal: a counter
     *                      that accompanies the player stays, a warning ("you entered the zone")
     *                      should go away on its own, and without a duration one would have to
     *                      manually schedule the call that removes it, which is a lot of work to
     *                      say something trivial.
     */
    private record Line(Component text, long expiresAtTick)
    {
    }

    /** A line that never expires: the default, since a HUD usually accompanies the player. */
    private static final long FOREVER = Long.MAX_VALUE;

    /** Player to the line they should see. Concurrent: written from a script, read from the tick. */
    private final Map<UUID, Line> lines = new ConcurrentHashMap<>();
    private long tick = 0;

    /**
     * Changes a player's line, and sends it to them immediately.
     *
     * @param line          {@code null} to remove it
     * @param durationTicks how many ticks it stays for, or {@code null} for "until changed"
     */
    public void set(ServerPlayer player, @Nullable Component line, @Nullable Long durationTicks)
    {
        if (line == null)
        {
            clear(player);
            return;
        }
        long expires = durationTicks == null ? FOREVER : tick + Math.max(1L, durationTicks);
        lines.put(player.getUUID(), new Line(line, expires));
        send(player, line);
    }

    /** The line this player is currently seeing, or {@code null}. */
    public @Nullable Component get(ServerPlayer player)
    {
        Line line = lines.get(player.getUUID());
        return line == null ? null : line.text();
    }

    private void clear(ServerPlayer player)
    {
        if (lines.remove(player.getUUID()) != null)
        {
            // an empty action bar is the only way to remove the current one: simply stopping
            // the resend would leave it visible until it fades out
            send(player, Component.empty());
        }
    }

    /** To be called on every server tick. */
    public void onTick(MinecraftServer server)
    {
        tick++;
        if (lines.isEmpty())
        {
            return; // the normal case: no allocation, no packet
        }
        // expiry is checked every tick, not every ten: a duration requested in ticks must be
        // honored in ticks, otherwise "stays 5 ticks" becomes "stays somewhere between 1 and 10"
        boolean refresh = tick % REFRESH_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            Line line = lines.get(player.getUUID());
            if (line == null)
            {
                continue;
            }
            if (tick >= line.expiresAtTick())
            {
                clear(player);
            }
            else if (refresh)
            {
                send(player, line.text());
            }
        }
    }

    public void onPlayerLeave(ServerPlayer player)
    {
        lines.remove(player.getUUID());
    }

    public void clear()
    {
        lines.clear();
    }

    private static void send(ServerPlayer player, Component line)
    {
        player.connection.send(new ClientboundSetActionBarTextPacket(line));
    }
}
