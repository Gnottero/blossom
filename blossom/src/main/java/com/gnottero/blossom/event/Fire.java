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
 * Le forme ricorrenti con cui gli eventi vengono generati.
 *
 * <p>I mixin sono il punto piu' caldo del mod: stanno dentro il tick loop e devono restare corti e
 * senza logica. Qui c'e' la costruzione degli argomenti, una volta sola per forma, e ogni metodo
 * comincia con il controllo che qualcuno stia ascoltando - cosi' un evento che nessuno ascolta costa
 * una lettura di campo.
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

    /** {@code (player, item)} - per gli eventi in cui la mano non vuol dire niente */
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

    /** {@code (player, block)} - il blocco e' ancora quello di prima della rottura */
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

    /** {@code (player, entity, hand)} oppure {@code (player, entity)} se la mano non c'entra */
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

    /** {@code (player, amount, entity)} - danno inflitto da un giocatore */
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
    public static void dimensionChange(ServerPlayer player, Vec3 from, @Nullable Vec3 to, ResourceKey<Level> fromDim,
            ResourceKey<Level> toDim)
    {
        Event event = Event.PLAYER_CHANGES_DIMENSION;
        if (!event.isListened())
        {
            return;
        }
        event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                from == null ? Value.NULL : ValueConversions.of(from),
                to == null ? Value.NULL : ValueConversions.of(to),
                ValueConversions.of(fromDim),
                ValueConversions.of(toDim)));
    }

    /** {@code (player, from, to)} */
    public static void slotSwitch(ServerPlayer player, int from, int to)
    {
        Event event = Event.PLAYER_SWITCHES_SLOT;
        if (!event.isListened())
        {
            return;
        }
        event.fireForPlayer(player,
                () -> List.of(new EntityValue(player), new NumericValue(from), new NumericValue(to)));
    }

    /** {@code (player, hand)} */
    public static void handAction(Event event, ServerPlayer player, InteractionHand hand)
    {
        if (!event.isListened())
        {
            return;
        }
        event.fireForPlayer(player, () -> List.of(new EntityValue(player), StringValue.of(handName(hand))));
    }

    /** {@code (player, strafe, forward, jumping, sneaking)} */
    public static void mountControls(ServerPlayer player, float strafe, float forward, boolean jumping,
            boolean sneaking)
    {
        Event event = Event.PLAYER_RIDES;
        if (!event.isListened())
        {
            return;
        }
        event.fireForPlayer(player, () -> List.of(
                new EntityValue(player),
                new NumericValue(strafe),
                new NumericValue(forward),
                blossom.value.BooleanValue.of(jumping),
                blossom.value.BooleanValue.of(sneaking)));
    }

    /** {@code (player, merchant, buy, buy_b, sell)} */
    public static void trade(ServerPlayer player, Merchant merchant, MerchantOffer offer)
    {
        Event event = Event.PLAYER_TRADES;
        if (!event.isListened() || offer == null)
        {
            return;
        }
        event.fireForPlayer(player, () -> List.of(
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
    public static void statistic(ServerPlayer player, Stat<?> stat, int amount)
    {
        Event event = Event.STATISTIC;
        if (!event.isListened())
        {
            return;
        }
        Identifier type = net.minecraft.core.registries.BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
        if (type == null)
        {
            return;
        }
        event.fireForPlayer(player, () -> List.of(
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

    /** {@code (entity)} - un'entita' e' comparsa nel mondo. */
    public static void entityLoads(ServerLevel level, Entity entity)
    {
        Event.ENTITY_LOADS.fireInLevel(level, () -> List.of(new EntityValue(entity)));
    }

    /**
     * Un chunk e' diventato un chunk vero: {@code chunk_generated} se e' nato adesso,
     * {@code chunk_loaded} in ogni caso.
     *
     * <p>Vanno insieme perche' nascono dallo stesso momento e dalla stessa informazione: separarli
     * vorrebbe dire agganciare due volte lo stesso punto.
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
     * {@code (piston, facing, blocks)} - un pistone sta per muovere dei blocchi.
     *
     * <p>L'elenco viene costruito solo se qualcuno ascolta: e' una lista di blocchi lunga quanto la
     * struttura spinta, e in una macchina a pistoni questo succede molte volte al secondo.
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
     * {@code (previous_block, new_block)} - un blocco sta per formarsene un altro.
     *
     * <p>Le due posizioni sono la stessa: quello che cambia e' lo stato, e passarli come due blocchi
     * completi evita all'handler di doversi ricordare che il secondo argomento vale "li'".
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

    /** {@code (pos, power, source, attacker, block_interaction, create_fire)} - prima dello scoppio */
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

    /** Come sopra, piu' {@code (blocks, entities)}: cosa lo scoppio ha effettivamente colpito. */
    public static void explosionOutcome(ServerLevel level, @Nullable Entity source, @Nullable Entity attacker,
            Vec3 center, float power, boolean createFire, String blockInteraction, List<BlockPos> blocks,
            List<Entity> entities)
    {
        Event event = Event.EXPLOSION_OUTCOME;
        if (!event.isListened())
        {
            return;
        }
        event.fireInLevel(level, () -> List.of(
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

    /** {@code (pos, entity)} - il fulmine, e il cavallo scheletro che ha evocato se l'ha evocato */
    public static void lightning(ServerLevel level, BlockPos pos, boolean skeletonHorse)
    {
        Event event = Event.LIGHTNING;
        if (!event.isListened())
        {
            return;
        }
        event.fireInLevel(level, () -> List.of(
                ValueConversions.of(pos),
                blossom.value.BooleanValue.of(skeletonHorse)));
    }
}
