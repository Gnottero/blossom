package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

import com.gnottero.blossom.access.Access;

/** Captures the generator's visitor: needed by {@code world.sample_noise}. */
@Mixin(RandomState.class)
public class RandomState_blossomAccessMixin implements Access.RandomStateVisitor
{
    @Unique
    private DensityFunction.Visitor blossom$captured;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),
            index = 0)
    private DensityFunction.Visitor blossom$captureVisitor(DensityFunction.Visitor visitor)
    {
        this.blossom$captured = visitor;
        return visitor;
    }

    @Override
    public DensityFunction.Visitor blossom$visitor()
    {
        return blossom$captured;
    }
}
