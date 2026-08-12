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

/** L'intelligenza di un mob: obiettivi vanilla, obiettivi aggiunti da uno script, e persistenza. */
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

    /** gli obiettivi che uno script ha aggiunto, per poterli togliere per nome */
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
