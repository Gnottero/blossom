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
 * Genera {@code chunk_generated} e {@code chunk_loaded}.
 *
 * <p>L'aggancio usato dall'upstream - un metodo intermedio di {@code ChunkMap} - non esiste piu' dopo
 * la riscrittura della pipeline dei chunk. Il punto giusto su questa versione e'
 * {@code ChunkStatusTasks.full}, l'ultimo passo con cui un chunk diventa un chunk vero: ci passano
 * sia quelli appena generati sia quelli riletti dal disco, ed e' l'ultimo posto in cui i due casi
 * sono distinguibili - un chunk gia' completo arriva avvolto in un {@link ImposterProtoChunk}.
 *
 * <p>Il metodo gira su un thread di generazione, quindi gli eventi vengono rimandati al thread del
 * server: un handler che legge il mondo da un thread di worldgen e' il tipo di corsa che si
 * manifesta un mese dopo su un server pieno.
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
