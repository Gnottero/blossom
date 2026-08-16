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
 * Aggiunge una fase di disegno che viene dopo le nuvole.
 *
 * <p>Il gioco esegue tutte le fasi traslucide prima delle nuvole. Una faccia opaca non ne soffre -
 * scrive la profondita' e le nuvole si scartano da sole (vedi {@code BlossomRenderTypes}) - ma una
 * traslucida non scrive la profondita', quindi la nuvola disegnata dopo la copre del tutto: una
 * sfera al 50% lascia vedere la nuvola intera, come se non ci fosse.
 *
 * <p>Scrivere la profondita' anche per il traslucido sposterebbe solo l'errore dall'altra parte, il
 * test di profondita' essendo binario. L'unico modo di mescolare nella proporzione giusta e'
 * disegnare il riempimento dopo la nuvola, che e' quello che questa fase permette.
 *
 * <p>Vale solo per la trasparenza classica: con la trasparenza migliorata (OIT) l'ordine non conta e
 * il riempimento va nella fase normale.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollection_blossomAfterCloudsMixin implements AfterCloudsSubmits
{
    @Unique
    private final SimpleFeatureRenderPhase blossom$afterClouds = new SimpleFeatureRenderPhase();

    /** L'elenco delle fasi col nostro pezzo in fondo, calcolato una volta sola: si chiede a ogni frame. */
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
     * Fa conoscere la nostra fase al gioco.
     *
     * <p>Questo elenco è quello che viene svuotato e ordinato all'inizio di ogni frame: una fase che
     * non c'è non verrebbe mai raccolta, e quello che ci si mette dentro resterebbe lì per sempre.
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
