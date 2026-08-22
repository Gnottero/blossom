package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/** The free slots of a point of interest, for {@code world.poi} and {@code world.set_poi}. */
@Mixin(PoiRecord.class)
public interface PoiRecord_blossomAccessMixin
{
    @Accessor("freeTickets")
    int blossom$freeTickets();

    @Invoker("acquireTicket")
    boolean blossom$acquireTicket();
}
