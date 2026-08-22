package com.gnottero.blossom.mixins.access;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.phys.Vec3;

import com.gnottero.blossom.access.Access;
import com.gnottero.blossom.event.EntityEvents;

/**
 * The state the game keeps private inside an entity, plus the handlers a script has attached to it
 * with {@code entity.event(...)}.
 *
 * <p>The handlers live here rather than in a dedicated external map: they follow the entity and
 * disappear with it, so nobody has to remember to clean up.
 */
@Mixin(Entity.class)
public abstract class Entity_blossomAccessMixin implements Access.EntityState
{
    @Shadow
    private int portalCooldown;

    @Shadow
    public abstract boolean isRemoved();

    @Shadow
    private Vec3 position;

    @Shadow
    private Vec3 deltaMovement;

    @Shadow
    public @Nullable PortalProcessor portalProcess;

    @Unique
    private boolean blossom$permanentVehicle;

    @Unique
    private final EntityEvents blossom$events = new EntityEvents((Entity) (Object) this);

    @Unique
    private Vec3 blossom$previousPosition;

    @Unique
    private Vec3 blossom$previousMotion;

    @Override
    public EntityEvents blossom$events()
    {
        return blossom$events;
    }

    @Override
    public boolean blossom$permanentVehicle()
    {
        return blossom$permanentVehicle;
    }

    @Override
    public void blossom$setPermanentVehicle(boolean permanent)
    {
        blossom$permanentVehicle = permanent;
    }

    @Override
    public int blossom$portalTimer()
    {
        return portalProcess == null ? 0 : portalProcess.getPortalTime();
    }

    @Override
    public void blossom$setPortalTimer(int amount)
    {
        if (portalProcess != null)
        {
            ((Access.Portal) portalProcess).blossom$setPortalTime(amount);
        }
    }

    @Override
    public int blossom$portalCooldown()
    {
        return portalCooldown;
    }

    @Override
    public void blossom$setPortalCooldown(int amount)
    {
        portalCooldown = amount;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void blossom$onTick(CallbackInfo ci)
    {
        blossom$events.fire(EntityEvents.Kind.ON_TICK);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void blossom$onRemove(CallbackInfo ci)
    {
        if (!isRemoved())
        {
            blossom$events.fire(EntityEvents.Kind.ON_REMOVED);
        }
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"))
    private void blossom$beforeMove(CallbackInfo ci)
    {
        blossom$previousPosition = this.position;
        blossom$previousMotion = this.deltaMovement;
    }

    @Inject(method = "setPosRaw", at = @At("TAIL"))
    private void blossom$afterMove(CallbackInfo ci)
    {
        if (blossom$previousPosition != this.position)
        {
            blossom$events.fire(EntityEvents.Kind.ON_MOVE, blossom$previousMotion, blossom$previousPosition,
                    this.position);
        }
    }
}
