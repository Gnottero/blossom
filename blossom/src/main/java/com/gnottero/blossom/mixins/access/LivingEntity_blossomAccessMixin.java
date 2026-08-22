package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.LivingEntity;

import com.gnottero.blossom.access.Access;

/** Making an entity jump, and knowing whether it's jumping. */
@Mixin(LivingEntity.class)
public abstract class LivingEntity_blossomAccessMixin implements Access.Jumping
{
    @Shadow
    protected boolean jumping;

    @Shadow
    protected abstract void jumpFromGround();

    @Override
    public boolean blossom$isJumping()
    {
        return jumping;
    }

    @Override
    public void blossom$jump()
    {
        jumpFromGround();
    }
}
