package com.gnottero.blossom.mixins;

import com.mojang.brigadier.CommandDispatcher;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import com.gnottero.blossom.command.BlossomCommand;

/**
 * Registra /blossom nell'albero dei comandi.
 *
 * <p>Via mixin e non via Fabric API: il mod non ha altre ragioni per dipendere da fabric-api, e
 * questo e' l'unico aggancio che gli serve.
 */
@Mixin(Commands.class)
public abstract class Commands_blossomCommandMixin
{
    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void blossom$registerCommands(Commands.CommandSelection selection, CommandBuildContext buildContext, CallbackInfo ci)
    {
        BlossomCommand.register(this.dispatcher);
    }
}
