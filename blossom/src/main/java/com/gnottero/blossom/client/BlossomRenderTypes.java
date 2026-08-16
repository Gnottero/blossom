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
 * Il modo di disegnare un riempimento che copre davvero quello che ha dietro.
 *
 * <p>Le primitive di debug del gioco hanno una sola pipeline per le facce, {@code DEBUG_FILLED_BOX},
 * che mescola sempre in trasparenza e non scrive la profondita': una faccia con alfa {@code ff} non
 * nasconde niente, e le nuvole ci passano sopra. Non e' il colore, e' la pipeline. Per il
 * riempimento opaco ce ne serve quindi una uguale ma senza mescolare e che scriva la profondita';
 * quello traslucido continua a passare dai gizmo.
 *
 * <p>Il resto dei parametri e' copiato da {@code DEBUG_FILLED_BOX} apposta: confronto di profondita'
 * {@code GREATER_THAN_OR_EQUAL} perche' il gioco la usa rovesciata, culling spento perche' una forma
 * si guarda anche da dentro, e lo stesso scostamento di layering delle linee, cosi' reticolo e
 * riempimento della stessa forma non litigano sullo stesso piano.
 */
@Environment(EnvType.CLIENT)
public final class BlossomRenderTypes
{
    /** la pipeline: come quella delle facce di debug, ma opaca e che scrive la profondità */
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
