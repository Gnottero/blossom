package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

import com.gnottero.blossom.BlossomMod;

/**
 * Aggancia il ciclo di vita del runtime a quello del server.
 *
 * <p>In singleplayer il server nasce e muore ad ogni mondo aperto, quindi gli script caricati vanno
 * buttati alla chiusura: altrimenti il mondo successivo si troverebbe in casa le app del precedente.
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
