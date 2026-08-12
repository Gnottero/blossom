package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.gnottero.blossom.client.BlossomClient;

/**
 * Disegna le forme di Blossom insieme al resto della scena.
 *
 * <p>Il punto di iniezione non è casuale. Le primitive vanno consegnate <b>nella stessa fase</b> in
 * cui vanilla consegna le proprie, cioè prima che il frame corrente le raccolga: consegnarle dopo
 * significa che vengono raccolte dal frame successivo, e siccome la traslazione relativa alla camera
 * viene fissata al momento della consegna, il disegno insegue la camera invece di stare fermo.
 *
 * <p>È lo stesso problema che il fork aveva risolto per Scarpet (voce BE-7 dell'inventario): qui è
 * risolto per costruzione, agganciandosi subito dopo {@code submitFeatures} invece di aggiungere una
 * fase propria.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderer_blossomShapesMixin
{
    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Inject(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
            shift = At.Shift.AFTER))
    private void blossom$submitShapes(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
            boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
            Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci)
    {
        if (BlossomClient.shapes().isEmpty())
        {
            return; // il caso normale: nessuna allocazione, nessun lavoro
        }
        DrawableGizmoPrimitives primitives = new DrawableGizmoPrimitives();
        // il partial tick e' quello del gioco, non quello reale: un'entita' seguita si muove coi
        // tick, e usare l'altro la farebbe scivolare avanti mentre il gioco e' in pausa
        BlossomClient.shapes().submit(primitives, submitNodeStorage, cameraState,
                deltaTracker.getGameTimeDeltaPartialTick(false));
        if (!primitives.isEmpty())
        {
            primitives.submit(submitNodeStorage, cameraState, false);
        }
    }
}
