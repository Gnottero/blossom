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
 * Una riga di testo che resta sopra la hotbar finché lo script non la cambia.
 *
 * <h2>Perché la action bar e non un pacchetto proprio</h2>
 *
 * <p>Il gioco non ha nessun pacchetto che dica "tieni questo testo lì": il titolo e la action bar
 * hanno una durata e poi svaniscono. Una HUD vera richiederebbe quindi un pacchetto nostro e del
 * codice che disegni sul client - cioè funzionerebbe solo per chi ha il mod installato.
 *
 * <p>Rimandare la action bar prima che svanisca dà lo stesso risultato <em>su qualunque client</em>,
 * vanilla compreso. È il compromesso giusto: si perde la libertà di posizionare il testo dove si
 * vuole, si guadagna che funziona per tutti senza installare niente. Il limite vero è che la riga è
 * una sola, ed è dichiarato.
 *
 * <h2>Ogni quanto</h2>
 *
 * <p>Non ad ogni tick. La action bar usa i tempi dei titoli, che per default tengono il testo 70
 * tick prima di sfumare: rimandarlo ogni {@link #REFRESH_TICKS} basta perché non svanisca mai, e
 * costa venti volte meno di un pacchetto per tick. La soglia è tenuta bassa apposta rispetto ai 70
 * perché uno script può accorciare i tempi dei titoli con {@code display_title}, e la HUD non deve
 * cominciare a lampeggiare per quello.
 *
 * <p>Un cambio di testo invece parte subito: chi scrive {@code hud(p, contatore)} si aspetta di
 * vedere il numero nuovo adesso, non entro mezzo secondo.
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
