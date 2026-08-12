package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.event.EntityEvents;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Morte e danno di un'entita' qualsiasi: alimentano gli handler attaccati alla singola entita'. */
@Mixin(LivingEntity.class)
public abstract class LivingEntity_blossomEventsMixin extends Entity
{
    protected LivingEntity_blossomEventsMixin(EntityType<?> type, Level level)
    {
        super(type, level);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void blossom$onDeath(DamageSource source, CallbackInfo ci)
    {
        ((Access.EntityState) this).blossom$events().fire(EntityEvents.Kind.ON_DEATH, source.getMsgId());
    }

    @Inject(method = "actuallyHurt", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            shift = At.Shift.BEFORE))
    private void blossom$onDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci)
    {
        ((Access.EntityState) this).blossom$events().fire(EntityEvents.Kind.ON_DAMAGE, amount, source);
        if (Fire.dealsDamage(Event.PLAYER_DEALS_DAMAGE, this, amount, source))
        {
            ci.cancel();
        }
    }
}
