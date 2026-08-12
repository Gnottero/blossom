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
 * <p>Il gioco esegue le fasi traslucide - tutte - prima di disegnare le nuvole, poi il tempo
 * atmosferico, poi il bordo del mondo. Per una faccia opaca non è un problema, perché scrive la
 * profondità e le nuvole vengono scartate dal loro stesso test (vedi {@code BlossomRenderTypes}). Per
 * una faccia traslucida invece sì: non scrive la profondità, quindi la nuvola disegnata dopo la copre
 * <b>del tutto</b>, quale che sia l'alfa del riempimento. Una sfera al 50% non lascia vedere metà
 * nuvola: la lascia vedere intera, come se la sfera non ci fosse.
 *
 * <p>Scrivere la profondità anche per il traslucido sposterebbe l'errore dall'altra parte - nuvola
 * nascosta del tutto invece che visibile del tutto - perché un test di profondità è una decisione
 * binaria e la trasparenza non lo è. L'unico modo di mescolare nella proporzione giusta è disegnare
 * il riempimento <b>dopo</b> la nuvola, che è quello che questa fase permette di fare.
 *
 * <p>Vale solo per la trasparenza classica. Con la trasparenza migliorata accesa (OIT) l'ordine non
 * conta per costruzione, le nuvole ci passano dentro, e il riempimento va nella fase normale.
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
