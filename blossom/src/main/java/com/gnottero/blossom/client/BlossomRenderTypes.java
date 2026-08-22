package com.gnottero.blossom.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.gnottero.blossom.mixins.client.RenderType_blossomCreateInvoker;

/**
 * How to draw a fill that actually covers what is behind it.
 *
 * <p>The game's debug primitives have a single pipeline for faces, {@code DEBUG_FILLED_BOX}, which
 * always blends with transparency and does not write depth: a face with alpha {@code ff} hides
 * nothing, and the clouds pass right over it. It is not the color, it is the pipeline. For an
 * opaque fill we therefore need one that is the same but does not blend and does write depth;
 * the translucent fill still goes through the gizmos.
 *
 * <p>The rest of the parameters are copied from {@code DEBUG_FILLED_BOX} on purpose: depth
 * comparison {@code GREATER_THAN_OR_EQUAL} because the game uses it reversed, culling off because
 * a shape is also viewed from inside, and the same layering offset as the lines, so the wireframe
 * and the fill of the same shape do not fight over the same plane.
 */
@Environment(EnvType.CLIENT)
public final class BlossomRenderTypes
{
    /** The pipeline: like the one for debug faces, but opaque and writing depth. */
    public static final RenderPipeline OPAQUE_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation("pipeline/blossom_opaque_fill")
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private static final RenderType OPAQUE_FILL = RenderType_blossomCreateInvoker.blossom$create(
            "blossom_opaque_fill",
            RenderSetup.builder(OPAQUE_FILL_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    private BlossomRenderTypes()
    {
    }

    public static RenderType opaqueFill()
    {
        return OPAQUE_FILL;
    }
}
