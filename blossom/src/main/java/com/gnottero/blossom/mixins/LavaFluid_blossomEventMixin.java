package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * La parte di {@code block_forms} in cui e' la lava a diffondersi (voce BE-3 dell'inventario):
 * pietra, ossidiana o basalto formati dove la lava incontra l'acqua.
 */
@Mixin(LavaFluid.class)
public abstract class LavaFluid_blossomEventMixin
{
    @Redirect(method = "spreadTo", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean blossom$onStoneForms(LevelAccessor level, BlockPos pos, BlockState newState)
    {
        if (Event.BLOCK_FORMS.isListened() && level instanceof ServerLevel serverLevel
                && Fire.blockForms(serverLevel, pos, serverLevel.getBlockState(pos), newState))
        {
            return false;
        }
        return level.setBlockAndUpdate(pos, newState);
    }
}
