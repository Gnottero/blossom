package com.gnottero.blossom.mixins.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/** I posti liberi di un punto di interesse, per {@code world.poi} e {@code world.set_poi}. */
@Mixin(PoiRecord.class)
public interface PoiRecord_blossomAccessMixin
{
    @Accessor("freeTickets")
    int blossom$freeTickets();

    @Invoker("acquireTicket")
    boolean blossom$acquireTicket();
}
