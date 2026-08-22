package com.gnottero.blossom.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import blossom.exception.ArityError;
import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.interpreter.Expression;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.EventBus;
import blossom.value.FunctionValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * {@code core.mc.event}: reacting to what happens in the world.
 *
 * <p>A handler is registered by passing the function as a value, not its name: that way it stays
 * bound to the module that defined it and keeps resolving correctly even when called by the mod,
 * which knows nothing about that module.
 *
 * <p>The function's argument count is checked now, not on the first event: a handler with the wrong
 * signature is a load-time error, not a line in the log three hours later.
 */
public final class EventApi
{
    public static final String MODULE = "core.mc.event";
    public static final Set<String> SYMBOLS = Set.of("on", "off", "available", "listeners", "describe");

    private EventApi()
    {
    }

    public static void apply(Expression expression, String appName)
    {
        // on(name, function) or on(name, function, [fixed arguments]) or
        // on(name, function, [fixed arguments], player)
        expression.addContextFunction("on", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "on(...) wants at least the event and the function: on(event, function [, fixed_arguments [, player]]).");
            }
            Event event = EventBus.eventOrThrow(lv.get(0).getString());
            if (!(lv.get(1) instanceof final FunctionValue function))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "The second argument of on(...) must be a function: pass it by bare name, e.g. on('tick', my_handler).");
            }
            List<Value> fixed = new ArrayList<>();
            if (lv.size() > 2 && lv.get(2) != Value.NULL)
            {
                if (!(lv.get(2) instanceof final ListValue extra))
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,
                            "The fixed arguments of on(...) must be passed in a list.");
                }
                fixed.addAll(extra.getItems());
            }
            String target = lv.size() > 3 && lv.get(3) != Value.NULL ? lv.get(3).getString() : null;

            int expected = event.arity() + fixed.size();
            if (function.getArguments().size() != expected)
            {
                throw new ArityError(SourcePosition.UNKNOWN,
                        "The handler of '" + event.name() + "' wants " + expected + " arguments, not "
                                + function.getArguments().size() + ".");
            }
            if (!(c instanceof final ScriptContext context))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "Events are registered from a loaded app, not from a one-off expression.");
            }
            event.add(new Event.Handler(appName, com.gnottero.blossom.BlossomMod.runtime().interpreterOf(appName),
                    context.source(), function, List.copyOf(fixed), target));
            return StringValue.of(event.name());
        });

        // off() removes all of the app's handlers, off(event) only those of one event
        expression.addContextFunction("off", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                EventBus.unregisterApp(appName);
                return Value.TRUE;
            }
            EventBus.eventOrThrow(lv.get(0).getString()).removeApp(appName);
            return Value.TRUE;
        });

        expression.addContextFunction("available", 0, (c, t, lv) ->
                ListValue.wrap(Event.names().stream().map(name -> (Value) StringValue.of(name)).toList()));

        expression.addContextFunction("listeners", 1, (c, t, lv) ->
        {
            List<Event.Handler> handlers = EventBus.handlersOf(lv.get(0).getString());
            return new NumericValue(handlers == null ? 0 : handlers.size());
        });

        // describe(event): how many arguments it passes and whether it can be cancelled, without opening the docs
        expression.addContextFunction("describe", 1, (c, t, lv) ->
        {
            Event event = EventBus.eventOrThrow(lv.get(0).getString());
            return MapValue.wrap(new java.util.LinkedHashMap<>(java.util.Map.of(
                    StringValue.of("name"), StringValue.of(event.name()),
                    StringValue.of("arity"), new NumericValue(event.arity()),
                    StringValue.of("cancellable"), blossom.value.BooleanValue.of(event.cancellable()),
                    StringValue.of("listeners"), new NumericValue(event.handlers().size()))));
        });
    }
}
