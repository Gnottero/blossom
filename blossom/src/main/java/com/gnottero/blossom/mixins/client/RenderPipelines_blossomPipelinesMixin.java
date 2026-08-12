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
 * Mette la nostra pipeline nell'elenco di quelle da compilare all'avvio.
 *
 * <p>Il gioco compila gli shader di questo elenco quando carica le risorse. Una pipeline che non c'è
 * verrebbe compilata al primo disegno, cioè nel mezzo di un frame: si vedrebbe uno scatto la prima
 * volta che uno script disegna una forma piena. Costa una compilazione all'avvio, e non si nota.
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
