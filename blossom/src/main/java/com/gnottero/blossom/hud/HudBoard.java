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
 * Una riga di testo che resta sopra la hotbar finche' lo script non la cambia.
 *
 * <p>Il gioco non ha un pacchetto che dica "tieni questo testo li'": titolo e action bar svaniscono.
 * Rimandare la action bar prima che sfumi da' lo stesso risultato su qualunque client, vanilla
 * compreso - si perde la liberta' di posizionare il testo, si guadagna che funziona per tutti. Il
 * limite dichiarato e' che la riga e' una sola.
 *
 * <p>Il rinvio e' ogni {@link #REFRESH_TICKS} tick e non ad ogni tick: la action bar usa i tempi dei
 * titoli, che tengono il testo 70 tick. La soglia sta bassa apposta perche' uno script puo'
 * accorciare quei tempi con {@code display_title}, e la HUD non deve lampeggiare per questo. Un
 * cambio di testo invece parte subito.
 */
public final class HudBoard
{
    /** ogni quanti tick si rimanda la riga, per non farla svanire */
    private static final int REFRESH_TICKS = 10;

    /**
     * Una riga e quando smette di esistere.
     *
     * @param expiresAtTick il tick in cui sparisce, o {@link #FOREVER} se resta finché non la si
     *                      cambia. Sono due usi diversi e tutti e due normali: un contatore che
     *                      accompagna il giocatore resta, un avviso ("sei entrato nella zona") deve
     *                      andarsene da solo — e senza durata bisognerebbe programmare a mano la
     *                      chiamata che lo toglie, che è lavoro per dire una cosa banale
     */
    private record Line(Component text, long expiresAtTick)
    {
    }

    /** una riga che non scade: è il default, perché una HUD di solito accompagna */
    private static final long FOREVER = Long.MAX_VALUE;

    /** giocatore → riga che deve vedere. Concorrente: ci si scrive da uno script, si legge dal tick */
    private final Map<UUID, Line> lines = new ConcurrentHashMap<>();
    private long tick = 0;

    /**
     * Cambia la riga di un giocatore, e gliela manda subito.
     *
     * @param line          {@code null} per toglierla
     * @param durationTicks per quanti tick resta, o {@code null} per "finché non la si cambia"
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

    /** La riga che questo giocatore sta vedendo, o {@code null}. */
    public @Nullable Component get(ServerPlayer player)
    {
        Line line = lines.get(player.getUUID());
        return line == null ? null : line.text();
    }

    private void clear(ServerPlayer player)
    {
        if (lines.remove(player.getUUID()) != null)
        {
            // una action bar vuota e' l'unico modo di togliere quella che c'e': smettere di
            // mandarla la lascerebbe visibile fino alla dissolvenza
            send(player, Component.empty());
        }
    }

    /** Da chiamare a ogni tick del server. */
    public void onTick(MinecraftServer server)
    {
        tick++;
        if (lines.isEmpty())
        {
            return; // il caso normale: nessuna allocazione, nessun pacchetto
        }
        // la scadenza si controlla ad ogni tick e non ogni dieci: una durata chiesta in tick va
        // rispettata in tick, altrimenti "resta 5 tick" diventa "resta fra 1 e 10"
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
