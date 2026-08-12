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
 * <p>Le primitive di disegno del gioco - i <em>gizmo</em> - hanno una sola pipeline per le facce:
 * {@code DEBUG_FILLED_BOX}, che mescola sempre in trasparenza e <b>non scrive la profondità</b>. Va
 * benissimo per una hitbox, ma vuol dire che una faccia con alfa {@code ff} non nasconde niente: le
 * nuvole, che vengono disegnate dopo e col loro test di profondità, ci passano sopra. Non è un
 * difetto del colore, è la pipeline: nessun valore di alfa può cambiarlo.
 *
 * <p>Quindi per il riempimento opaco ce ne facciamo una: la stessa cosa, ma senza mescolare e
 * scrivendo la profondità. Il riempimento traslucido continua a passare dai gizmo, dove la
 * trasparenza è quello che si vuole davvero.
 *
 * <p>Il resto dei parametri è copiato da {@code DEBUG_FILLED_BOX} e non a caso: il confronto di
 * profondità è {@code GREATER_THAN_OR_EQUAL} perché il gioco usa la profondità rovesciata, il culling
 * è spento perché una forma si guarda anche da dentro, e lo scostamento di layering è lo stesso delle
 * linee, così reticolo e riempimento della stessa forma non litigano sullo stesso piano.
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
