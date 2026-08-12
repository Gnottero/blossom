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
 * Riceve la risposta al saluto di un client Blossom.
 *
 * <p>È l'unico payload che il server accetta: tutto quello che gli script possono fare passa dai
 * comandi e dagli eventi, e un canale su cui un client può chiedere cose sarebbe una superficie in
 * più da difendere in cambio di niente.
 *
 * <p>Il bersaglio è {@code ServerGamePacketListenerImpl} e non la superclasse comune:
 * {@code handleCustomPayload} è <b>ridefinito</b> qui, e il corpo ridefinito non chiama quello del
 * padre. Un aggancio sulla superclasse compila, si applica senza lamentarsi, e non viene mai
 * eseguito in fase di gioco — che è esattamente il modo in cui il saluto si perdeva.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImpl_blossomNetworkMixin
{
    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void blossom$onCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci)
    {
        if (packet.payload() instanceof final BlossomPayloads.Hello hello)
        {
            // il saluto arriva sul thread di rete: si registra e basta, senza toccare il mondo
            ServerNetwork.onHello(((ServerGamePacketListenerImpl) (Object) this).player, hello.data());
        }
    }
}
