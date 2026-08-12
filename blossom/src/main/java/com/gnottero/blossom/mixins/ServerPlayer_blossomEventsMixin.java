package com.gnottero.blossom.mixins;

import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.event.EntityEvents;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Statistiche, morte, cambio dimensione e fine dell'uso di un oggetto. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayer_blossomEventsMixin extends Player
{
    @Shadow
    public boolean wonGame;

    @Unique
    private Vec3 blossom$previousLocation;

    @Unique
    private ResourceKey<Level> blossom$previousDimension;

    protected ServerPlayer_blossomEventsMixin(Level level, GameProfile profile)
    {
        super(level, profile);
    }

    @Redirect(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;completeUsingItem()V"))
    private void blossom$onFinishUsingItem(Player player)
    {
        if (!Event.PLAYER_FINISHES_USING_ITEM.isListened())
        {
            super.completeUsingItem();
            return;
        }
        InteractionHand hand = getUsedItemHand();
        if (!Fire.item(Event.PLAYER_FINISHES_USING_ITEM, (ServerPlayer) (Object) this, hand, getUseItem()))
        {
            super.completeUsingItem();
        }
    }

    @Inject(method = "awardStat", at = @At("HEAD"))
    private void blossom$onStat(Stat<?> stat, int amount, CallbackInfo ci)
    {
        Fire.statistic((ServerPlayer) (Object) this, stat, amount);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void blossom$onDeath(DamageSource source, CallbackInfo ci)
    {
        ((Access.EntityState) this).blossom$events().fire(EntityEvents.Kind.ON_DEATH, source.getMsgId());
        Fire.player(Event.PLAYER_DIES, (ServerPlayer) (Object) this);
    }

    @Inject(method = "teleport", at = @At("HEAD"))
    private void blossom$rememberOrigin(TeleportTransition transition, CallbackInfoReturnable<Entity> cir)
    {
        blossom$previousLocation = position();
        blossom$previousDimension = level().dimension();
    }

    @Inject(method = "teleport", at = @At("RETURN"))
    private void blossom$onDimensionChange(TeleportTransition transition, CallbackInfoReturnable<Entity> cir)
    {
        if (!Event.PLAYER_CHANGES_DIMENSION.isListened())
        {
            return;
        }
        ServerLevel destination = transition.newLevel();
        // finire la partita porta all'overworld senza una posizione di arrivo sensata
        Vec3 to = wonGame && blossom$previousDimension == Level.END && destination.dimension() == Level.OVERWORLD
                ? null
                : position();
        Fire.dimensionChange((ServerPlayer) (Object) this, blossom$previousLocation, to, blossom$previousDimension,
                destination.dimension());
    }
}
