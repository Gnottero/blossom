package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.food.FoodData;

import com.gnottero.blossom.access.Access;

/** The exhaustion level: read and written via {@code entity.query/modify}. */
@Mixin(FoodData.class)
public class FoodData_blossomAccessMixin implements Access.Food
{
    @Shadow
    private float exhaustionLevel;

    @Override
    public float blossom$exhaustion()
    {
        return exhaustionLevel;
    }

    @Override
    public void blossom$setExhaustion(float exhaustion)
    {
        exhaustionLevel = exhaustion;
    }
}
