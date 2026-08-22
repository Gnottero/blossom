package com.gnottero.blossom.api;

import com.gnottero.blossom.ScriptContext;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.argument.FunctionArgument;
import com.gnottero.blossom.argument.Vector3Argument;
import blossom.exception.InternalExpressionException;
import com.gnottero.blossom.value.EntityValue;
import blossom.value.ListValue;
import com.gnottero.blossom.value.NBTSerializableValue;
import blossom.value.NumericValue;
import blossom.value.Value;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Entities
{
    private static ListValue getPlayersFromWorldMatching(Context c, Predicate<ServerPlayer> condition)
    {
        List<Value> ret = new ArrayList<>();
        for (ServerPlayer player : ((ScriptContext) c).level().players())
        {
            if (condition.test(player))
            {
                ret.add(new EntityValue(player));
            }
        }
        return ListValue.wrap(ret);
    }

    public static void apply(Expression expression)
    {
        expression.addContextFunction("player", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                ScriptContext cc = (ScriptContext) c;
                Entity callingEntity = cc.source().getEntity();
                if (callingEntity instanceof Player)
                {
                    return EntityValue.of(callingEntity);
                }
                Vec3 pos = ((ScriptContext) c).source().getPosition();
                Player closestPlayer = ((ScriptContext) c).level().getNearestPlayer(pos.x, pos.y, pos.z, -1.0, EntitySelector.ENTITY_STILL_ALIVE);
                return EntityValue.of(closestPlayer);
            }
            String playerName = lv.get(0).getString();
            return switch (playerName)
                    {
                        case "all" -> {
                            List<Value> ret = new ArrayList<>();
                            for (ServerPlayer player : ((ScriptContext) c).server().getPlayerList().getPlayers())
                            {
                                ret.add(new EntityValue(player));
                            }
                            yield ListValue.wrap(ret);
                        }
                        case "*" -> getPlayersFromWorldMatching(c, p -> true);
                        case "survival" -> getPlayersFromWorldMatching(c, p -> p.gameMode.isSurvival()); // includes adventure
                        case "creative" -> getPlayersFromWorldMatching(c, ServerPlayer::isCreative);
                        case "spectating" -> getPlayersFromWorldMatching(c, ServerPlayer::isSpectator);
                        case "!spectating" -> getPlayersFromWorldMatching(c, p -> !p.isSpectator());
                        default -> {
                            ServerPlayer player = ((ScriptContext) c).server().getPlayerList().getPlayerByName(playerName);
                            yield player != null ? new EntityValue(player) : Value.NULL;
                        }
                    };
        });

        expression.addContextFunction("spawn", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'spawn' function takes mob name, and position to spawn");
            }
            String entityString = lv.get(0).getString();
            Identifier entityId;
            try
            {
                entityId = Identifier.read(new StringReader(entityString));
                EntityType<? extends Entity> type = cc.registry(Registries.ENTITY_TYPE).getOptional(entityId).orElse(null);
                if (type == null || !type.canSummon())
                {
                    return Value.NULL;
                }
            }
            catch (CommandSyntaxException ignored)
            {
                return Value.NULL;
            }

            Vector3Argument position = Vector3Argument.findIn(lv, 1);
            if (position.fromBlock)
            {
                position.vec = position.vec.subtract(0, 0.5, 0);
            }
            CompoundTag tag = new CompoundTag();
            boolean hasTag = false;
            if (lv.size() > position.offset)
            {
                Value nbt = lv.get(position.offset);
                NBTSerializableValue v = (nbt instanceof final NBTSerializableValue nbtsv)
                        ? nbtsv
                        : NBTSerializableValue.parseStringOrFail(nbt.getString());
                hasTag = true;
                tag = v.getCompoundTag();
            }
            tag.putString("id", entityId.toString());
            Vec3 vec3d = position.vec;

            ServerLevel serverWorld = cc.level();
            Entity entity = EntityType.loadEntityRecursive(tag, serverWorld, new EntitySpawnRequest(EntitySpawnReason.COMMAND, true), e -> {
                e.snapTo(vec3d.x, vec3d.y, vec3d.z, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity == null)
            {
                return Value.NULL;
            }
            if (!hasTag && entity instanceof Mob mob)
            {
                mob.finalizeSpawn(serverWorld, serverWorld.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.COMMAND, null);
            }
            if (!serverWorld.tryAddFreshEntityWithPassengers(entity))
            {
                entity.discard();
                return Value.NULL;
            }
            return new EntityValue(entity);
        });

        expression.addContextFunction("entity_id", 1, (c, t, lv) ->
        {
            Value who = lv.get(0);
            if (who instanceof final NumericValue numericValue)
            {
                return EntityValue.of(((ScriptContext) c).level().getEntity((int) numericValue.getLong()));
            }
            return EntityValue.of(((ScriptContext) c).level().getEntity(UUID.fromString(who.getString())));
        });

        expression.addContextFunction("entity_list", 1, (c, t, lv) ->
        {
            String who = lv.get(0).getString();
            CommandSourceStack source = ((ScriptContext) c).source();
            EntityValue.EntityClassDescriptor eDesc = EntityValue.getEntityDescriptor(who, source.getServer());
            @SuppressWarnings("unchecked") // Eclipse's compiler finds an error if the cast isn't there (TODO report to ECJ)
            List<? extends Entity> entityList = (List<? extends Entity>) source.getLevel().getEntities(eDesc.directType, eDesc.filteringPredicate);
            return ListValue.wrap(entityList.stream().map(EntityValue::new));
        });

        expression.addContextFunction("entity_area", -1, (c, t, lv) ->
        {
            if (lv.size() < 3)
            {
                throw new InternalExpressionException("'entity_area' requires entity type, center and range arguments");
            }
            String who = lv.get(0).getString();
            ScriptContext cc = (ScriptContext) c;
            Vector3Argument centerLocator = Vector3Argument.findIn(lv, 1, false, true);

            AABB centerBox;
            if (centerLocator.entity != null)
            {
                centerBox = centerLocator.entity.getBoundingBox();
            }
            else
            {
                Vec3 center = centerLocator.vec;
                if (centerLocator.fromBlock)
                {
                    center.add(0.5, 0.5, 0.5);
                }
                centerBox = new AABB(center, center);
            }
            Vector3Argument rangeLocator = Vector3Argument.findIn(lv, centerLocator.offset);
            if (rangeLocator.fromBlock)
            {
                throw new InternalExpressionException("Range of 'entity_area' cannot come from a block argument");
            }
            Vec3 range = rangeLocator.vec;
            AABB area = centerBox.inflate(range.x, range.y, range.z);
            EntityValue.EntityClassDescriptor eDesc = EntityValue.getEntityDescriptor(who, cc.server());
            List<? extends Entity> entityList = cc.level().getEntities(eDesc.directType, area, eDesc.filteringPredicate);
            return ListValue.wrap(entityList.stream().map(EntityValue::new));
        });

        expression.addContextFunction("entity_selector", -1, (c, t, lv) ->
        {
            String selector = lv.get(0).getString();
            List<Value> retlist = new ArrayList<>();
            for (Entity e : EntityValue.getEntitiesFromSelector(((ScriptContext) c).source(), selector))
            {
                retlist.add(new EntityValue(e));
            }
            return ListValue.wrap(retlist);
        });

        expression.addContextFunction("query", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'query' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0);
            if (!(v instanceof final EntityValue ev))
            {
                throw new InternalExpressionException("First argument to query should be an entity");
            }
            String what = lv.get(1).getString().toLowerCase(Locale.ROOT);
            return switch (lv.size())
                    {
                        case 2 -> ev.get(what, null);
                        case 3 -> ev.get(what, lv.get(2));
                        default -> ev.get(what, ListValue.wrap(lv.subList(2, lv.size())));
                    };
        });

        // or update
        expression.addContextFunction("modify", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new InternalExpressionException("'modify' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0);
            if (!(v instanceof final EntityValue ev))
            {
                throw new InternalExpressionException("First argument to modify should be an entity");
            }
            String what = lv.get(1).getString();
            switch (lv.size())
            {
                case 2 -> ev.set(what, null);
                case 3 -> ev.set(what, lv.get(2));
                default -> ev.set(what, ListValue.wrap(lv.subList(2, lv.size())));
            }
            return v;
        });

        expression.addContextFunction("entity_types", -1, (c, t, lv) ->
        {
            if (lv.size() > 1)
            {
                throw new InternalExpressionException("'entity_types' requires one or no arguments");
            }
            String desc = (lv.size() == 1) ? lv.get(0).getString() : "*";
            return EntityValue.getEntityDescriptor(desc, ((ScriptContext) c).server()).listValue(((ScriptContext) c).registryAccess());
        });

        /*
         * entity_load_handler(types, function) - called every time an entity of one of those
         * types appears.
         *
         * There's a single event ('entity_loads') instead of one generated per entity type: a
         * catalog of 100+ names is not something anyone would read. The type filter is done in
         * Java before calling the handler, so listening for a rare type doesn't cost a call for
         * every entity that appears.
         *
         * 'types' is a type or a list of types; with null all entities are listened for.
         */
        expression.addContextFunction("entity_load_handler", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
            {
                throw new blossom.exception.ArityError(blossom.exception.SourcePosition.UNKNOWN,
                        "'entity_load_handler' wants the entity types to listen for and a function.");
            }
            if (!(c instanceof final ScriptContext context))
            {
                throw new blossom.exception.TypeMismatchError(blossom.exception.SourcePosition.UNKNOWN,
                        "Handlers are registered from a loaded app, not from a one-off expression.");
            }
            FunctionArgument funArg = FunctionArgument.findIn(c, expression.module, lv, 1, true, false);
            java.util.Set<String> types = entityTypeFilter(lv.get(0));
            int expected = 1 + funArg.args.size();
            if (funArg.function.getArguments().size() != expected)
            {
                throw new blossom.exception.ArityError(blossom.exception.SourcePosition.UNKNOWN,
                        "The 'entity_load_handler' handler wants " + expected + " arguments, not "
                                + funArg.function.getArguments().size() + ".");
            }
            String app = c.host.getName();
            com.gnottero.blossom.event.Event.ENTITY_LOADS.add(new com.gnottero.blossom.event.Event.Handler(
                    app, com.gnottero.blossom.BlossomMod.runtime().interpreterOf(app), context.source(),
                    funArg.function, List.copyOf(funArg.args), null,
                    types == null ? null : arguments -> matchesType(arguments, types)));
            return Value.TRUE;
        });

        expression.addContextFunction("entity_event", -1, (c, t, lv) ->
        {
            if (lv.size() < 3)
            {
                throw new InternalExpressionException("'entity_event' requires at least 3 arguments, entity, event to be handled, and function name, with optional arguments");
            }
            Value v = lv.get(0);
            if (!(v instanceof final EntityValue ev))
            {
                throw new InternalExpressionException("First argument to entity_event should be an entity");
            }
            String what = lv.get(1).getString();

            FunctionArgument funArg = FunctionArgument.findIn(c, expression.module, lv, 2, true, false);

            ev.setEvent((ScriptContext) c, c.host.getName(), what, funArg.function, funArg.args);

            return Value.NULL;
        });

        /**
         * has_client(player) - whether that player has Blossom on the client too.
         *
         * <p>This lives here and not among the shape functions: it's a question about a player,
         * like everything else in this module, and the answer isn't needed for drawing
         * ({@code draw_shape} decides on its own who sees the real shape and who sees particles).
         * It's for anyone who wants to offer something different to players with the client,
         * without having to guess.
         */
        expression.addContextFunction("has_client", 1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerPlayer player = EntityValue.getPlayerByValue(cc.server(), lv.get(0));
            return blossom.value.BooleanValue.of(player != null && com.gnottero.blossom.net.ServerNetwork.hasClient(player));
        });
    }

    /**
     * The types an {@code entity_load_handler} wants to hear about, or {@code null} for all.
     *
     * <p>Names are normalized now, once, rather than for every entity that appears.
     */
    private static java.util.@org.jspecify.annotations.Nullable Set<String> entityTypeFilter(Value declared)
    {
        if (declared.isNull())
        {
            return null;
        }
        List<Value> names = declared instanceof final ListValue list ? list.getItems() : List.of(declared);
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (Value name : names)
        {
            types.add(name.getString().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", ""));
        }
        return types;
    }

    /** Whether the entity passed to the event is one of the listened-for types. */
    private static boolean matchesType(List<Value> arguments, java.util.Set<String> types)
    {
        if (arguments.isEmpty() || !(arguments.get(0) instanceof final EntityValue entity))
        {
            return false;
        }
        net.minecraft.resources.Identifier id = entity.getEntity().level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                .getKey(entity.getEntity().getType());
        return id != null && (types.contains(id.getPath()) || types.contains(id.toString()));
    }
}
