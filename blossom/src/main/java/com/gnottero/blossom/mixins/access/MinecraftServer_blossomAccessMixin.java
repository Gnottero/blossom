package com.gnottero.blossom.mixins.access;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.util.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.storage.LevelStorageSource;

import com.gnottero.blossom.access.Access;

/** Fa avanzare il server di un tick su richiesta, e dice dov'e' il salvataggio del mondo. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_blossomAccessMixin extends ReentrantBlockableEventLoop<TickTask>
        implements Access.Server
{
    @Shadow
    protected abstract void tickServer(BooleanSupplier until);

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Shadow
    private long nextTickTimeNanos;

    @Shadow
    private long lastOverloadWarningNanos;

    private MinecraftServer_blossomAccessMixin(String name, boolean propagatesCrashes)
    {
        super(name, propagatesCrashes);
    }

    @Override
    public void blossom$forceTick(BooleanSupplier until)
    {
        // il tick va forzato senza che il server creda di essere in ritardo, altrimenti salta
        // meta' del lavoro per recuperare
        nextTickTimeNanos = lastOverloadWarningNanos = Util.getNanos();
        tickServer(until);
        pollTask();
        while (pollTask())
        {
            Thread.yield();
        }
    }

    @Override
    public LevelStorageSource.LevelStorageAccess blossom$storage()
    {
        return storageSource;
    }
}
