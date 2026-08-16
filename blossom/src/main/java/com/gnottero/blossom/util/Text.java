package com.gnottero.blossom.util;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Il testo formattato di {@code format(...)}.
 *
 * <p>Ogni pezzo e' una stringa {@code "stile testo"}: le lettere dello stile sono colori e
 * decorazioni, e i pezzi che cominciano con un simbolo attaccano un'azione al pezzo precedente
 * ({@code ^} suggerimento al passaggio del mouse, {@code !} esegue un comando, {@code ?} lo scrive
 * in chat, {@code @} apre un indirizzo, {@code &} copia negli appunti).
 *
 * <p>La sintassi e' compatta apposta: formattare una riga di chat e' una cosa che si fa spesso e
 * di sfuggita, e non deve costare tre chiamate annidate. I colori si scrivono con la lettera o
 * per esteso.
 */
public final class Text
{
    private static final Pattern HEX_COLOR = Pattern.compile("#([0-9a-fA-F]{6})");

    /** le lettere dei colori, piu' i nomi per esteso */
    private static final Map<Character, ChatFormatting> COLORS = Map.ofEntries(
            Map.entry('w', ChatFormatting.WHITE),
            Map.entry('y', ChatFormatting.YELLOW),
            Map.entry('m', ChatFormatting.LIGHT_PURPLE),
            Map.entry('r', ChatFormatting.RED),
            Map.entry('c', ChatFormatting.AQUA),
            Map.entry('l', ChatFormatting.GREEN),
            Map.entry('t', ChatFormatting.BLUE),
            Map.entry('f', ChatFormatting.DARK_GRAY),
            Map.entry('g', ChatFormatting.GRAY),
            Map.entry('d', ChatFormatting.GOLD),
            Map.entry('p', ChatFormatting.DARK_PURPLE),
            Map.entry('n', ChatFormatting.DARK_RED),
            Map.entry('q', ChatFormatting.DARK_AQUA),
            Map.entry('e', ChatFormatting.DARK_GREEN),
            Map.entry('v', ChatFormatting.DARK_BLUE),
            Map.entry('k', ChatFormatting.BLACK));

    private Text()
    {
    }

    public static Style parseStyle(String style)
    {
        Style result = Style.EMPTY.withColor(ChatFormatting.WHITE);
        for (char code : style.toCharArray())
        {
            ChatFormatting color = COLORS.get(code);
            if (color != null)
            {
                result = result.withColor(color);
                continue;
            }
            result = switch (code)
            {
                case 'i' -> result.withItalic(true);
                case 'b' -> result.withBold(true);
                case 's' -> result.applyFormat(ChatFormatting.STRIKETHROUGH);
                case 'u' -> result.applyFormat(ChatFormatting.UNDERLINE);
                case 'o' -> result.applyFormat(ChatFormatting.OBFUSCATED);
                default -> result;
            };
        }
        Matcher hex = HEX_COLOR.matcher(style);
        if (hex.find())
        {
            TextColor exact = TextColor.parseColor("#" + hex.group(1)).result().orElse(null);
            if (exact != null)
            {
                result = result.withColor(exact);
            }
        }
        return result;
    }

    /** Mette insieme i pezzi in un solo componente. */
    public static Component compose(Object... parts)
    {
        MutableComponent message = Component.literal("");
        MutableComponent previous = null;
        for (Object part : parts)
        {
            if (part instanceof final Component component)
            {
                message.append(component);
                previous = null;
                continue;
            }
            MutableComponent piece = fromDescriptor(part.toString(), previous);
            if (piece != previous)
            {
                message.append(piece);
                previous = piece;
            }
        }
        return message;
    }

    private static MutableComponent fromDescriptor(String message, MutableComponent previous)
    {
        if (message.isEmpty())
        {
            return Component.literal("");
        }
        if (Character.isWhitespace(message.charAt(0)))
        {
            message = "w" + message;
        }
        int space = message.indexOf(' ');
        String style = space >= 0 ? message.substring(0, space) : message;
        String body = space >= 0 ? message.substring(space + 1) : "";

        if (previous != null)
        {
            Style previousStyle = previous.getStyle();
            switch (message.charAt(0))
            {
                case '?':
                    previous.setStyle(previousStyle.withClickEvent(new ClickEvent.SuggestCommand(message.substring(1))));
                    return previous;
                case '!':
                    previous.setStyle(previousStyle.withClickEvent(new ClickEvent.RunCommand(message.substring(1))));
                    return previous;
                case '^':
                    previous.setStyle(previousStyle.withHoverEvent(
                            new HoverEvent.ShowText(compose(message.substring(1)))));
                    return previous;
                case '@':
                    previous.setStyle(previousStyle.withClickEvent(
                            new ClickEvent.OpenUrl(URI.create(message.substring(1)))));
                    return previous;
                case '&':
                    previous.setStyle(previousStyle.withClickEvent(
                            new ClickEvent.CopyToClipboard(message.substring(1))));
                    return previous;
                default:
                    break;
            }
        }
        MutableComponent piece = Component.literal(body);
        piece.setStyle(parseStyle(style));
        return piece;
    }
}
