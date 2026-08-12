package com.gnottero.blossom.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import blossom.interpreter.Context;
import blossom.interpreter.Interpreter;
import com.gnottero.blossom.ScriptContext;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.FunctionValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * Gli handler che uno script ha attaccato a una singola entita' con {@code entity.event(...)}.
 *
 * <p>Vivono dentro l'entita' (vedi il mixin {@code Entity_blossomAccessMixin}), non in una mappa
 * globale: seguono l'entita' fra i mondi e spariscono con lei, senza nessuna pulizia da ricordare.
 */
public final class EntityEvents
{
    /** Cosa puo' succedere a un'entita'. Il primo argomento passato e' sempre l'entita' stessa. */
    public enum Kind
    {
        /** {@code (entity)} */
        ON_TICK("on_tick", 0),
        /** {@code (entity)} */
        ON_REMOVED("on_removed", 0),
        /** {@code (entity, cause)} */
        ON_DEATH("on_death", 1),
        /** {@code (entity, amount, source, source_entity)} */
        ON_DAMAGE("on_damage", 3),
        /** {@code (entity, motion, from, to)} */
        ON_MOVE("on_move", 3);

        private final String id;
        private final int extraArity;

        Kind(String id, int extraArity)
        {
            this.id = id;
            this.extraArity = extraArity;
        }

        public String id()
        {
            return id;
        }

        /** Quanti argomenti riceve la funzione: l'entita' piu' quelli specifici dell'evento. */
        public int arity()
        {
            return extraArity + 1;
        }

        public static @Nullable Kind byName(String name)
        {
            for (Kind kind : values())
            {
                if (kind.id.equals(name))
                {
                    return kind;
                }
            }
            return null;
        }
    }

    /** un handler per una singola entita' */
    public record Handler(String app, Interpreter interpreter, FunctionValue function, List<Value> extraArguments)
    {
    }

    private final Entity entity;
    private final Map<Kind, Map<String, Handler>> handlers = new EnumMap<>(Kind.class);

    public EntityEvents(Entity entity)
    {
        this.entity = entity;
    }

    /** Aggiunge o toglie ({@code function == null}) l'handler dell'app data. */
    public void set(Kind kind, String app, Interpreter interpreter, @Nullable FunctionValue function,
            @Nullable List<Value> extraArguments)
    {
        if (function == null)
        {
            Map<String, Handler> forKind = handlers.get(kind);
            if (forKind != null)
            {
                forKind.remove(app);
                if (forKind.isEmpty())
                {
                    handlers.remove(kind);
                }
            }
            return;
        }
        handlers.computeIfAbsent(kind, key -> new java.util.LinkedHashMap<>())
                .put(app, new Handler(app, interpreter, function,
                        extraArguments == null ? List.of() : extraArguments));
    }

    public void removeApp(String app)
    {
        handlers.values().forEach(forKind -> forKind.remove(app));
        handlers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Genera l'evento. Gli argomenti specifici arrivano grezzi da Minecraft e vengono convertiti qui,
     * ma solo se qualcuno ascolta: un'entita' senza handler paga una {@code isEmpty()} per tick.
     */
    public void fire(Kind kind, Object... arguments)
    {
        if (handlers.isEmpty())
        {
            return;
        }
        Map<String, Handler> forKind = handlers.get(kind);
        if (forKind == null || forKind.isEmpty())
        {
            return;
        }
        List<Value> base = makeArguments(kind, arguments);
        CommandSourceStack source = entity instanceof final ServerPlayer player
                ? player.createCommandSourceStack()
                : entity.level().getServer().createCommandSourceStack();
        for (Iterator<Handler> iterator = forKind.values().iterator(); iterator.hasNext(); )
        {
            Handler handler = iterator.next();
            List<Value> call = base;
            if (!handler.extraArguments().isEmpty())
            {
                call = new ArrayList<>(base);
                call.addAll(handler.extraArguments());
            }
            try
            {
                Context context = new ScriptContext(handler.interpreter().host(), source);
                handler.function().callInContext(context, Context.Type.NONE, call).evalValue(context);
            }
            catch (RuntimeException | StackOverflowError error)
            {
                com.gnottero.blossom.util.AppErrors.report(handler.app(), "event " + kind.id(), error);
                iterator.remove();
            }
        }
    }

    private List<Value> makeArguments(Kind kind, Object... arguments)
    {
        EntityValue self = new EntityValue(entity);
        return switch (kind)
        {
            case ON_TICK, ON_REMOVED -> List.of(self);
            case ON_DEATH -> List.of(self, StringValue.of((String) arguments[0]));
            case ON_DAMAGE ->
            {
                float amount = (Float) arguments[0];
                DamageSource source = (DamageSource) arguments[1];
                yield List.of(self,
                        new NumericValue(amount),
                        StringValue.of(source.getMsgId()),
                        source.getEntity() == null ? Value.NULL : new EntityValue(source.getEntity()));
            }
            case ON_MOVE -> List.of(self,
                    ValueConversions.of((Vec3) arguments[0]),
                    ValueConversions.of((Vec3) arguments[1]),
                    ValueConversions.of((Vec3) arguments[2]));
        };
    }
}
