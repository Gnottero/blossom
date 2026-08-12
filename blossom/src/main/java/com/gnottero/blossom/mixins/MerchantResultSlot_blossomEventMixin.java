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

/** {@code player_trades}: scatta quando lo scambio viene davvero portato via. */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlot_blossomEventMixin
{
    @Shadow
    @Final
    private Merchant merchant;

    @Shadow
    @Final
    private MerchantContainer slots;

    @Inject(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
    private void blossom$onTrade(Player player, ItemStack stack, CallbackInfo ci)
    {
        if (player instanceof final ServerPlayer serverPlayer && !player.level().isClientSide())
        {
            Fire.trade(serverPlayer, merchant, slots.getActiveOffer());
        }
    }
}
