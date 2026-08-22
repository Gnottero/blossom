package com.gnottero.blossom.mixins.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.gnottero.blossom.client.AfterCloudsSubmits;

/**
 * Adds a draw phase that runs after the clouds.
 *
 * <p>The game runs all translucent phases before the clouds. An opaque face doesn't suffer from
 * this (it writes depth and the clouds get discarded on their own, see {@code BlossomRenderTypes}),
 * but a translucent one doesn't write depth, so a cloud drawn afterward covers it completely: a 50%
 * sphere would let the whole cloud show through, as if it weren't there.
 *
 * <p>Writing depth for translucent geometry too would just move the error to the other side, since
 * the depth test is binary. The only way to blend in the right proportion is to draw the fill after
 * the cloud, which is what this phase enables.
 *
 * <p>Only applies to classic transparency: with improved transparency (OIT) order doesn't matter and
 * the fill goes in the normal phase.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollection_blossomAfterCloudsMixin implements AfterCloudsSubmits
{
    @Unique
    private final SimpleFeatureRenderPhase blossom$afterClouds = new SimpleFeatureRenderPhase();

    /** The list of phases with our piece at the end, computed once, since it's asked for every frame. */
    @Unique
    private @Nullable List<FeatureRenderPhase<?>> blossom$allPhases = null;

    @Override
    public void blossom$submitAfterClouds(PoseStack.Pose pose, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer)
    {
        this.blossom$afterClouds.submit(new CustomFeatureRenderer.Submit(pose, renderType, renderer));
    }

    @Override
    public FeatureRenderPhase<?> blossom$afterCloudsPhase()
    {
        return this.blossom$afterClouds;
    }

    /**
     * Makes our phase known to the game.
     *
     * <p>This list is the one that gets cleared and reordered at the start of every frame: a phase
     * that isn't in it would never be collected, and whatever gets submitted into it would stay
     * there forever.
     */
    @Inject(method = "allPhases", at = @At("RETURN"), cancellable = true)
    private void blossom$addOwnPhase(CallbackInfoReturnable<List<FeatureRenderPhase<?>>> cir)
    {
        if (this.blossom$allPhases == null)
        {
            List<FeatureRenderPhase<?>> phases = new ArrayList<>(cir.getReturnValue());
            phases.add(this.blossom$afterClouds);
            this.blossom$allPhases = List.copyOf(phases);
        }
        cir.setReturnValue(this.blossom$allPhases);
    }
}
