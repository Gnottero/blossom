package com.gnottero.blossom.access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.Ticket;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import com.gnottero.blossom.event.EntityEvents;
import com.gnottero.blossom.mixins.access.Objective_blossomAccessMixin;
import com.gnottero.blossom.mixins.access.PoiRecord_blossomAccessMixin;
import com.gnottero.blossom.mixins.access.Scoreboard_blossomAccessMixin;
import blossom.value.MapValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * The facade above {@link Access}: one method for every piece of state the game does not expose.
 *
 * <p>It exists for a single reason: keeping the casts to mixins in one place. If a Minecraft
 * version moves a field, the fix happens here and in the mixin, not in twenty spots across the API.
 */
public final class Vanilla
{
    private Vanilla()
    {
    }

    // --- server ---

    public static void MinecraftServer_forceTick(MinecraftServer server, BooleanSupplier until)
    {
        ((Access.Server) server).blossom$forceTick(until);
    }

    public static LevelStorageSource.LevelStorageAccess MinecraftServer_storageSource(MinecraftServer server)
    {
        return ((Access.Server) server).blossom$storage();
    }

    public static boolean isDevelopmentEnvironment()
    {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** The loaded mods, name to version: this is what {@code system_info} calls 'server_mods'. */
    public static MapValue getServerMods(MinecraftServer server)
    {
        Map<Value, Value> mods = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods())
        {
            mods.put(StringValue.of(mod.getMetadata().getId()),
                    StringValue.of(mod.getMetadata().getVersion().getFriendlyString()));
        }
        return MapValue.wrap(mods);
    }

    // --- world ---

    public static PotentialCalculator SpawnState_getPotentialCalculator(NaturalSpawner.SpawnState state)
    {
        return ((Access.SpawnState) state).blossom$potentialCalculator();
    }

    public static Biome.ClimateSettings Biome_getClimateSettings(Biome biome)
    {
        return ((Access.BiomeClimate) (Object) biome).blossom$climate();
    }

    public static int PoiRecord_getFreeTickets(PoiRecord record)
    {
        return ((PoiRecord_blossomAccessMixin) record).blossom$freeTickets();
    }

    public static void PoiRecord_callAcquireTicket(PoiRecord record)
    {
        ((PoiRecord_blossomAccessMixin) record).blossom$acquireTicket();
    }

    /**
     * In Carpet this was a global switch for generating structures without vanilla's checks.
     * Here there is no mod rule driving it: it stays off, and structure generation always goes
     * through the checks.
     */
    public static ThreadLocal<Boolean> skipGenerationChecks(ServerLevel level)
    {
        return SKIP_GENERATION_CHECKS;
    }

    private static final ThreadLocal<Boolean> SKIP_GENERATION_CHECKS = ThreadLocal.withInitial(() -> false);

    /**
     * The number vanilla uses to scale the mob cap per chunk (17*17, the area around a player
     * that counts for spawning).
     */
    public static int NaturalSpawner_MAGIC_NUMBER()
    {
        return 17 * 17;
    }

    /**
     * A world's chunk-loading tickets, from where the game keeps them: the {@code TicketStorage} of
     * its chunk source, which is what the mixin reads (the distance manager only looks at it).
     */
    public static Long2ObjectOpenHashMap<List<Ticket>> TicketStorage_getTicketsByPosition(ServerLevel level)
    {
        return ((Access.Tickets) level.getChunkSource().ticketStorage).blossom$ticketsByPosition();
    }

    // --- scoreboard ---

    public static void Objective_setCriterion(Objective objective, ObjectiveCriteria criterion)
    {
        ((Objective_blossomAccessMixin) objective).blossom$setCriterion(criterion);
    }

    public static Map<ObjectiveCriteria, List<Objective>> Scoreboard_getObjectivesByCriterion(Scoreboard scoreboard)
    {
        return ((Scoreboard_blossomAccessMixin) scoreboard).blossom$objectivesByCriterion();
    }

    // --- entities ---

    public static EntityEvents Entity_getEventContainer(Entity entity)
    {
        return ((Access.EntityState) entity).blossom$events();
    }

    public static boolean Entity_isPermanentVehicle(Entity entity)
    {
        return ((Access.EntityState) entity).blossom$permanentVehicle();
    }

    public static void Entity_setPermanentVehicle(Entity entity, boolean permanent)
    {
        ((Access.EntityState) entity).blossom$setPermanentVehicle(permanent);
    }

    public static int Entity_getPortalTimer(Entity entity)
    {
        return ((Access.EntityState) entity).blossom$portalTimer();
    }

    public static void Entity_setPortalTimer(Entity entity, int amount)
    {
        ((Access.EntityState) entity).blossom$setPortalTimer(amount);
    }

    public static int Entity_getPublicNetherPortalCooldown(Entity entity)
    {
        return ((Access.EntityState) entity).blossom$portalCooldown();
    }

