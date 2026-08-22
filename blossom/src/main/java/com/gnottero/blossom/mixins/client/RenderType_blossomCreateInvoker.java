package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Opens up {@code RenderType.create}, which the game keeps visible only inside its own package.
 *
 * <p>There's no other way to build a way of drawing that isn't already in vanilla's list, and we
 * need one: see {@code BlossomRenderTypes} for why.
 */
@Mixin(RenderType.class)
public interface RenderType_blossomCreateInvoker
{
    @Invoker("create")
    static RenderType blossom$create(String name, RenderSetup setup)
    {
        throw new AssertionError();
    }
}
