package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Placing a block: before ({@code player_placing_block}) and after ({@code player_places_block}). */
@Mixin(BlockItem.class)
public class BlockItem_blossomEventMixin
{
    /**
     * What was in that spot before the placement, kept for the length of one {@code place} call.
     *
     * <p>An item is a singleton shared by everybody, so this looks like state that two players
     * could trample. They cannot: {@code placeBlock} and the hook that reads this run inside the
     * same synchronous {@code place}, on the server thread, one placement at a time. It is only
     * filled when somebody is listening, so the ordinary placement does not even read the world
     * for it.
     */
    @Unique
    private @Nullable BlockState blossom$replaced;

    /**
     * {@code player_places_block}: cancelling puts back what was there and stops what the placement
     * had left to do - the sound, the advancement, the item leaving the hand.
     */
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            cancellable = true,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER))
    private void blossom$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir)
    {
        BlockState replaced = blossom$replaced;
        blossom$replaced = null;
        if (context.getPlayer() instanceof final ServerPlayer player
                && Fire.blockPlaced(Event.PLAYER_PLACES_BLOCK, player, context.getClickedPos(), context.getHand(),
                        context.getItemInHand()))
        {
            // air when the state was not captured, which happens for the few items that place a
            // block their own way instead of going through placeBlock: the block goes away either
            // way, only what was underneath it is lost
            context.getLevel().setBlock(context.getClickedPos(),
                    replaced != null ? replaced : Blocks.AIR.defaultBlockState(), 3);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void blossom$beforePlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir)
    {
        if (Event.PLAYER_PLACES_BLOCK.isListened())
        {
            blossom$replaced = context.getLevel().getBlockState(context.getClickedPos());
        }
        if (context.getPlayer() instanceof final ServerPlayer player
                && Fire.blockPlaced(Event.PLAYER_PLACING_BLOCK, player, context.getClickedPos(), context.getHand(),
                        context.getItemInHand()))
        {
            cir.setReturnValue(false);
        }
    }
}
