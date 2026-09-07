package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
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

/** The events belonging to the world: lightning, explosions and unloaded chunks. */
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

    /**
     * {@code lightning}: the bolt, and the skeleton horse trap that sometimes comes with it.
     *
     * <p>Both are added to the world by the same method, one after the other, so one redirect
     * covers them: the event is raised at the first of the two that shows up and the answer is
     * remembered for the second, otherwise cancelling the bolt would leave a horse behind and a
     * strike with a trap would raise two events.
     */
    @Redirect(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean blossom$onLightning(ServerLevel level, Entity entity)
    {
        if (entity instanceof SkeletonHorse)
        {
            boolean cancelled = Fire.lightning(level, entity.blockPosition(), true);
            blossom$lightningCancelled = cancelled;
            return !cancelled && level.addFreshEntity(entity);
        }
        if (entity instanceof LightningBolt)
        {
            Boolean decided = blossom$lightningCancelled;
            blossom$lightningCancelled = null;
            boolean cancelled = decided != null ? decided : Fire.lightning(level, entity.blockPosition(), false);
            return !cancelled && level.addFreshEntity(entity);
        }
        return level.addFreshEntity(entity);
    }

    /** The answer given for the horse, waiting for the bolt that follows it. Server thread only. */
    @Unique
    private Boolean blossom$lightningCancelled;

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
