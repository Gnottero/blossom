package com.gnottero.blossom.net;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import com.gnottero.blossom.BlossomMod;

/**
 * Which of the connected players also has Blossom on the client.
 *
 * <p>This is the only state the network layer keeps, and it serves a single decision: who can be
 * sent a real drawing and who instead gets particles. The rest of the mod has no idea this
 * distinction exists.
 *
 * <p>The set is concurrent because it is written from the network thread (when a client's reply
 * arrives) and read from the server thread (when drawing).
 */
public final class ServerNetwork
{
    private static final Set<ServerPlayer> blossomClients = ConcurrentHashMap.newKeySet();

    private ServerNetwork()
    {
    }

    /** Greets a player who just joined. A vanilla client discards the packet and does not reply. */
    public static void onPlayerJoin(ServerPlayer player)
    {
        send(player, BlossomPayloads.Hello.of(BlossomPayloads.PROTOCOL_VERSION));
    }

    public static void onPlayerLeave(ServerPlayer player)
    {
        blossomClients.remove(player);
    }

    /** The client replied to the greeting: from now on it can receive drawings. */
    public static void onHello(ServerPlayer player, CompoundTag data)
    {
        int version = data.getIntOr(BlossomPayloads.VERSION_KEY, 0);
        if (version != BlossomPayloads.PROTOCOL_VERSION)
        {
            // better particles than a drawing interpreted with the wrong rules
            BlossomMod.LOG.info("{} has a Blossom client speaking protocol {}, this server speaks {}:"
                    + " they will get particles", player.getScoreboardName(), version, BlossomPayloads.PROTOCOL_VERSION);
            blossomClients.remove(player);
            return;
        }
        blossomClients.add(player);
    }

    /** @return whether this player has a compatible Blossom client */
    public static boolean hasClient(ServerPlayer player)
    {
        return blossomClients.contains(player);
    }

    /** The players with the client, among the given ones. */
    public static List<ServerPlayer> withClient(Collection<ServerPlayer> players)
    {
        return players.stream().filter(ServerNetwork::hasClient).toList();
    }

    /** The players without the client, among the given ones: they are the ones who will see particles. */
    public static List<ServerPlayer> withoutClient(Collection<ServerPlayer> players)
    {
        return players.stream().filter(player -> !hasClient(player)).toList();
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload)
    {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void clear()
    {
        blossomClients.clear();
    }
}
