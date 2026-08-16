package com.gnottero.blossom.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.gnottero.blossom.BlossomMod;

/**
 * I due canali con cui server e client Blossom si parlano.
 *
 * <p>Il mod e' server-side, ma alcune cose - disegnare linee nello spazio, testo fluttuante - non
 * hanno nessun pacchetto vanilla che le esprima. Il patto e' che <b>il client e' facoltativo</b>:
 * chi ce l'ha vede il disegno vero, chi non ce l'ha vede un'approssimazione di particelle. Nessuna
 * funzione smette di funzionare per chi gioca vanilla.
 *
 * <p>Appena un giocatore entra il server gli manda un {@code hello}: un client vanilla scarta il
 * payload sconosciuto, uno Blossom risponde, e solo chi ha risposto riceve i pacchetti di disegno.
 * Non c'e' altro modo di saperlo - il protocollo non elenca i mod del client.
 */
public final class BlossomPayloads
{
    /** server → client alla connessione, e client → server come risposta */
    public static final Identifier HELLO_CHANNEL =
            Identifier.fromNamespaceAndPath(BlossomMod.MOD_ID, "hello");
    /** server → client: le forme da disegnare */
    public static final Identifier SHAPES_CHANNEL =
            Identifier.fromNamespaceAndPath(BlossomMod.MOD_ID, "shapes");

    /**
     * La versione del protocollo.
     *
     * <p>Viaggia nell'{@code hello} in tutte e due le direzioni: un client di una versione diversa
     * viene trattato come se non ci fosse, e riceve le particelle. È meglio di far finta che si
     * capiscano e disegnare male.
     */
    public static final int PROTOCOL_VERSION = 1;

    public static final String VERSION_KEY = "version";
    public static final String SHAPES_KEY = "shapes";

    private BlossomPayloads()
    {
    }

    /** Il saluto, in tutte e due le direzioni. */
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

    /** Le forme da disegnare, dal server al client. */
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
