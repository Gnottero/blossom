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
 * Registers Blossom's two payloads among the ones the game knows how to read and write.
 *
 * <p>Without Fabric API (which the mod doesn't require, and isn't worth requiring for two packets),
 * the list of known payloads is built once by vanilla and has no extension point. Here it's extended
 * on the fly, before the codec is built.
 *
 * <p>The incoming list is replaced with a marked one: the intercepted method is then called again
 * with the extended list, and without a way to recognize it, this would re-enter forever.
 */
@Mixin(CustomPacketPayload.class)
public interface CustomPacketPayload_blossomNetworkMixin
{
    /** an ordinary {@link ArrayList}, but recognizable: the only purpose of this class */
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
