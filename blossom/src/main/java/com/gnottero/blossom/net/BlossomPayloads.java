package com.gnottero.blossom.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.gnottero.blossom.BlossomMod;

/**
 * The two channels the Blossom server and client talk over.
 *
 * <p>The mod is server-side, but some things (drawing lines in space, floating text) have no
 * vanilla packet that can express them. The contract is that <b>the client is optional</b>:
 * whoever has it sees the real drawing, whoever does not sees a particle approximation. No
 * feature stops working for a vanilla player.
 *
 * <p>As soon as a player joins, the server sends them a {@code hello}: a vanilla client discards
 * the unknown payload, a Blossom client replies, and only whoever replied receives the drawing
 * packets. There is no other way to know: the protocol does not list the client's mods.
 */
public final class BlossomPayloads
{
    /** server → client on connect, and client → server as a reply */
    public static final Identifier HELLO_CHANNEL =
            Identifier.fromNamespaceAndPath(BlossomMod.MOD_ID, "hello");
    /** server → client: the shapes to draw */
    public static final Identifier SHAPES_CHANNEL =
            Identifier.fromNamespaceAndPath(BlossomMod.MOD_ID, "shapes");

    /**
     * The protocol version.
     *
     * <p>Travels in the {@code hello} in both directions: a client on a different version is
     * treated as if it were not there, and gets the particles. That is better than pretending
     * they understand each other and drawing badly.
     */
    public static final int PROTOCOL_VERSION = 1;

    public static final String VERSION_KEY = "version";
    public static final String SHAPES_KEY = "shapes";

    private BlossomPayloads()
    {
    }

    /** The greeting, in both directions. */
    public record Hello(CompoundTag data) implements CustomPacketPayload
    {
        public static final Type<Hello> TYPE = new Type<>(HELLO_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, Hello> STREAM_CODEC =
                CustomPacketPayload.codec(Hello::write, Hello::new);

        public Hello(FriendlyByteBuf input)
        {
            this(input.readNbt());
        }

        public void write(FriendlyByteBuf output)
        {
            output.writeNbt(data);
        }

        @Override
        public Type<Hello> type()
        {
            return TYPE;
        }

        public static Hello of(int version)
        {
            CompoundTag tag = new CompoundTag();
            tag.putInt(VERSION_KEY, version);
            return new Hello(tag);
        }

        public int version()
        {
            return data.getIntOr(VERSION_KEY, 0);
        }
    }

    /** The shapes to draw, from the server to the client. */
    public record Shapes(CompoundTag data) implements CustomPacketPayload
    {
        public static final Type<Shapes> TYPE = new Type<>(SHAPES_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, Shapes> STREAM_CODEC =
                CustomPacketPayload.codec(Shapes::write, Shapes::new);

        public Shapes(FriendlyByteBuf input)
        {
            this(input.readNbt());
        }

        public void write(FriendlyByteBuf output)
        {
            output.writeNbt(data);
        }

        @Override
        public Type<Shapes> type()
        {
            return TYPE;
        }
    }
}
