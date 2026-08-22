package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.TaskChainer;

import com.gnottero.blossom.access.Access;

/**
 * Allows building a "silent" command source: {@code run()} executes a command and collects its
 * output instead of printing it to chat.
 */
@Mixin(CommandSourceStack.class)
public class CommandSourceStack_blossomAccessMixin implements Access.Source
{
    @Mutable
    @Shadow
    private boolean silent;

    @Mutable
    @Shadow
    private CommandResultCallback resultCallback;

    @Mutable
    @Shadow
    private EntityAnchorArgument.Anchor anchor;

    @Mutable
    @Shadow
    private CommandSigningContext signingContext;

    @Mutable
    @Shadow
    private TaskChainer chatMessageChainer;

    @Override
    public void blossom$setupPrivates(boolean silent, CommandResultCallback callback,
            EntityAnchorArgument.Anchor anchor, CommandSigningContext signingContext, TaskChainer chainer)
    {
        this.silent = silent;
        this.resultCallback = callback;
        this.anchor = anchor;
        this.signingContext = signingContext;
        this.chatMessageChainer = chainer;
    }
}
