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
 * La parte di {@code block_forms} che riguarda il cemento (voce BE-3 dell'inventario).
 *
 * <p>Due strade diverse portano allo stesso risultato e vanno intercettate tutte e due: la polvere
 * che cade nell'acqua ({@code onLand}) e quella che ne viene toccata dopo essere gia' a terra
 * ({@code updateShape}). Nel secondo caso non c'e' nessuna scrittura da deviare - il gioco decide
 * guardando un booleano - quindi si annulla dicendo che l'acqua non c'e'.
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
