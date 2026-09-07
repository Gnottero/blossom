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

/** {@code player_picks_up_item}: what is about to enter the inventory, in time to say no. */
@Mixin(Inventory.class)
public abstract class Inventory_blossomEventMixin
{
    @Shadow
    @Final
    public Player player;

    /**
     * The event fires <b>before</b> the stack is put away, and not after with the part that fit:
     * an event that can stop a pickup has to be asked while the item is still on the ground.
     * Answering false here is what {@code add} answers when the inventory is full, so the item
     * stays where it is with no special case anywhere else.
     */
    @Redirect(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(ILnet/minecraft/world/item/ItemStack;)Z"))
    private boolean blossom$onPickup(Inventory inventory, int slot, ItemStack stack)
    {
        if (Event.PLAYER_PICKS_UP_ITEM.isListened() && player instanceof final ServerPlayer serverPlayer
                && Fire.pickup(Event.PLAYER_PICKS_UP_ITEM, serverPlayer, stack.copy()))
        {
            return false;
        }
        return inventory.add(slot, stack);
    }
}
