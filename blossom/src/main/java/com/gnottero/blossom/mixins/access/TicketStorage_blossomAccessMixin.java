package com.gnottero.blossom.mixins.access;

import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;

import com.gnottero.blossom.access.Access;

/** I ticket di caricamento chunk, per {@code world.chunk_tickets}. */
@Mixin(TicketStorage.class)
public class TicketStorage_blossomAccessMixin implements Access.Tickets
{
    @Shadow
    @Final
    private Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Override
    public Long2ObjectOpenHashMap<List<Ticket>> blossom$ticketsByPosition()
    {
        return tickets;
    }
}
