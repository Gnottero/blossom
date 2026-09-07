package com.gnottero.blossom.event;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.value.BlockValue;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.ListValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * The recurring shapes with which events are fired.
 *
 * <p>The mixins are the mod's hottest spot: they live inside the tick loop and must stay short and
 * free of logic. Here is where arguments are built, once per shape, and every method starts with a
 * check that someone is actually listening, so an event nobody listens to only costs a field read.
 */
public final class Fire
{
    private Fire()
    {
    }

    private static String handName(@Nullable InteractionHand hand)
    {
        return hand == InteractionHand.OFF_HAND ? "offhand" : "mainhand";
    }

    /** {@code (player)} */
    public static boolean player(Event event, ServerPlayer player)
    {
        return event.isListened() && event.fireForPlayer(player, () -> List.of(new EntityValue(player)));
    }

    /** {@code (player, item, hand)} */
    public static boolean item(Event event, ServerPlayer player, @Nullable InteractionHand hand, ItemStack stack)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                ValueConversions.of(stack, player.level().registryAccess()),
                StringValue.of(handName(hand))));
    }

    /** {@code (player, item)} - for events in which the hand does not matter */
    public static boolean pickup(Event event, ServerPlayer player, ItemStack stack)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                ValueConversions.of(stack, player.level().registryAccess())));
    }

    /**
     * {@code (player, carried, other, slot, action)}: an item clicked onto another item.
     *
     * <p>The two stacks are passed as they are before the click, {@code slot} is the slot's number
     * in the open container and {@code action} the button, {@code 'left'} or {@code 'right'}.
     */
    public static boolean itemOnItem(ServerPlayer player, ItemStack carried, ItemStack other, int slot,
            boolean secondary)
    {
        Event event = Event.PLAYER_CLICKS_ITEM_ON_ITEM;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                ValueConversions.of(carried, player.level().registryAccess()),
                ValueConversions.of(other, player.level().registryAccess()),
                new NumericValue(slot),
                StringValue.of(secondary ? "right" : "left")));
    }

    /** {@code (player, block, facing)} */
    public static boolean blockClick(Event event, ServerPlayer player, BlockPos pos, Direction facing)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new BlockValue(null, (ServerLevel) player.level(), pos),
                StringValue.of(facing.getName())));
    }

    /** {@code (player, item, hand, block, face, hitvec)} */
    public static boolean blockHitWithItem(Event event, ServerPlayer player, InteractionHand hand, BlockHitResult hit)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () ->
        {
            BlockPos pos = hit.getBlockPos();
            return List.of(
                    new EntityValue(player),
                    ValueConversions.of(player.getItemInHand(hand), player.level().registryAccess()),
                    StringValue.of(handName(hand)),
                    new BlockValue(null, (ServerLevel) player.level(), pos),
                    StringValue.of(hit.getDirection().getName()),
                    relative(hit, pos));
        });
    }

    /** {@code (player, hand, block, face, hitvec)} */
    public static boolean blockHit(Event event, ServerPlayer player, InteractionHand hand, BlockHitResult hit)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () ->
        {
            BlockPos pos = hit.getBlockPos();
            return List.of(
                    new EntityValue(player),
                    StringValue.of(handName(hand)),
                    new BlockValue(null, (ServerLevel) player.level(), pos),
                    StringValue.of(hit.getDirection().getName()),
                    relative(hit, pos));
        });
    }

    private static Value relative(BlockHitResult hit, BlockPos pos)
    {
        Vec3 inside = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return ListValue.of(new NumericValue(inside.x), new NumericValue(inside.y), new NumericValue(inside.z));
    }

    /** {@code (player, item, hand, block)} */
    public static boolean blockPlaced(Event event, ServerPlayer player, BlockPos pos, InteractionHand hand,
            ItemStack stack)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                ValueConversions.of(stack, player.level().registryAccess()),
                StringValue.of(handName(hand)),
                new BlockValue(null, (ServerLevel) player.level(), pos)));
    }

    /** {@code (player, block)}: the block is still the one from before it broke. */
    public static boolean blockBroken(Event event, ServerPlayer player, BlockPos pos, BlockState previous)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new BlockValue(previous, (ServerLevel) player.level(), pos)));
    }

    /** {@code (player, entity, hand)}, or {@code (player, entity)} when the hand is irrelevant. */
    public static boolean entity(Event event, ServerPlayer player, Entity entity, @Nullable InteractionHand hand)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> hand == null
                ? List.of(new EntityValue(player), new EntityValue(entity))
                : List.of(new EntityValue(player), new EntityValue(entity), StringValue.of(handName(hand))));
    }

    /** {@code (player, amount, source, source_entity)} - danno subito */
    public static boolean takesDamage(Event event, Entity target, float amount, DamageSource source)
    {
        if (!event.isListened() || !(target instanceof final ServerPlayer player))
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new NumericValue(amount),
                StringValue.of(source.getMsgId()),
                source.getEntity() == null ? Value.NULL : new EntityValue(source.getEntity())));
    }

    /** {@code (player, amount, entity)}: damage dealt by a player. */
    public static boolean dealsDamage(Event event, Entity target, float amount, DamageSource source)
    {
        if (!event.isListened() || !(source.getEntity() instanceof final ServerPlayer player))
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new NumericValue(amount),
                new EntityValue(target)));
    }

    /** {@code (player, from_pos, to_pos, from_dimension, to_dimension)} */
    public static boolean dimensionChange(ServerPlayer player, Vec3 from, @Nullable Vec3 to,
            ResourceKey<Level> fromDim, ResourceKey<Level> toDim)
    {
        Event event = Event.PLAYER_CHANGES_DIMENSION;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                from == null ? Value.NULL : ValueConversions.of(from),
                to == null ? Value.NULL : ValueConversions.of(to),
                ValueConversions.of(fromDim),
                ValueConversions.of(toDim)));
    }

    /** {@code (player, from, to)} */
    public static boolean slotSwitch(ServerPlayer player, int from, int to)
    {
        Event event = Event.PLAYER_SWITCHES_SLOT;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player,
                () -> List.of(new EntityValue(player), new NumericValue(from), new NumericValue(to)));
    }

    /** {@code (player, hand)} */
    public static boolean handAction(Event event, ServerPlayer player, InteractionHand hand)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(new EntityValue(player), StringValue.of(handName(hand))));
    }

    /** {@code (player, strafe, forward, jumping, sneaking)} */
    public static boolean mountControls(ServerPlayer player, float strafe, float forward, boolean jumping,
            boolean sneaking)
    {
        Event event = Event.PLAYER_RIDES;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new NumericValue(strafe),
                new NumericValue(forward),
                blossom.value.BooleanValue.of(jumping),
                blossom.value.BooleanValue.of(sneaking)));
    }

    /** {@code (player, merchant, buy, buy_b, sell)} */
    public static boolean trade(ServerPlayer player, Merchant merchant, @Nullable MerchantOffer offer)
    {
        Event event = Event.PLAYER_TRADES;
        if (!event.isListened() || offer == null)
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                merchant instanceof final AbstractVillager villager ? new EntityValue(villager) : Value.NULL,
                ValueConversions.of(offer.getBaseCostA(), player.level().registryAccess()),
                ValueConversions.of(offer.getCostB(), player.level().registryAccess()),
                ValueConversions.of(offer.getResult(), player.level().registryAccess())));
    }

    /** {@code (player, recipe, full_stack)} */
    public static boolean recipe(ServerPlayer player, Identifier recipe, boolean fullStack)
    {
        Event event = Event.PLAYER_CHOOSES_RECIPE;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                StringValue.of(recipe.toString()),
                blossom.value.BooleanValue.of(fullStack)));
    }

    /** {@code (player, category, event, value)} */
    public static boolean statistic(ServerPlayer player, Stat<?> stat, int amount)
    {
        Event event = Event.STATISTIC;
        if (!event.isListened())
        {
            return false;
        }
        Identifier type = net.minecraft.core.registries.BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
        if (type == null)
        {
            return false;
        }
        return event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                StringValue.of(type.getPath()),
                StringValue.of(stat.getValue().toString()),
                new NumericValue(amount)));
    }

    /** {@code (x, z)} */
    public static void chunk(Event event, ServerLevel level, ChunkPos pos)
    {
        if (!event.isListened())
        {
            return;
        }
        event.fireInLevel(level, () -> List.of(new NumericValue(pos.x()), new NumericValue(pos.z())));
    }

    /** {@code (entity)}: an entity appeared in the world. */
    public static boolean entityLoads(ServerLevel level, Entity entity)
    {
        return Event.ENTITY_LOADS.fireInLevel(level, () -> List.of(new EntityValue(entity)));
    }

    /**
     * A chunk became a real chunk: {@code chunk_generated} if it was just born,
     * {@code chunk_loaded} in every case.
     *
     * <p>They go together because they come from the same moment and the same information:
     * separating them would mean hooking the same spot twice.
     */
    public static void chunkBecameFull(ServerLevel level, ChunkPos pos, boolean generated)
    {
        if (generated && Event.CHUNK_GENERATED.isListened())
        {
            Event.CHUNK_GENERATED.fireInLevel(level,
                    () -> List.of(new NumericValue(pos.x()), new NumericValue(pos.z())));
        }
        if (Event.CHUNK_LOADED.isListened())
        {
            Event.CHUNK_LOADED.fireInLevel(level, () -> List.of(
                    new NumericValue(pos.x()), new NumericValue(pos.z()),
                    blossom.value.BooleanValue.of(generated)));
        }
    }

    /**
     * {@code (piston, facing, blocks)}: a piston is about to move blocks.
     *
     * <p>The list is built only if someone is listening: it is a block list as long as the pushed
     * structure, and in a piston machine this happens many times per second.
     */
    public static boolean piston(Event event, ServerLevel level, BlockPos pistonPos, Direction facing,
            List<BlockPos> toMove)
    {
        if (!event.isListened())
        {
            return false;
        }
        return event.fireInLevel(level, () -> List.of(
                new BlockValue(null, level, pistonPos),
                StringValue.of(facing.getName()),
                ListValue.wrap(toMove.stream().map(pos -> new BlockValue(null, level, pos)))));
    }

    /**
     * {@code (previous_block, new_block)}: a block is about to turn into another one.
     *
     * <p>The two positions are the same: what changes is the state, and passing them as two complete
     * blocks spares the handler from remembering that the second argument means "there".
     */
    public static boolean blockForms(ServerLevel level, BlockPos pos, BlockState previous, BlockState formed)
    {
        if (!Event.BLOCK_FORMS.isListened())
        {
            return false;
        }
        return Event.BLOCK_FORMS.fireInLevel(level, () -> List.of(
                new BlockValue(previous, level, pos),
                new BlockValue(formed, level, pos)));
    }

    /** {@code (pos, power, source, attacker, block_interaction, create_fire)}: before the blast. */
    public static boolean explosion(ServerLevel level, @Nullable Entity source, @Nullable Entity attacker, Vec3 center,
            float power, boolean createFire, String blockInteraction)
    {
        Event event = Event.EXPLOSION;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireInLevel(level, () -> List.of(
                ValueConversions.of(center),
                new NumericValue(power),
                source == null ? Value.NULL : new EntityValue(source),
                attacker == null ? Value.NULL : new EntityValue(attacker),
                StringValue.of(blockInteraction),
                blossom.value.BooleanValue.of(createFire)));
    }

    /** As above, plus {@code (blocks, entities)}: what the blast hit, and what it is about to break. */
    public static boolean explosionOutcome(ServerLevel level, @Nullable Entity source, @Nullable Entity attacker,
            Vec3 center, float power, boolean createFire, String blockInteraction, List<BlockPos> blocks,
            List<Entity> entities)
    {
        Event event = Event.EXPLOSION_OUTCOME;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireInLevel(level, () -> List.of(
                ValueConversions.of(center),
                new NumericValue(power),
                source == null ? Value.NULL : new EntityValue(source),
                attacker == null ? Value.NULL : new EntityValue(attacker),
                StringValue.of(blockInteraction),
                blossom.value.BooleanValue.of(createFire),
                ListValue.wrap(blocks.stream()
                        .filter(pos -> !level.isEmptyBlock(pos))
                        .map(pos -> (Value) new BlockValue(level.getBlockState(pos), level, pos))
                        .toList()),
                ListValue.wrap(entities.stream().map(entity -> (Value) new EntityValue(entity)).toList())));
    }

    /** {@code (pos, skeleton_horse)}: a bolt is about to strike, with or without its horse trap. */
    public static boolean lightning(ServerLevel level, BlockPos pos, boolean skeletonHorse)
    {
        Event event = Event.LIGHTNING;
        if (!event.isListened())
        {
            return false;
        }
        return event.fireInLevel(level, () -> List.of(
                ValueConversions.of(pos),
                blossom.value.BooleanValue.of(skeletonHorse)));
    }
}
