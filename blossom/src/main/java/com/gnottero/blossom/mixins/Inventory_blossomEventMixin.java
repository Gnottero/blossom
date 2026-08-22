package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** {@code player_picks_up_item}: what really enters the inventory, not what is touched. */
@Mixin(Inventory.class)
public abstract class Inventory_blossomEventMixin
{
    @Shadow
    @Final
    public Player player;

    @Redirect(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(ILnet/minecraft/world/item/ItemStack;)Z"))
    private boolean blossom$onPickup(Inventory inventory, int slot, ItemStack stack)
    {
        if (!Event.PLAYER_PICKS_UP_ITEM.isListened() || !(player instanceof final ServerPlayer serverPlayer))
        {
            return inventory.add(-1, stack);
        }
        int before = stack.getCount();
        ItemStack previous = stack.copy();
        boolean result = inventory.add(-1, stack);
        // add() returns false even when part of the stack went in: the difference is what counts
        if (before != stack.getCount())
        {
            Fire.pickup(Event.PLAYER_PICKS_UP_ITEM, serverPlayer, previous.copyWithCount(before - stack.getCount()));
        }
        return result;
    }
}
