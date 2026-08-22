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
 * Draws the translucent fill of shapes right after the clouds.
 *
 * <p>The timing is precise: after the clouds, so the fill blends with them in the right proportion
 * (which is the whole reason for this phase, see {@code SubmitNodeCollection_blossomAfterCloudsMixin}),
 * and before the rain, which sits between the camera and the shape far more often than behind it.
 * The hook is on the weather rendering call rather than on the clouds, because that call is inside an
 * {@code if}: with clouds disabled, nothing would run.
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
