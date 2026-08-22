package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import com.gnottero.blossom.client.BlossomClient;

/**
 * Advances the shapes' clock on the client, and discards them when the world changes.
 *
 * <p>Time is tracked here rather than on the server: what arrives over the network is a duration,
 * not an instant, so this is the only clock that matters. Leaving a world clears everything, shapes
 * belong to the session in which they were drawn, and keeping them would mean seeing them reappear
 * elsewhere.
 */
@Mixin(Minecraft.class)
public abstract class Minecraft_blossomClientTickMixin
{
    @Inject(method = "tick", at = @At("RETURN"))
    private void blossom$onClientTick(CallbackInfo ci)
    {
        BlossomClient.shapes().onTick();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void blossom$onLevelChange(ClientLevel level, CallbackInfo ci)
    {
        BlossomClient.shapes().clear();
    }
}
