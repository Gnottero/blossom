package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/** Breaking a block and interacting with a block, from the game side that really decides. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameMode_blossomEventsMixin
{
    @Shadow
    public ServerPlayer player;

    @Shadow
    public ServerLevel level;

    @Inject(method = "destroyBlock", locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            shift = At.Shift.BEFORE))
    private void blossom$onBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir, BlockEntity blockEntity,
            Block block, BlockState state)
    {
        if (Fire.blockBroken(Event.PLAYER_BREAKS_BLOCK, player, pos, state))
        {
            // the client already believes it broke: the real state has to be sent back
            this.level.sendBlockUpdated(pos, state, state, 3);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/triggers/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemInstance;)V",
            shift = At.Shift.BEFORE))
    private void blossom$onInteract(ServerPlayer serverPlayer, Level world, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir)
    {
        Fire.blockHit(Event.PLAYER_INTERACTS_WITH_BLOCK, player, hand, hit);
    }
}
