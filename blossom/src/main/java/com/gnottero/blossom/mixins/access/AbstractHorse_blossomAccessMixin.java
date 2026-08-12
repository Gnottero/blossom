package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import com.gnottero.blossom.access.Access;

/** La sella e la bisaccia di un cavallo sono un inventario a parte. */
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
