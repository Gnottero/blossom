package com.gnottero.blossom.mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * {@code explosion_outcome}: what the blast really hit, and what it is about to break.
 *
 * <p>The entities hit are not known in advance, so they are collected while the game hurts them and
 * handed to the event afterwards.
 *
 * <p>Afterwards, but not too late: the hook sits between the two halves of {@code explode}, with the
 * entities already hurt and the blocks only chosen. That is what makes the event cancellable at all
 * - answering yes leaves every block standing and lights no fires, and the count of broken blocks
 * the method returns becomes the zero it now is.
 */
@Mixin(value = ServerExplosion.class, priority = 990)
public abstract class Explosion_blossomEventMixin
{
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Vec3 center;

    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private boolean fire;

    @Shadow
    @Final
    private Explosion.BlockInteraction blockInteraction;

    @Shadow
    @Final
    private @Nullable Entity source;

    @Shadow
    public abstract @Nullable LivingEntity getIndirectSourceEntity();

    @Unique
    private final List<Entity> blossom$affected = new ArrayList<>();

    @Inject(method = "explode", at = @At("HEAD"))
    private void blossom$beforeExplode(CallbackInfoReturnable<Integer> cir)
    {
        blossom$affected.clear();
    }

    @Redirect(method = "hurtEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;onExplosionHit(Lnet/minecraft/world/entity/Entity;)V"))
    private void blossom$onEntityHit(Entity hit, Entity source)
    {
        blossom$affected.add(hit);
        hit.onExplosionHit(source);
    }

    @Inject(method = "explode", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;hurtEntities()V",
            shift = At.Shift.AFTER))
    private void blossom$afterExplode(CallbackInfoReturnable<Integer> cir, List<BlockPos> blocks)
    {
        if (Event.EXPLOSION_OUTCOME.isListened() && !level.isClientSide()
                && Fire.explosionOutcome(level, source, getIndirectSourceEntity(), center, radius, fire,
                        blockInteraction.name().toLowerCase(Locale.ROOT), blocks, blossom$affected))
        {
            cir.setReturnValue(0);
        }
    }
}
