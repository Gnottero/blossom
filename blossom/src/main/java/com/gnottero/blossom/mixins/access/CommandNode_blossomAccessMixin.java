package com.gnottero.blossom.mixins.access;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.gnottero.blossom.access.Access;

/**
 * Lets an app's command leave the tree when the app is unloaded.
 *
 * <p>Brigadier can add nodes and never remove them: a child lives in three maps at once, and taking
 * it out of all three is the only way to make {@code /app} disappear without rebuilding the whole
 * dispatcher.
 */
@Mixin(value = CommandNode.class, remap = false)
public class CommandNode_blossomAccessMixin<S> implements Access.CommandTree
{
    @Shadow
    @Final
    private Map<String, CommandNode<S>> children;

    @Shadow
    @Final
    private Map<String, LiteralCommandNode<S>> literals;

    @Shadow
    @Final
    private Map<String, ArgumentCommandNode<S, ?>> arguments;

    @Override
    public void blossom$removeChild(String name)
    {
        children.remove(name);
        literals.remove(name);
        arguments.remove(name);
    }
}
