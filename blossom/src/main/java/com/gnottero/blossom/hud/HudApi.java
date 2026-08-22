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
 * {@code hud}: a line of text that stays above the hotbar (see {@link HudBoard}).
 *
 * <p>Lives in {@code core.mc.text} next to {@code display_title}, and is its version that does
 * not fade away. Needs nothing on the client.
 */
public final class HudApi
{
    /** Symbols added to {@code core.mc.text}. */
    public static final java.util.Set<String> MODULE = java.util.Set.of("hud");

    private HudApi()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * hud(players, text)          - sets the line until it is changed
         * hud(players, text, ticks)   - sets it for that many ticks, then it disappears on its own
         * hud(players, null)          - removes it
         * hud(player)                 - the line that player is currently seeing, or null
         *
         * One single name because these are the same thing seen from four sides, and because
         * 'hud_set'/'hud_get'/'hud_clear' would be three names to remember for one line of text.
         *
         * The duration is optional and has no hidden default value: without it, the line stays.
         * These are two different uses and both are normal - a counter sticks around, a warning
         * should go away on its own - and giving it an implicit duration would have broken the first.
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
     * The duration in ticks, or {@code null} if none was requested.
     *
     * <p>Zero or negative is an error, not "disappears immediately": someone writing
     * {@code hud(p, text, 0)} has almost always miscalculated an interval, and silently making
     * it vanish would hide the wrong count. To remove the line there is {@code hud(p, null)},
     * which says so explicitly.
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
