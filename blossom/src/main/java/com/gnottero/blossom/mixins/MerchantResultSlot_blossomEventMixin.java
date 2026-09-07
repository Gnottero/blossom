package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;

import com.gnottero.blossom.event.Fire;

/**
 * {@code player_trades}: fires when the trade result is really taken away.
 *
 * <p>The hook is the head of {@code onTake}, the one moment when everything is still undoable: the
 * result is in the hand of whoever clicked but the cost items are still in the slots and the
 * villager has not been told anything. Emptying that stack and stopping the method there is the
 * whole of the cancellation - the trade simply never happened, and the result slot fills itself
 * back up from the costs that were never spent.
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlot_blossomEventMixin
{
    @Shadow
    @Final
    private Merchant merchant;

    @Shadow
    @Final
    private MerchantContainer slots;

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void blossom$onTrade(Player player, ItemStack stack, CallbackInfo ci)
    {
        if (player instanceof final ServerPlayer serverPlayer && !player.level().isClientSide()
                && Fire.trade(serverPlayer, merchant, slots.getActiveOffer()))
        {
            stack.setCount(0);
            ci.cancel();
        }
    }
}
