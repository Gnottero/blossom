package com.gnottero.blossom.mixins;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;

/**
 * Fires {@code chunk_generated} and {@code chunk_loaded}.
 *
 * <p>The hook used upstream (an intermediate method of {@code ChunkMap}) no longer exists after the
 * chunk pipeline rewrite. On this version the right spot is {@code ChunkStatusTasks.full}, the last
 * step where a chunk becomes an actual chunk: both freshly generated ones and ones reloaded from
 * disk pass through it, and it's the last place where the two cases can be told apart, since an
 * already-complete chunk arrives wrapped in an {@link ImposterProtoChunk}.
 *
 * <p>The method runs on a generation thread, so the events are deferred to the server thread: a
 * handler reading the world from a worldgen thread is the kind of race that shows up a month later
 * on a busy server.
 */
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasks_blossomEventsMixin
{
    @Inject(method = "full", at = @At("HEAD"))
    private static void blossom$onChunkBecomesFull(WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir)
    {
        boolean generated = !(chunk instanceof ImposterProtoChunk);
        if (!Event.CHUNK_LOADED.isListened() && !(generated && Event.CHUNK_GENERATED.isListened()))
        {
            return;
        }
        ServerLevel level = context.level();
        ChunkPos pos = chunk.getPos();
        level.getServer().execute(() -> Fire.chunkBecameFull(level, pos, generated));
    }
}
