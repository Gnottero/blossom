package com.gnottero.blossom.event;

import java.util.List;

import org.jspecify.annotations.Nullable;

import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;
import blossom.exception.UndefinedSymbolError;

/**
 * The registry of event handlers.
 *
 * <p>The firing logic lives in {@link Event}, which keeps its own list of handlers: here there is
 * only what is needed by whoever registers, and by whoever unloads an app.
 */
public final class EventBus
{
    private EventBus()
    {
    }

    /**
     * Registers {@code handler} on the {@code name} event.
     *
     * @throws UndefinedSymbolError if the name is not in the catalog, with the closest name as a
     *         suggestion
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

    /** Removes all handlers of an app: called when the app is reloaded or unloaded. */
    public static void unregisterApp(String app)
    {
        Event.unregisterApp(app);
    }

    public static void clear()
    {
        Event.clearAll();
    }

    /** @return whether someone is listening to {@code name} */
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
