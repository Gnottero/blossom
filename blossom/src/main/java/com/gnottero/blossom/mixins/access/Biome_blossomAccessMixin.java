package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.level.biome.Biome;

import com.gnottero.blossom.access.Access;

/** Temperatura e umidita' di un bioma, che vanilla tiene private. */
@Mixin(Biome.class)
public class Biome_blossomAccessMixin implements Access.BiomeClimate
{
    @Shadow
    private Biome.ClimateSettings climateSettings;

    @Override
    public Biome.ClimateSettings blossom$climate()
    {
        return climateSettings;
    }
}
