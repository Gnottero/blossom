package com.gnottero.blossom.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.ValueConversions;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * Genera {@code bell_rings}, portato dal fork (voce EV-1 dell'inventario).
 *
 * <p>Gli argomenti sono posizione, direzione del colpo e chi l'ha suonata - {@code null} se a
 * suonarla e' stata la redstone.
 */
@Mixin(BellBlock.class)
public abstract class BellBlock_blossomEventMixin
{
    @Inject(
            method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BellBlockEntity;onHit(Lnet/minecraft/core/Direction;)V"),
            cancellable = true)
    private void blossom$onRing(Entity entity, Level level, BlockPos pos, Direction direction,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (!(level instanceof final ServerLevel serverLevel) || !Event.BELL_RINGS.isListened())
        {
            return;
        }
        List<Value> arguments = List.of(ValueConversions.of(pos),
                StringValue.of(direction.getName()),
                entity == null ? Value.NULL : new EntityValue(entity));
        if (Event.BELL_RINGS.fireInLevel(serverLevel, () -> arguments))
        {
            cir.setReturnValue(false);
        }
    }
}
