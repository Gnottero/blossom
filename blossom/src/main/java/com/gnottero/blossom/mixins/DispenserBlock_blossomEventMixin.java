package com.gnottero.blossom.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.Value;

/**
 * Genera {@code block_dispenses}, portato dal fork (voce BE-4 dell'inventario).
 *
 * <p>L'handler puo' annullare l'erogazione restituendo un valore vero.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlock_blossomEventMixin
{
    @Inject(method = "dispenseFrom", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            shift = At.Shift.BEFORE))
    private void blossom$onDispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci,
            DispenserBlockEntity blockEntity, BlockSource source, int slot, ItemStack stack,
            DispenseItemBehavior behavior)
    {
        if (!Event.BLOCK_DISPENSES.isListened())
        {
            return;
        }
        List<Value> arguments = List.of(ValueConversions.of(pos),
                ValueConversions.of(stack, level.registryAccess()));
        if (Event.BLOCK_DISPENSES.fireInLevel(level, () -> arguments))
        {
            ci.cancel();
        }
    }
}
