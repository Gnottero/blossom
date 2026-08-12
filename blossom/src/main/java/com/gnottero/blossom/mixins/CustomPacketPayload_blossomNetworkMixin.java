package com.gnottero.blossom.mixins;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.gnottero.blossom.net.BlossomPayloads;

/**
 * Registra i due payload di Blossom fra quelli che il gioco sa leggere e scrivere.
 *
 * <p>Senza Fabric API - che il mod non richiede, e non vale la pena richiedere per due pacchetti -
 * l'elenco dei payload conosciuti è costruito una volta da vanilla e non ha un punto di estensione.
 * Qui viene esteso al volo, prima che il codec venga costruito.
 *
 * <p>La lista in arrivo viene sostituita con una marcata: il metodo che si intercetta viene poi
 * richiamato con la lista estesa, e senza un modo di riconoscerla si rientrerebbe qui per sempre.
 */
@Mixin(CustomPacketPayload.class)
public interface CustomPacketPayload_blossomNetworkMixin
{
    /** una {@link ArrayList} qualunque, ma riconoscibile: è l'unico scopo di questa classe */
    final class BlossomExtendedList<E> extends ArrayList<E>
    {
        BlossomExtendedList(List<? extends E> source)
        {
            super(source);
        }
    }

    @Inject(method = "codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            at = @At("HEAD"), cancellable = true)
    private static <B extends FriendlyByteBuf> void blossom$registerPayloads(
            CustomPacketPayload.FallbackProvider<B> fallbackProvider,
            List<CustomPacketPayload.TypeAndCodec<? super B, ?>> known,
            CallbackInfoReturnable<StreamCodec<B, CustomPacketPayload>> cir)
    {
        if (known instanceof BlossomExtendedList)
        {
            return;
        }
        List<CustomPacketPayload.TypeAndCodec<? super B, ?>> extended = new BlossomExtendedList<>(known);
        extended.add(new CustomPacketPayload.TypeAndCodec<>(
                BlossomPayloads.Hello.TYPE, BlossomPayloads.Hello.STREAM_CODEC));
        extended.add(new CustomPacketPayload.TypeAndCodec<>(
                BlossomPayloads.Shapes.TYPE, BlossomPayloads.Shapes.STREAM_CODEC));
        cir.setReturnValue(CustomPacketPayload.codec(fallbackProvider, extended));
    }
}
