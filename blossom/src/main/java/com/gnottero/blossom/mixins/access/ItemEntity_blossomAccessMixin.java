package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.item.ItemEntity;

import com.gnottero.blossom.access.Access;

/** Il ritardo prima che un item per terra possa essere raccolto. */
@Mixin(ItemEntity.class)
public abstract class ItemEntity_blossomAccessMixin implements Access.ItemEntityDelay
{
    @Shadow
    private int pickupDelay;

    @Override
    public int blossom$pickupDelay()
    {
        return pickupDelay;
    }
}
