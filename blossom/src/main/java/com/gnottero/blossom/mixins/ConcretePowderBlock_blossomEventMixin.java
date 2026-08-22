package com.gnottero.blossom.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * The part of {@code block_forms} that concerns concrete (inventory entry BE-3).
 *
 * <p>Two different paths lead to the same result and both must be intercepted: powder falling into
 * water ({@code onLand}) and powder that gets touched by water after already sitting on the ground
 * ({@code updateShape}). In the second case there's no write to redirect, the game decides just by
 * checking a boolean, so it's cancelled by reporting that the water isn't there.
 */
@Mixin(ConcretePowderBlock.class)
public abstract class ConcretePowderBlock_blossomEventMixin extends FallingBlock
{
    @Shadow
    @Final
    private Block concrete;

    protected ConcretePowderBlock_blossomEventMixin(Properties properties)
    {
        super(properties);
    }

    @Redirect(method = "onLand", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean blossom$onConcreteLand(Level level, BlockPos pos, BlockState newState)
    {
        if (Event.BLOCK_FORMS.isListened() && level instanceof ServerLevel serverLevel
                && Fire.blockForms(serverLevel, pos, level.getBlockState(pos), newState))
        {
            return false;
        }
        return level.setBlockAndUpdate(pos, newState);
    }

    @ModifyExpressionValue(method = "updateShape", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/ConcretePowderBlock;touchesLiquid(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean blossom$onConcreteTouchesLiquid(boolean touchesLiquid, BlockState blockState,
            LevelReader levelReader, ScheduledTickAccess scheduledTickAccess, BlockPos blockPos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (touchesLiquid && Event.BLOCK_FORMS.isListened() && levelReader instanceof ServerLevel serverLevel
                && Fire.blockForms(serverLevel, blockPos, blockState, concrete.defaultBlockState()))
        {
            return false;
        }
        return touchesLiquid;
    }
}