    public static void Entity_setPublicNetherPortalCooldown(Entity entity, int amount)
    {
        ((Access.EntityState) entity).blossom$setPortalCooldown(amount);
    }

    public static int ItemEntity_getPickupDelay(ItemEntity entity)
    {
        return ((Access.ItemEntityDelay) entity).blossom$pickupDelay();
    }

    public static boolean LivingEntity_isJumping(LivingEntity entity)
    {
        return ((Access.Jumping) entity).blossom$isJumping();
    }

    public static void LivingEntity_setJumping(LivingEntity entity)
    {
        ((Access.Jumping) entity).blossom$jump();
    }

    public static GoalSelector Mob_getAI(Mob mob, boolean target)
    {
        return ((Access.MobAi) mob).blossom$goals(target);
    }

    public static Map<String, Goal> Mob_getTemporaryTasks(Mob mob)
    {
        return ((Access.MobAi) mob).blossom$temporaryTasks();
    }

    public static void Mob_setPersistence(Mob mob, boolean persistent)
    {
        ((Access.MobAi) mob).blossom$setPersistence(persistent);
    }

    public static Map<MemoryModuleType<?>, MemorySlot<?>> Brain_getMemories(Brain<?> brain)
    {
        return ((Access.BrainMemories) brain).blossom$memories();
    }

    public static Container AbstractHorse_getInventory(AbstractHorse horse)
    {
        return ((Access.InventoryBearer) horse).blossom$inventory();
    }

    public static double FoodData_getExhaustion(FoodData food)
    {
        return ((Access.Food) food).blossom$exhaustion();
    }

    public static void FoodData_setExhaustion(FoodData food, float exhaustion)
    {
        ((Access.Food) food).blossom$setExhaustion(exhaustion);
    }

    /**
     * In Carpet this distinguished fake players from real ones. Blossom has no fake players: a
     * player reference is valid for as long as the player is in the game.
     */
    public static boolean ServerPlayer_isInvalidEntityObject(ServerPlayer player)
    {
        return player.isRemoved();
    }

    // --- player breaking blocks ---

    public static BlockPos ServerPlayerGameMode_getCurrentBlockPosition(ServerPlayerGameMode gameMode)
    {
        return ((Access.GameMode) gameMode).blossom$breakingBlock();
    }

    public static int ServerPlayerGameMode_getCurrentBlockBreakingProgress(ServerPlayerGameMode gameMode)
    {
        return ((Access.GameMode) gameMode).blossom$breakingProgress();
    }

    public static void ServerPlayerGameMode_setBlockBreakingProgress(ServerPlayerGameMode gameMode, int progress)
    {
        ((Access.GameMode) gameMode).blossom$setBreakingProgress(progress);
    }

    // --- inventories and screens ---

    public static DataSlot AbstractContainerMenu_getDataSlot(AbstractContainerMenu menu, int index)
    {
        return ((Access.Menu) menu).blossom$dataSlot(index);
    }

    /** The permission level script code runs with: that of a game operator. */
    public static net.minecraft.server.permissions.PermissionSet MinecraftServer_getRunPermissionLevel(
            MinecraftServer server)
    {
        return net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER;
    }

    private static final java.util.regex.Pattern RELEASE = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** The Minecraft version it runs on, as a triple of numbers. */
    public static int[] MinecraftServer_getReleaseTarget(MinecraftServer server)
    {
        // "26.4", "26.4.1", "26.4 Snapshot 4", "26.4-rc-1": the numbers at the start, the rest is a label
        java.util.regex.Matcher m = RELEASE.matcher(net.minecraft.SharedConstants.getCurrentVersion().name());
        int[] target = new int[3];
        if (m.lookingAt())
        {
            for (int i = 0; i < 3; i++)
            {
                target[i] = m.group(i + 1) == null ? 0 : Integer.parseInt(m.group(i + 1));
            }
        }
        return target;
    }

    /** What is inside a block predicate written in a command. */
    public record BlockPredicatePayload(BlockState state, TagKey<Block> tagKey, Map<Value, Value> properties,
            CompoundTag tag)
    {
        public static BlockPredicatePayload of(Predicate<BlockInWorld> predicate)
        {
            Access.BlockPredicate data = (Access.BlockPredicate) predicate;
            return new BlockPredicatePayload(data.blossom$state(), data.blossom$tagKey(), data.blossom$properties(),
                    data.blossom$dataTag());
        }
    }

    /**
     * A structure template's palettes, which vanilla keeps private.
     *
     * <p>Needed by {@code structure_matches}: a template can have multiple variants of the same
     * volume, and "matches" means at least one of them matches.
     */
    public static List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette>
            StructureTemplate_palettes(
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template)
    {
        return ((com.gnottero.blossom.mixins.access.StructureTemplate_blossomAccessMixin) (Object) template).blossom$palettes();
    }
}
