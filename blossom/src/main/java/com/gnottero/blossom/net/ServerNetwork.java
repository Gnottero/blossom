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
 * Chi, fra i giocatori connessi, ha Blossom anche sul client.
 *
 * <p>È l'unico stato che il layer di rete tiene, e serve a una decisione sola: a chi si può mandare
 * un disegno vero e a chi invece vanno mandate le particelle. Tutto il resto del mod non sa che
 * questa distinzione esista.
 *
 * <p>L'insieme è concorrente perché ci si scrive dal thread di rete (quando arriva la risposta di un
 * client) e ci si legge dal thread del server (quando si disegna).
 */
public final class ServerNetwork
{
    private static final Set<ServerPlayer> blossomClients = ConcurrentHashMap.newKeySet();

    private ServerNetwork()
    {
    }

    /** Saluta un giocatore appena entrato. Un client vanilla scarta il pacchetto e non risponde. */
    public static void onPlayerJoin(ServerPlayer player)
    {
        send(player, BlossomPayloads.Hello.of(BlossomPayloads.PROTOCOL_VERSION));
    }

    public static void onPlayerLeave(ServerPlayer player)
    {
        blossomClients.remove(player);
    }

    /** Il client ha risposto al saluto: da adesso può ricevere i disegni. */
    public static void onHello(ServerPlayer player, CompoundTag data)
    {
        int version = data.getIntOr(BlossomPayloads.VERSION_KEY, 0);
        if (version != BlossomPayloads.PROTOCOL_VERSION)
        {
            // meglio le particelle che un disegno interpretato con le regole sbagliate
            BlossomMod.LOG.info("{} has a Blossom client speaking protocol {}, this server speaks {}:"
                    + " they will get particles", player.getScoreboardName(), version, BlossomPayloads.PROTOCOL_VERSION);
            blossomClients.remove(player);
            return;
        }
        blossomClients.add(player);
    }

    /** @return se questo giocatore ha un client Blossom compatibile */
    public static boolean hasClient(ServerPlayer player)
    {
        return blossomClients.contains(player);
    }

    /** I giocatori con il client, fra quelli dati. */
    public static List<ServerPlayer> withClient(Collection<ServerPlayer> players)
    {
        return players.stream().filter(ServerNetwork::hasClient).toList();
    }

    /** I giocatori senza il client, fra quelli dati: sono quelli che vedranno le particelle. */
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
