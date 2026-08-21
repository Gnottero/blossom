package com.gnottero.blossom.mixins;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.validation.DirectoryValidator;

import com.gnottero.blossom.content.BlossomRepositorySource;

/**
 * Mette i pack Blossom fra le sorgenti di contenuto del mondo.
 *
 * <p>Il repository di un mondo nasce in un posto solo, e ci nasce prima che il server esista: da qui
 * passano sia il caricamento delle risorse ricaricabili sia quello dei registri dinamici e del
 * worldgen, che il gioco congela subito dopo. Aggiungere una sorgente qui vuol dire che il contenuto
 * di un pack Blossom arriva in tempo per tutte e due le cose, senza un aggancio diverso per
 * categoria.
 *
 * <p>Il percorso serve e non si puo' chiedere: {@code @ModifyArg} vede solo l'argomento che
 * modifica, e su un varargs non accetta la firma estesa con i parametri del metodo. Lo prende quindi
 * un secondo injector all'ingresso, e lo posa dove il primo lo trova. Un {@link ThreadLocal} e non
 * un campo perche' due mondi possono aprirsi insieme - un server dedicato che ne carica uno mentre
 * un client ne apre un altro nello stesso processo - e il valore vive quanto la chiamata.
 */
@Mixin(ServerPacksSource.class)
public abstract class ServerPacksSource_blossomContentMixin
{
    private static final ThreadLocal<Path> blossom$datapacks = new ThreadLocal<>();

    @Inject(
            method = "createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;",
            at = @At("HEAD"))
    private static void blossom$rememberWorld(Path datapacks, DirectoryValidator validator,
            CallbackInfoReturnable<PackRepository> cir)
    {
        blossom$datapacks.set(datapacks);
    }

    @ModifyArg(
            method = "createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"),
            index = 0)
    private static RepositorySource[] blossom$addPackContent(RepositorySource[] sources)
    {
        Path datapacks = blossom$datapacks.get();
        blossom$datapacks.remove();
        return datapacks == null ? sources : BlossomRepositorySource.appendTo(sources, datapacks);
    }
}
