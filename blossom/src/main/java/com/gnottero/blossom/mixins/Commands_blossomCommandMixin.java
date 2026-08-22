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
 * Registers /blossom in the command tree.
 *
 * <p>Via mixin rather than via Fabric API: the mod has no other reason to depend on fabric-api, and
 * this is the only hook it needs.
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
