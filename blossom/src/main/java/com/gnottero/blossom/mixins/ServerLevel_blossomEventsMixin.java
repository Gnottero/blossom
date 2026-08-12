package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Gli eventi che appartengono al mondo: fulmini, esplosioni e chunk scaricati. */
@Mixin(ServerLevel.class)
public abstract class ServerLevel_blossomEventsMixin extends Level
{
    @Shadow
    public abstract GameRules getGameRules();

    protected ServerLevel_blossomEventsMixin(WritableLevelData levelData, ResourceKey<Level> dimension,
            RegistryAccess registryAccess, Holder<DimensionType> dimensionType, boolean isClientSide, boolean debug,
            long biomeZoomSeed, int maxChainedNeighborUpdates)
    {
        super(levelData, dimension, registryAccess, dimensionType, isClientSide, debug, biomeZoomSeed,
                maxChainedNeighborUpdates);
    }

    @Inject(method = "tickThunder", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            shift = At.Shift.BEFORE, ordinal = 1))
    private void blossom$onLightning(LevelChunk chunk, CallbackInfo ci, ChunkPos chunkPos, boolean thundering,
            int i, int j, ProfilerFiller profiler, BlockPos pos, DifficultyInstance difficulty,
            boolean skeletonHorse, LightningBolt bolt)
    {
        Fire.lightning((ServerLevel) (Object) this, pos, skeletonHorse);
    }

    @Unique
    private Explosion.BlockInteraction blossom$destroyType(GameRule<Boolean> rule)
    {
        return getGameRules().get(rule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY
                : Explosion.BlockInteraction.DESTROY;
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void blossom$onExplosion(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator,
            double x, double y, double z, float power, boolean createFire, ExplosionInteraction interaction,
            ParticleOptions smallParticle, ParticleOptions largeParticle,
            WeightedList<ExplosionParticleInfo> particles, Holder<SoundEvent> sound, CallbackInfo ci)
    {
        if (!Event.EXPLOSION.isListened())
        {
            return;
        }
        Explosion.BlockInteraction blockInteraction = switch (interaction)
        {
            case NONE -> Explosion.BlockInteraction.KEEP;
            case BLOCK -> blossom$destroyType(GameRules.BLOCK_EXPLOSION_DROP_DECAY);
            case MOB -> getGameRules().get(GameRules.MOB_GRIEFING)
                    ? blossom$destroyType(GameRules.MOB_EXPLOSION_DROP_DECAY)
                    : Explosion.BlockInteraction.KEEP;
            case TNT -> blossom$destroyType(GameRules.TNT_EXPLOSION_DROP_DECAY);
            case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
        };
        boolean cancelled = Fire.explosion((ServerLevel) (Object) this, source, null, new Vec3(x, y, z), power,
                createFire, blockInteraction.name().toLowerCase(Locale.ROOT));
        if (cancelled)
        {
            ci.cancel();
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void blossom$onChunkUnload(LevelChunk chunk, CallbackInfo ci)
    {
        Fire.chunk(Event.CHUNK_UNLOADED, (ServerLevel) (Object) this, chunk.getPos());
    }
}
