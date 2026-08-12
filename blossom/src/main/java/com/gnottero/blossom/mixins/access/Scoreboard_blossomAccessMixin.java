package com.gnottero.blossom.mixins.access;

import java.util.List;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Gli obiettivi raggruppati per criterio, per aggiornarli tutti insieme. */
@Mixin(Scoreboard.class)
public interface Scoreboard_blossomAccessMixin
{
    @Accessor("objectivesByCriteria")
    Reference2ObjectMap<ObjectiveCriteria, List<Objective>> blossom$objectivesByCriterion();
}
