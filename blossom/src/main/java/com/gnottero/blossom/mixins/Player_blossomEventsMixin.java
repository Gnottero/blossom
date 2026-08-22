package com.gnottero.blossom.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.event.EntityEvents;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** The events born from what a player does to someone else, or has done to them. */
@Mixin(Player.class)
public abstract class Player_blossomEventsMixin extends LivingEntity
{
    protected Player_blossomEventsMixin(EntityType<? extends LivingEntity> type, Level level)
    {
        super(type, level);
    }

    @Inject(method = "actuallyHurt", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private void blossom$onDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci)
    {
        ((Access.EntityState) this).blossom$events().fire(EntityEvents.Kind.ON_DAMAGE, amount, source);
        if (Fire.takesDamage(Event.PLAYER_TAKES_DAMAGE, this, amount, source))
        {
            ci.cancel();
        }
        if (Fire.dealsDamage(Event.PLAYER_DEALS_DAMAGE, this, amount, source))
        {
            ci.cancel();
        }
    }

    @Inject(method = "touch", at = @At("HEAD"))
    private void blossom$onCollide(Entity entity, CallbackInfo ci)
    {
        if (!level().isClientSide() && (Object) this instanceof final ServerPlayer player)
        {
            Fire.entity(Event.PLAYER_COLLIDES_WITH_ENTITY, player, entity, null);
        }
    }

    @Inject(method = "interactOn", cancellable = true, at = @At("HEAD"))
    private void blossom$onInteract(Entity entity, InteractionHand hand, Vec3 pos,
            CallbackInfoReturnable<InteractionResult> cir)
    {
        if (!level().isClientSide() && (Object) this instanceof final ServerPlayer player
                && Fire.entity(Event.PLAYER_INTERACTS_WITH_ENTITY, player, entity, hand))
        {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void blossom$onAttack(Entity target, CallbackInfo ci)
    {
        if (!level().isClientSide() && target.isAttackable() && (Object) this instanceof final ServerPlayer player
                && Fire.entity(Event.PLAYER_ATTACKS_ENTITY, player, target, null))
        {
            ci.cancel();
        }
    }

    /** A mount marked permanent by a script does not let itself be dismounted. */
    @ModifyReturnValue(method = "wantsToStopRiding", at = @At("TAIL"))
    private boolean blossom$keepPermanentVehicle(boolean original)
    {
        Entity vehicle = getVehicle();
        return vehicle == null ? original : original && !((Access.EntityState) vehicle).blossom$permanentVehicle();
    }
}
