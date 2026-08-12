package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.PortalProcessor;

import com.gnottero.blossom.access.Access;

/** Quanto tempo un'entita' e' rimasta dentro un portale. */
@Mixin(PortalProcessor.class)
public class PortalProcessor_blossomAccessMixin implements Access.Portal
{
    @Shadow
    private int portalTime;

    @Override
    public void blossom$setPortalTime(int time)
    {
        portalTime = time;
    }
}
