package com.gnottero.blossom.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.gnottero.blossom.client.BlossomClient;
import com.gnottero.blossom.net.BlossomPayloads;

/**
 * Responds to the server's hello and receives shapes.
 *
 * <p>It's the client that says "I'm here": the server has no other way of knowing, and until it
 * responds it receives particles like any vanilla client. If the protocol doesn't match, it doesn't
 * respond at all, particles are better than a drawing interpreted with the wrong rules.
 *
 * <p>The target is {@code ClientPacketListener} and not its common superclass: there the method is
 * abstract, and there's nothing to inject into in an abstract method.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListener_blossomNetworkMixin
{
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"), cancellable = true)
    private void blossom$onCustomPayload(CustomPacketPayload payload, CallbackInfo ci)
    {
        if (payload instanceof final BlossomPayloads.Hello hello)
        {
            if (hello.version() == BlossomPayloads.PROTOCOL_VERSION)
            {
                // 'send' is public but declared in the superclass: use a cast instead of a
                // @Shadow, which doesn't work on an inherited member
                ((ClientCommonPacketListenerImpl) (Object) this).send(new ServerboundCustomPayloadPacket(
                        BlossomPayloads.Hello.of(BlossomPayloads.PROTOCOL_VERSION)));
            }
            // the payload is handled: without stopping here, vanilla logs it as unknown on every
            // connection, which is a false warning
            ci.cancel();
            return;
        }
        if (payload instanceof final BlossomPayloads.Shapes shapes)
        {
            BlossomClient.shapes().accept(shapes.data());
            ci.cancel();
        }
    }
}
