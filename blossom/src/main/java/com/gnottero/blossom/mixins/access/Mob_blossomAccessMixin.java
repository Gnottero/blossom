package com.gnottero.blossom.mixins.access;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import com.gnottero.blossom.access.Access;

/** A mob's AI: vanilla goals, goals added by a script, and persistence. */
@Mixin(Mob.class)
public abstract class Mob_blossomAccessMixin implements Access.MobAi
{
    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Shadow
    private boolean persistenceRequired;

    /** The goals a script has added, kept so they can be removed by name. */
    @Unique
    private final Map<String, Goal> blossom$temporaryTasks = new HashMap<>();

    @Override
    public GoalSelector blossom$goals(boolean target)
    {
        return target ? targetSelector : goalSelector;
    }

    @Override
    public Map<String, Goal> blossom$temporaryTasks()
    {
        return blossom$temporaryTasks;
    }

    @Override
    public void blossom$setPersistence(boolean persistent)
    {
        persistenceRequired = persistent;
    }
}
