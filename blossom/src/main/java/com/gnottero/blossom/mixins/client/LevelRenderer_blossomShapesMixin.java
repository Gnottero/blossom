package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import com.gnottero.blossom.client.BlossomClient;

/**
 * Draws Blossom's shapes together with the rest of the scene.
 *
 * <p>The injection point is not arbitrary. Primitives must be submitted <b>in the same phase</b> in
 * which vanilla submits its own, that is, before the current frame collects them: submitting them
 * later means they get collected by the next frame, and since the camera-relative translation is
 * fixed at submission time, the drawing would trail the camera instead of staying put.
 *
 * <p>Solved by construction, by hooking right after {@code submitFeatures} instead of adding a
 * dedicated render phase (inventory entry BE-7).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderer_blossomShapesMixin
{
    @Shadow
    @Final
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
            shift = At.Shift.AFTER))
    private void blossom$submitShapes(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
            CameraRenderState cameraState, GpuBufferSlice fogBuffer, boolean consistentDepthRequired, CallbackInfo ci)
    {
        if (BlossomClient.shapes().isEmpty())
        {
            return; // the common case: no allocation, no work
        }
        DrawableGizmoPrimitives primitives = new DrawableGizmoPrimitives();
        // the partial tick is the game's, not the real one: a followed entity moves with ticks,
        // and using the other one would make it drift forward while the game is paused
        BlossomClient.shapes().submit(primitives, submitNodeStorage, cameraState, levelRenderState.worldPartialTicks);
        if (!primitives.isEmpty())
        {
            primitives.submit(submitNodeStorage, cameraState, false);
        }
    }
}
