package com.gnottero.blossom.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import com.gnottero.blossom.BlossomMod;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.FormattedTextValue;
import blossom.value.ListValue;
import blossom.value.NumericValue;
import blossom.value.Value;

/**
 * {@code hud}: una riga di testo che resta sopra la hotbar (vedi {@link HudBoard}).
 *
 * <p>Sta in {@code core.mc.text} accanto a {@code display_title}, ed è la sua versione che non
 * svanisce. Non serve niente sul client.
 */
public final class HudApi
{
    /** i simboli aggiunti a {@code core.mc.text} */
    public static final java.util.Set<String> MODULE = java.util.Set.of("hud");

    private HudApi()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * hud(giocatori, testo)         - mette la riga finché non la si cambia
         * hud(giocatori, testo, durata) - la mette per quei tick, poi sparisce da sola
         * hud(giocatori, null)          - la toglie
         * hud(giocatore)                - la riga che quel giocatore sta vedendo, o null
         *
         * Un solo nome perché sono la stessa cosa vista da quattro lati, e perché
         * 'hud_set'/'hud_get'/'hud_clear' sarebbero tre nomi da ricordare per una riga di testo.
         *
         * La durata è facoltativa e non ha un valore di default nascosto: senza, la riga resta.
         * Sono due usi diversi e tutti e due normali - un contatore accompagna, un avviso deve
         * andarsene da solo - e mettere una durata implicita avrebbe rotto il primo.
         */
        expression.addContextFunction("hud", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "'hud' wants a player and the line to show: hud(player, 'text'),"
                                + " hud(player, 'text', ticks) for a line that goes away by itself,"
                                + " or hud(player, null) to clear it.");
            }
            ScriptContext cc = (ScriptContext) c;
            List<ServerPlayer> players = playersOf(cc, lv.get(0));
            if (lv.size() == 1)
            {
                if (players.size() != 1)
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,
                            "'hud' with a single argument reads one player's line: two players have two different lines.");
                }
                Component line = BlossomMod.hud().get(players.getFirst());
                return line == null ? Value.NULL : new FormattedTextValue(line);
            }
            Value text = lv.get(1);
            Component line = text.isNull() ? null : FormattedTextValue.getTextByValue(text);
            Long duration = durationOf(lv);
            players.forEach(player -> BlossomMod.hud().set(player, line, duration));
            return new NumericValue(players.size());
        });
    }

    /**
     * La durata in tick, o {@code null} se non è stata chiesta.
     *
     * <p>Zero o negativa è un errore e non "sparisce subito": chi scrive {@code hud(p, testo, 0)}
     * quasi sempre ha calcolato male un intervallo, e farla sparire in silenzio nasconderebbe il
     * conto sbagliato. Per togliere la riga c'è {@code hud(p, null)}, che lo dice.
     */
    private static @org.jspecify.annotations.Nullable Long durationOf(List<Value> lv)
    {
        if (lv.size() < 3 || lv.get(2).isNull())
        {
            return null;
        }
        long ticks = NumericValue.asNumber(lv.get(2)).getLong();
        if (ticks <= 0)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "'hud' wants a duration of at least one tick, not " + ticks
                            + ". To remove the line use hud(player, null).");
        }
        return ticks;
    }

    private static List<ServerPlayer> playersOf(ScriptContext cc, Value declared)
    {
        List<Value> names = declared instanceof final ListValue list ? list.getItems() : List.of(declared);
        List<ServerPlayer> players = new ArrayList<>();
        for (Value name : names)
        {
            ServerPlayer player = EntityValue.getPlayerByValue(cc.server(), name);
            if (player == null)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'hud' wants a connected player, not " + name.getString() + ".");
            }
            players.add(player);
        }
        return players;
    }
}
