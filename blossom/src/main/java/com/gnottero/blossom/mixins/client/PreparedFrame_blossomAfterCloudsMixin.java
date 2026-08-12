package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.renderpearl.api.commands.RenderPass;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;

import com.gnottero.blossom.client.AfterCloudsFrame;
import com.gnottero.blossom.client.AfterCloudsSubmits;

/**
 * Esegue la fase di {@link AfterCloudsSubmits} sul frame già preparato.
 *
 * <p>È lo stesso schema di {@code executeTranslucentAfterTerrain} e delle altre: si scorre l'ordine
 * di consegna e si esegue la fase per ognuno. Chi la chiama è
 * {@code LevelRenderer_blossomAfterCloudsMixin}, nel punto giusto del frame.
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class PreparedFrame_blossomAfterCloudsMixin implements AfterCloudsFrame
{
    @Shadow
    private @Nullable FeatureFrameContext context;

    @Shadow
    private @Nullable SubmitNodeStorage submitNodeStorage;

    @Shadow
    private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context, RenderPass renderPass)
    {
        throw new AssertionError();
    }

    @Override
    @Unique
    public void blossom$executeAfterClouds(RenderPass renderPass)
    {
        FeatureFrameContext frameContext = this.context;
        SubmitNodeStorage storage = this.submitNodeStorage;
        if (frameContext == null || storage == null)
        {
            return;
        }
        for (SubmitNodeCollection collection : storage.getSubmitsPerOrder().values())
        {
            this.executePhase(((AfterCloudsSubmits) collection).blossom$afterCloudsPhase(), frameContext,
                    renderPass);
        }
    }
}
