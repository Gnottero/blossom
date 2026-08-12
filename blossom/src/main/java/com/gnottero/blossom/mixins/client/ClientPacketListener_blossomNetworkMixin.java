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
 * Risponde al saluto del server e riceve le forme.
 *
 * <p>È il client a dire "ci sono": il server non ha nessun altro modo di saperlo, e finché non
 * risponde riceve le particelle come qualunque client vanilla. Se il protocollo non combacia non si
 * risponde affatto — meglio le particelle che un disegno interpretato con le regole sbagliate.
 *
 * <p>Il bersaglio è {@code ClientPacketListener} e non la sua superclasse comune: lì il metodo è
 * astratto, e in un metodo astratto non c'è niente in cui iniettare.
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
                // 'send' e' pubblico ma dichiarato nella superclasse: si passa dal cast invece
                // che da uno @Shadow, che su un membro ereditato non si puo' fare
                ((ClientCommonPacketListenerImpl) (Object) this).send(new ServerboundCustomPayloadPacket(
                        BlossomPayloads.Hello.of(BlossomPayloads.PROTOCOL_VERSION)));
            }
            // il payload e' gestito: senza fermare qui, vanilla lo dichiara sconosciuto nel log a
            // ogni connessione, che e' un avviso falso
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
