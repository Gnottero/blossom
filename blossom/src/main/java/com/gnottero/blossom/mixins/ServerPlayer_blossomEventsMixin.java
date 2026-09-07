package com.gnottero.blossom.mixins;

import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.event.EntityEvents;
import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Statistics, death, dimension change and the end of using an item. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayer_blossomEventsMixin extends Player
{
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

    @Inject(method = "awardStat", at = @At("HEAD"), cancellable = true)
    private void blossom$onStat(Stat<?> stat, int amount, CallbackInfo ci)
    {
        if (Fire.statistic((ServerPlayer) (Object) this, stat, amount))
        {
            ci.cancel();
        }
    }

    /**
     * {@code player_dies}: not cancellable, and it is a deliberate line.
     *
     * <p>The health is already at zero when {@code die} is called, so stopping it would mean also
     * deciding how much life to hand back - a decision that belongs to whoever writes the script,
     * not to the shape of the event. {@code player_takes_damage} is asked before the blow lands
     * and stops it properly.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void blossom$onDeath(DamageSource source, CallbackInfo ci)
    {
        ((Access.EntityState) this).blossom$events().fire(EntityEvents.Kind.ON_DEATH, source.getMsgId());
        Fire.player(Event.PLAYER_DIES, (ServerPlayer) (Object) this);
    }

    /**
     * {@code player_changes_dimension}: fired before the move, with the destination the game has
     * already chosen.
     *
     * <p>Before and not after so that it can be cancelled, and because the transition carries the
     * arrival position even in the cases where reading it afterwards gives nothing sensible, like
     * finishing the game and leaving the end.
     *
     * <p>The descriptor is written out in full on purpose: {@code teleport} also exists as the
     * bridge method that returns {@code Entity}, and matching by name alone would fire the event
     * twice for one teleport.
     */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), cancellable = true)
    private void blossom$onDimensionChange(TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir)
    {
        if (Fire.dimensionChange((ServerPlayer) (Object) this, position(), transition.position(),
                level().dimension(), transition.newLevel().dimension()))
        {
            // what the game itself returns when the teleport does not happen: the player, where it was
            cir.setReturnValue((ServerPlayer) (Object) this);
        }
    }
}
