package com.gnottero.blossom.mixins.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;

import com.gnottero.blossom.client.BlossomRenderTypes;

/**
 * Adds our pipeline to the list of ones compiled at startup.
 *
 * <p>The game compiles the shaders in this list when it loads resources. A pipeline that isn't there
 * would get compiled on the first draw, that is, in the middle of a frame: you'd see a stutter the
 * first time a script draws a filled shape. It costs one compilation at startup, and it goes unnoticed.
 */
@Mixin(RenderPipelines.class)
public abstract class RenderPipelines_blossomPipelinesMixin
{
    @Inject(method = "getStaticPipelines", at = @At("RETURN"), cancellable = true)
    private static void blossom$addOwnPipelines(CallbackInfoReturnable<List<RenderPipeline>> cir)
    {
        List<RenderPipeline> pipelines = new ArrayList<>(cir.getReturnValue());
        pipelines.add(BlossomRenderTypes.OPAQUE_FILL_PIPELINE);
        cir.setReturnValue(List.copyOf(pipelines));
    }
}
