package com.gnottero.blossom.access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
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
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
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
 * La facciata sopra {@link Access}: un metodo per ogni cosa che il gioco non espone.
 *
 * <p>Esiste per un motivo solo: tenere i cast ai mixin in un posto solo. Se una versione di
 * Minecraft sposta un campo, si aggiusta qui e nel mixin, non in venti punti dell'API.
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

    /** I mod caricati, nome → versione: e' quello che {@code system_info} chiama 'server_mods'. */
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

    // --- mondo ---

    public static PotentialCalculator SpawnState_getPotentialCalculator(NaturalSpawner.SpawnState state)
    {
        return ((Access.SpawnState) state).blossom$potentialCalculator();
    }

    public static Long2ObjectOpenHashMap<List<Ticket>> TicketStorage_getTicketsByPosition(TicketStorage storage)
    {
        return ((Access.Tickets) storage).blossom$ticketsByPosition();
    }

    public static DensityFunction.Visitor RandomState_getVisitor(RandomState state)
    {
        return ((Access.RandomStateVisitor) (Object) state).blossom$visitor();
    }

    public static Biome.ClimateSettings Biome_getClimateSettings(Biome biome)
    {
        return ((Access.BiomeClimate) (Object) biome).blossom$climate();
    }

    public static CompoundTag BlockInput_getTag(BlockInput input)
    {
        return ((Access.BlockInputTag) input).blossom$tag();
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
     * In Carpet questo era un interruttore globale per generare strutture senza i controlli di
     * vanilla. Qui non c'e' nessuna regola di mod a comandarlo: resta spento, e chi genera struttura
     * passa comunque dai controlli.
     */
    public static ThreadLocal<Boolean> skipGenerationChecks(ServerLevel level)
    {
        return SKIP_GENERATION_CHECKS;
    }

    private static final ThreadLocal<Boolean> SKIP_GENERATION_CHECKS = ThreadLocal.withInitial(() -> false);

    /**
     * Il numero con cui vanilla scala il tetto di mob per chunk (17*17, l'area attorno a un
     * giocatore che conta per lo spawn).
     */
    public static int NaturalSpawner_MAGIC_NUMBER()
    {
        return 17 * 17;
    }

    /** I ticket di caricamento chunk di un mondo. */
    public static Long2ObjectOpenHashMap<List<Ticket>> ChunkTicketManager_getTicketsByPosition(
            net.minecraft.server.level.DistanceManager manager)
    {
        return ((Access.Tickets) (Object) manager).blossom$ticketsByPosition();
    }

    // --- punteggi ---

    public static void Objective_setCriterion(Objective objective, ObjectiveCriteria criterion)
    {
        ((Objective_blossomAccessMixin) objective).blossom$setCriterion(criterion);
    }

    public static Map<ObjectiveCriteria, List<Objective>> Scoreboard_getObjectivesByCriterion(Scoreboard scoreboard)
    {
        return ((Scoreboard_blossomAccessMixin) scoreboard).blossom$objectivesByCriterion();
    }

    // --- entita' ---

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
     * In Carpet questo distingueva i giocatori finti da quelli veri. Blossom non ha giocatori finti:
     * un riferimento a un giocatore e' valido finche' il giocatore e' nel gioco.
     */
    public static boolean ServerPlayer_isInvalidEntityObject(ServerPlayer player)
    {
        return player.isRemoved();
    }

    // --- giocatore che rompe blocchi ---

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

    // --- inventari e schermate ---

    public static DataSlot AbstractContainerMenu_getDataSlot(AbstractContainerMenu menu, int index)
    {
        return ((Access.Menu) menu).blossom$dataSlot(index);
    }

    /** Il permesso con cui gira il codice degli script: quello di un operatore di gioco. */
    public static net.minecraft.server.permissions.PermissionSet MinecraftServer_getRunPermissionLevel(
            MinecraftServer server)
    {
        return net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER;
    }

    /** La versione di Minecraft su cui gira, come tripletta di numeri. */
    public static int[] MinecraftServer_getReleaseTarget(MinecraftServer server)
    {
        String version = net.minecraft.SharedConstants.getCurrentVersion().name();
        String[] parts = version.split("[.-]");
        int[] target = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++)
        {
            try
            {
                target[i] = Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException ignored)
            {
                target[i] = 0;
            }
        }
        return target;
    }

    /** Quello che c'e' dentro un predicato di blocco scritto in un comando. */
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
     * Le palette di un template di struttura, che vanilla tiene private.
     *
     * <p>Servono a {@code structure_matches}: un template puo' avere piu' varianti dello stesso
     * volume, e "combacia" vuol dire che ne combacia almeno una.
     */
    public static List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette>
            StructureTemplate_palettes(
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template)
    {
        return ((com.gnottero.blossom.mixins.access.StructureTemplate_blossomAccessMixin) (Object) template).blossom$palettes();
    }

    /** Costruisce una sorgente comando "silenziosa" per {@code run()}. */
    public static void CommandSourceStack_setupPrivates(CommandSourceStack source, boolean silent,
            net.minecraft.commands.CommandResultCallback callback,
            net.minecraft.commands.arguments.EntityAnchorArgument.Anchor anchor,
            net.minecraft.commands.CommandSigningContext signingContext, net.minecraft.util.TaskChainer chainer)
    {
        ((Access.Source) source).blossom$setupPrivates(silent, callback, anchor, signingContext, chainer);
    }
}
