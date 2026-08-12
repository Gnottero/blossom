package com.gnottero.blossom.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Quello che una {@code SubmitNodeCollection} sa fare in più, per noi: una fase di disegno che viene
 * dopo le nuvole.
 *
 * <p>Il gioco raccoglie tutto quello che c'è da disegnare in un frame dentro delle <em>fasi</em>, e
 * le esegue in un ordine fisso. Nessuna delle fasi traslucide di vanilla viene eseguita dopo le
 * nuvole, e quello è esattamente il momento che serve al riempimento traslucido di una forma: vedi
 * {@code SubmitNodeCollection_blossomAfterCloudsMixin}.
 */
@Environment(EnvType.CLIENT)
public interface AfterCloudsSubmits
{
    /** Mette della geometria nella fase che disegna dopo le nuvole. */
    void blossom$submitAfterClouds(PoseStack.Pose pose, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer);

    /** La fase, per chi deve eseguirla. */
    FeatureRenderPhase<?> blossom$afterCloudsPhase();
}
