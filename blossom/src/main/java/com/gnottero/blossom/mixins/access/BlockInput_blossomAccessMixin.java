package com.gnottero.blossom.mixins.access;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.nbt.CompoundTag;

import com.gnottero.blossom.access.Access;

/** Il tag NBT scritto in un argomento blocco ({@code stone{...}}). */
@Mixin(BlockInput.class)
public class BlockInput_blossomAccessMixin implements Access.BlockInputTag
{
    @Shadow
    @Final
    private @Nullable CompoundTag tag;

    @Override
    public CompoundTag blossom$tag()
    {
        return tag;
    }
}
