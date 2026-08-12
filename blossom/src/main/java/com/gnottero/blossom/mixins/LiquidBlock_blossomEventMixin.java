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
 * La parte di {@code block_forms} che nasce dal blocco di liquido (voce BE-3 dell'inventario):
 * ostruzione o pietrisco dove l'acqua incontra la lava, e basalto sul soul soil.
 *
 * <p>Due scritture nello stesso metodo, distinte solo dall'ordine: sono due formazioni diverse per
 * il gioco, ma la stessa cosa per chi ascolta l'evento, quindi passano dallo stesso punto.
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
