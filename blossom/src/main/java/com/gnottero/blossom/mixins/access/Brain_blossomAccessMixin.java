package com.gnottero.blossom.mixins.access;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;

import com.gnottero.blossom.access.Access;

/** La memoria di un mob, per {@code entity.query(e, 'brain', ...)}. */
@Mixin(Brain.class)
public class Brain_blossomAccessMixin implements Access.BrainMemories
{
    @Shadow
    @Final
    private Map<MemoryModuleType<?>, MemorySlot<?>> memories;

    @Override
    public Map<MemoryModuleType<?>, MemorySlot<?>> blossom$memories()
    {
        return memories;
    }
}
