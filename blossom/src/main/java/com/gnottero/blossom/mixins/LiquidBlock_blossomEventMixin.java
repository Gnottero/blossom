package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * The part of {@code block_forms} that comes from the liquid block (inventory entry BE-3):
 * obsidian or cobblestone where water meets lava, and basalt over soul soil.
 *
 * <p>Two writes in the same method, told apart only by order: they are two different formations for
 * the game, but the same thing for whoever listens to the event, so they go through the same point.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlock_blossomEventMixin
{
    @Unique
    private static boolean blossom$formed(Level level, BlockPos pos, BlockState newState)
    {
        if (Event.BLOCK_FORMS.isListened() && level instanceof ServerLevel serverLevel
                && Fire.blockForms(serverLevel, pos, level.getBlockState(pos), newState))
        {
            return false;
        }
        return level.setBlockAndUpdate(pos, newState);
    }

    @Redirect(method = "shouldSpreadLiquid", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            ordinal = 0))
    private boolean blossom$onObsidianOrCobblestoneForms(Level level, BlockPos pos, BlockState newState)
    {
        return blossom$formed(level, pos, newState);
    }

    @Redirect(method = "shouldSpreadLiquid", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            ordinal = 1))
    private boolean blossom$onBasaltForms(Level level, BlockPos pos, BlockState newState)
    {
        return blossom$formed(level, pos, newState);
    }
}
