package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.gnottero.blossom.net.BlossomPayloads;
import com.gnottero.blossom.net.ServerNetwork;

/**
 * Receives the answer to the greeting of a Blossom client.
 *
 * <p>It is the only payload the server accepts: everything scripts can do goes through commands and
 * events, and a channel a client could ask things on would be one more surface to defend in exchange
 * for nothing.
 *
 * <p>The target is {@code ServerGamePacketListenerImpl} and not the common superclass:
 * {@code handleCustomPayload} is <b>overridden</b> here, and the overriding body does not call the
 * parent one. A hook on the superclass compiles, applies without complaining, and never runs during
 * the play phase, which is exactly how the greeting used to get lost.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImpl_blossomNetworkMixin
{
    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void blossom$onCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci)
    {
        if (packet.payload() instanceof final BlossomPayloads.Hello hello)
        {
            // the greeting arrives on the network thread: just record it, without touching the world
            ServerNetwork.onHello(((ServerGamePacketListenerImpl) (Object) this).player, hello.data());
        }
    }
}
