package com.gnottero.blossom.mixins;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import com.gnottero.blossom.event.Event;

/** Fires {@code tick}, {@code tick_nether} and {@code tick_ender}, once per server tick. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_blossomTickMixin
{
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void blossom$onTick(BooleanSupplier hasTimeLeft, CallbackInfo ci)
    {
        MinecraftServer server = (MinecraftServer) (Object) this;
        com.gnottero.blossom.ScriptRuntime runtime = com.gnottero.blossom.BlossomMod.runtime();
        if (runtime != null)
        {
            runtime.scheduler.tick();
        }
        com.gnottero.blossom.BlossomMod.shapes().onTick();
        com.gnottero.blossom.BlossomMod.hud().onTick(server);
        // the check before the call is what makes an event nobody listens to free
        if (Event.TICK.isListened())
        {
            Event.TICK.fireOnServer(server, List::of);
        }
        if (Event.TICK_NETHER.isListened() && server.getLevel(Level.NETHER) != null)
        {
            Event.TICK_NETHER.fireInLevel(server.getLevel(Level.NETHER), List::of);
        }
        if (Event.TICK_ENDER.isListened() && server.getLevel(Level.END) != null)
        {
            Event.TICK_ENDER.fireInLevel(server.getLevel(Level.END), List::of);
        }
    }
}
