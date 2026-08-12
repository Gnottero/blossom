package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

import com.gnottero.blossom.client.AfterCloudsFrame;

/**
 * Disegna il riempimento traslucido delle forme subito dopo le nuvole.
 *
 * <p>Il punto è preciso: dopo le nuvole - così il riempimento le mescola nella proporzione giusta,
 * che è il motivo di tutta questa fase (vedi {@code SubmitNodeCollection_blossomAfterCloudsMixin}) -
 * e prima della pioggia, che sta fra la camera e la forma molto più spesso di quanto le stia dietro.
 * L'aggancio è sulla chiamata al tempo atmosferico e non sulle nuvole, perché quella è dentro un
 * {@code if}: con le nuvole spente non verrebbe eseguito niente.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderer_blossomAfterCloudsMixin
{
    @Inject(method = "executeClassicTransparency", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void blossom$drawAfterClouds(ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass, CallbackInfo ci)
    {
        ((AfterCloudsFrame) featureFrame).blossom$executeAfterClouds(renderPass);
    }
}
