package com.gnottero.blossom.access;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.tags.TagKey;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelStorageSource;

import com.gnottero.blossom.event.EntityEvents;
import blossom.value.Value;

/**
 * The windows the mod opens into Minecraft.
 *
 * <p>Every interface here is implemented by a mixin in {@code com.gnottero.blossom.mixins.access}
 * onto a game class: it is the way to read and write fields that vanilla keeps private, without
 * spreading casts and reflection across the whole API. Code writing the API does not touch these
 * interfaces directly: it goes through the {@link Vanilla} facade, which hides the casts.
 */
public final class Access
{
    private Access()
    {
    }

    /** {@link net.minecraft.server.MinecraftServer} */
    public interface Server
    {
        void blossom$forceTick(BooleanSupplier until);

        LevelStorageSource.LevelStorageAccess blossom$storage();
    }

    /** {@link net.minecraft.world.level.NaturalSpawner.SpawnState} */
    public interface SpawnState
    {
        PotentialCalculator blossom$potentialCalculator();
    }

    /** {@link net.minecraft.server.level.TicketStorage} */
    public interface Tickets
    {
        Long2ObjectOpenHashMap<List<Ticket>> blossom$ticketsByPosition();
    }

    /** {@link Biome} */
    public interface BiomeClimate
    {
        Biome.ClimateSettings blossom$climate();
    }

    /** {@link net.minecraft.world.entity.ai.Brain} */
    public interface BrainMemories
    {
        Map<MemoryModuleType<?>, MemorySlot<?>> blossom$memories();
    }

    /** {@link net.minecraft.server.level.ServerPlayerGameMode} */
    public interface GameMode
    {
        BlockPos blossom$breakingBlock();

        int blossom$breakingProgress();

        void blossom$setBreakingProgress(int progress);
    }

    /** {@link net.minecraft.world.entity.Mob} */
    public interface MobAi
    {
        GoalSelector blossom$goals(boolean target);

        Map<String, Goal> blossom$temporaryTasks();

        void blossom$setPersistence(boolean persistent);
    }

    /** {@link net.minecraft.world.entity.Entity} */
    public interface EntityState
    {
        EntityEvents blossom$events();

        boolean blossom$permanentVehicle();

        void blossom$setPermanentVehicle(boolean permanent);

        int blossom$portalTimer();

        void blossom$setPortalTimer(int amount);

        int blossom$portalCooldown();

        void blossom$setPortalCooldown(int amount);
    }

    /** {@link net.minecraft.world.level.portal.PortalProcessor} */
    public interface Portal
    {
        void blossom$setPortalTime(int time);
    }

    /** {@link net.minecraft.world.entity.item.ItemEntity} */
    public interface ItemEntityDelay
    {
        int blossom$pickupDelay();
    }

    /** {@link net.minecraft.world.entity.LivingEntity} */
    public interface Jumping
    {
        boolean blossom$isJumping();

        void blossom$jump();
    }

    /** {@link net.minecraft.world.entity.animal.equine.AbstractHorse} */
    public interface InventoryBearer
    {
        Container blossom$inventory();
    }

    /** {@link net.minecraft.world.inventory.AbstractContainerMenu} */
    public interface Menu
    {
        DataSlot blossom$dataSlot(int index);

        boolean blossom$callButtonClick(int button, Player player);

        boolean blossom$callSelectRecipe(ServerPlayer player, RecipeHolder<?> recipe, boolean craftAll);
    }

    /** {@link net.minecraft.world.food.FoodData} */
    public interface Food
    {
        float blossom$exhaustion();

        void blossom$setExhaustion(float exhaustion);
    }

    /** {@code com.mojang.brigadier.tree.CommandNode} */
    public interface CommandTree
    {
        void blossom$removeChild(String name);
    }

    /** {@link net.minecraft.commands.CommandSourceStack} */
    public interface Source
    {
        void blossom$setupPrivates(net.minecraft.commands.CommandSourceStack.NamesProvider names, boolean silent,
                CommandResultCallback callback, EntityAnchorArgument.Anchor anchor, CommandSigningContext signingContext,
                TaskChainer chainer);
    }

    /** {@code BlockPredicateArgument$BlockPredicate} */
    public interface BlockPredicate
    {
        BlockState blossom$state();

        TagKey<Block> blossom$tagKey();

        Map<Value, Value> blossom$properties();

        CompoundTag blossom$dataTag();
    }
}
