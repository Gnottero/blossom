package com.gnottero.blossom.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The extra thing a {@code SubmitNodeCollection} can do for us: a draw phase that runs after
 * the clouds.
 *
 * <p>The game collects everything to draw in a frame into <em>phases</em>, and runs them in a
 * fixed order. None of vanilla's translucent phases run after the clouds, and that is exactly
 * the moment a shape's translucent fill needs: see
 * {@code SubmitNodeCollection_blossomAfterCloudsMixin}.
 */
@Environment(EnvType.CLIENT)
public interface AfterCloudsSubmits
{
    /** Puts geometry into the phase that draws after the clouds. */
    void blossom$submitAfterClouds(PoseStack.Pose pose, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer);

    /** The phase, for whoever needs to run it. */
    FeatureRenderPhase<?> blossom$afterCloudsPhase();
}
