package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import com.gnottero.blossom.access.Access;

/** A horse's saddle and saddlebag are a separate inventory. */
@Mixin(AbstractHorse.class)
public class AbstractHorse_blossomAccessMixin implements Access.InventoryBearer
{
    @Shadow
    protected SimpleContainer inventory;

    @Override
    public Container blossom$inventory()
    {
        return inventory;
    }
}
