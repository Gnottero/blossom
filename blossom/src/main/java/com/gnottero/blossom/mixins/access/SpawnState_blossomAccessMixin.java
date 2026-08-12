package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;

import com.gnottero.blossom.access.Access;

/** Il potenziale di spawn calcolato nel tick corrente, per {@code world.spawn_potential}. */
@Mixin(NaturalSpawner.SpawnState.class)
public class SpawnState_blossomAccessMixin implements Access.SpawnState
{
    @Shadow
    @Final
    private PotentialCalculator spawnPotential;

    @Override
    public PotentialCalculator blossom$potentialCalculator()
    {
        return spawnPotential;
    }
}
