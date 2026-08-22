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
 * Puts Blossom packs among the content sources of the world.
 *
 * <p>The repository of a world is born in one single place, and it is born there before the server
 * exists: both the loading of reloadable resources and that of dynamic registries and worldgen,
 * which the game freezes right after, go through here. Adding a source here means the content of a
 * Blossom pack arrives in time for both, without a different hook per category.
 *
 * <p>The path is needed and cannot be asked for: {@code @ModifyArg} only sees the argument it
 * modifies, and on a varargs it does not accept the extended signature with the method parameters.
 * So a second injector picks it up at the entry and leaves it where the first one finds it. A
 * {@link ThreadLocal} and not a field because two worlds can open together, a dedicated server
 * loading one while a client opens another in the same process, and the value lives as long as the
 * call.
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
