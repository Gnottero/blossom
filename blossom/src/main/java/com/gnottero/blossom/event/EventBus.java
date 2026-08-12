package com.gnottero.blossom.event;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.UndefinedSymbolError;

/**
 * Il registro degli handler di evento.
 *
 * <p>La logica di generazione sta in {@link Event}, che tiene la propria lista di handler: qui c'e'
 * solo quello che serve a chi registra e a chi scarica un'app.
 */
public final class EventBus
{
    private EventBus()
    {
    }

    /** Gli eventi che il mod sa generare. Registrarsi a un nome che non e' qui e' un errore. */
    public static Collection<String> knownEvents()
    {
        return Event.names();
    }

    /**
     * Registra {@code handler} sull'evento {@code name}.
     *
     * @throws UndefinedSymbolError se il nome non e' nel catalogo, con il nome piu' vicino come
     *         suggerimento
     */
    public static void register(String name, Event.Handler handler)
    {
        eventOrThrow(name).add(handler);
    }

    public static Event eventOrThrow(String name)
    {
        Event event = Event.byName(name);
        if (event == null)
        {
            throw new UndefinedSymbolError(SourcePosition.UNKNOWN,
                    "Evento '" + name + "' sconosciuto.",
                    Suggestions.closestTo(name, List.copyOf(Event.names())));
        }
        return event;
    }

    public static @Nullable Event event(String name)
    {
        return Event.byName(name);
    }

    /** Toglie tutti gli handler di un'app: si chiama quando l'app viene ricaricata o scaricata. */
    public static void unregisterApp(String app)
    {
        Event.unregisterApp(app);
    }

    public static void clear()
    {
        Event.clearAll();
    }

    /** @return se qualcuno ascolta {@code name} */
    public static boolean isListened(String name)
    {
        Event event = Event.byName(name);
        return event != null && event.isListened();
    }

    public static @Nullable List<Event.Handler> handlersOf(String name)
    {
        Event event = Event.byName(name);
        return event == null ? null : event.handlers();
    }
}
