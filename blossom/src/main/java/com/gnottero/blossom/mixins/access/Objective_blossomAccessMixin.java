package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Cambiare criterio a un obiettivo gia' creato. */
@Mixin(Objective.class)
public interface Objective_blossomAccessMixin
{
    @Mutable
    @Accessor("criteria")
    void blossom$setCriterion(ObjectiveCriteria criterion);
}
