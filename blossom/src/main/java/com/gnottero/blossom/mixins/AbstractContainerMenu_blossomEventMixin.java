package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * {@code player_clicks_item_on_item}: an item clicked onto another item in a container.
 *
 * <p>The hook is the point where the game asks the two stacks whether they want to handle the click
 * themselves instead of the ordinary swap - the door a bundle goes through. Answering {@code true}
 * means "handled", so returning true from a cancelling handler is exactly the game's own way of
 * saying that nothing else must happen.
 *
 * <p>It fires only when <b>both</b> stacks are there: picking an item up from a slot, or putting one
 * into an empty one, is not an item clicked onto another item.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenu_blossomEventMixin
{
    @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
    private void blossom$onItemOnItem(Player player, ClickAction action, Slot slot, ItemStack clicked,
            ItemStack carried, CallbackInfoReturnable<Boolean> cir)
    {
        if (!Event.PLAYER_CLICKS_ITEM_ON_ITEM.isListened() || clicked.isEmpty() || carried.isEmpty()
                || player.level().isClientSide() || !(player instanceof final ServerPlayer serverPlayer))
        {
            return;
        }
        if (Fire.itemOnItem(serverPlayer, carried, clicked, slot.index, action == ClickAction.SECONDARY))
        {
            cir.setReturnValue(true);
        }
    }
}
