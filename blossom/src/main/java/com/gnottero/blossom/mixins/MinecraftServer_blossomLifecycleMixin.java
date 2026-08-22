package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

import com.gnottero.blossom.BlossomMod;

/**
 * Hooks the runtime lifecycle to the server one.
 *
 * <p>In singleplayer the server is born and dies with every world opened, so the loaded scripts have
 * to be dropped on shutdown: otherwise the next world would find the previous world's apps at home.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_blossomLifecycleMixin
{
    @Inject(method = "loadLevel", at = @At("RETURN"))
    private void blossom$onServerLoaded(CallbackInfo ci)
    {
        BlossomMod.onServerLoaded((MinecraftServer) (Object) this);
        com.gnottero.blossom.event.Event.SERVER_STARTS.fireOnServer((MinecraftServer) (Object) this, java.util.List::of);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void blossom$onServerStopped(CallbackInfo ci)
    {
        com.gnottero.blossom.event.Event.SERVER_SHUTS_DOWN.fireOnServer((MinecraftServer) (Object) this, java.util.List::of);
        BlossomMod.onServerClosed();
    }
}
