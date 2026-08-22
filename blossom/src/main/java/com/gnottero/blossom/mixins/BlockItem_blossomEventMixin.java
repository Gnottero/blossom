package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Placing a block: before ({@code player_placing_block}) and after ({@code player_places_block}). */
@Mixin(BlockItem.class)
public class BlockItem_blossomEventMixin
{
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER))
    private void blossom$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir)
    {
        if (context.getPlayer() instanceof final ServerPlayer player)
        {
            Fire.blockPlaced(Event.PLAYER_PLACES_BLOCK, player, context.getClickedPos(), context.getHand(),
                    context.getItemInHand());
        }
    }

    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void blossom$beforePlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir)
    {
        if (context.getPlayer() instanceof final ServerPlayer player
                && Fire.blockPlaced(Event.PLAYER_PLACING_BLOCK, player, context.getClickedPos(), context.getHand(),
                        context.getItemInHand()))
        {
            cir.setReturnValue(false);
        }
    }
}
