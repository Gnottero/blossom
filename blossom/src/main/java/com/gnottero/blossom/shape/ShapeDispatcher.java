package com.gnottero.blossom.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.gnottero.blossom.net.BlossomPayloads;
import com.gnottero.blossom.net.ServerNetwork;
import com.gnottero.blossom.util.Particles;

/**
 * Manda le forme a chi deve vederle, in un modo o nell'altro.
 *
 * <p>La regola è una sola e sta tutta qui: chi ha il client Blossom riceve la forma e la disegna;
 * chi non ce l'ha riceve delle particelle disposte lungo la stessa geometria. Nessuna delle due
 * strade è un ripiego dell'altra — la prima è precisa, la seconda funziona ovunque - e chi scrive lo
 * script non deve sapere quale delle due è toccata a chi.
 *
 * <h2>Perché le particelle vanno ripetute</h2>
 *
 * <p>Una particella vive qualche istante e poi sparisce, mentre una forma dura quanto le è stato
 * detto. Le forme ancora vive vengono quindi ridisegnate ogni {@link #PARTICLE_PERIOD_TICKS} tick,
 * ma solo per i giocatori senza client e solo finché ce n'è almeno uno: su un server dove tutti
 * hanno il client questo elenco resta vuoto e non costa niente.
 */
public final class ShapeDispatcher
{
    /**
     * Ogni quanti tick si ridisegnano le particelle.
     *
     * <p>Mezzo secondo: abbastanza fitto perché il disegno non lampeggi, abbastanza raro da non
     * riempire la rete di pacchetti di particelle per una cosa che è un aiuto visivo.
     */
    private static final int PARTICLE_PERIOD_TICKS = 10;

    /** quante particelle per blocco lungo un segmento */
    private static final double PARTICLE_DENSITY = 2.0;

    /** una forma viva, con chi la deve vedere a particelle */
    private record Pending(Shape shape, ServerLevel level, List<ServerPlayer> audience)
    {
    }

    private final List<Pending> alive = new CopyOnWriteArrayList<>();
    private long tick = 0;

    /**
     * Disegna {@code shape} per {@code audience}.
     *
     * @return quanti giocatori l'hanno ricevuta come forma vera (gli altri l'hanno avuta a particelle)
     */
    public int draw(Shape shape, ServerLevel level, List<ServerPlayer> audience)
    {
        List<ServerPlayer> withClient = ServerNetwork.withClient(audience);
        if (!withClient.isEmpty())
        {
            BlossomPayloads.Shapes payload = payloadOf(List.of(shape), tick);
            withClient.forEach(player -> ServerNetwork.send(player, payload));
        }
        List<ServerPlayer> withoutClient = ServerNetwork.withoutClient(audience);
        if (!withoutClient.isEmpty())
        {
            drawWithParticles(shape, level, withoutClient);
            if (shape.isAliveAt(tick + PARTICLE_PERIOD_TICKS))
            {
                // solo se durera' abbastanza da dover essere ridisegnata: una forma di un tick
                // non deve nemmeno entrare nell'elenco
                alive.add(new Pending(shape, level, List.copyOf(withoutClient)));
            }
        }
        return withClient.size();
    }

    /** Da chiamare a ogni tick del server. */
    public void onTick()
    {
        tick++;
        if (alive.isEmpty())
        {
            return;
        }
        alive.removeIf(pending -> !pending.shape().isAliveAt(tick));
        if (tick % PARTICLE_PERIOD_TICKS != 0)
        {
            return;
        }
        for (Pending pending : alive)
        {
            drawWithParticles(pending.shape(), pending.level(), pending.audience());
        }
    }

    /** Toglie tutte le forme in attesa: lo fa lo spegnimento, e {@code clear_shapes}. */
    public void clear()
    {
        alive.clear();
    }

    public long tick()
    {
        return tick;
    }

    private static BlossomPayloads.Shapes payloadOf(List<Shape> shapes, long nowTick)
    {
        ListTag list = new ListTag();
        shapes.forEach(shape -> list.add(shape.toTag(nowTick)));
        CompoundTag tag = new CompoundTag();
        tag.put(BlossomPayloads.SHAPES_KEY, list);
        return new BlossomPayloads.Shapes(tag);
    }

    /**
     * L'approssimazione a particelle.
     *
     * <p>Il colore della forma diventa la scelta della particella: le particelle vanilla non si
     * colorano a piacere, quindi si sceglie quella che somiglia di più. È il limite onesto di questa
     * strada, e la ragione per cui il client esiste.
     */
    private static void drawWithParticles(Shape shape, ServerLevel level, List<ServerPlayer> audience)
    {
        // se la forma segue un'entita', il punto si ricalcola ad ogni ridisegno: e' la meta' del
        // senso di 'follow', e l'entita' nel frattempo si e' mossa
        if (!shape.hasLines() && !shape.hasFill())
        {
            return; // una forma invisibile da entrambe le parti: non c'e' niente da approssimare
        }
        net.minecraft.world.phys.Vec3 followPos = followPositionOf(shape, level);
        if (shape instanceof final Shape.Label label)
        {
            // un testo non e' fatto di segmenti: si segna il punto, il testo l'ha gia' avuto in chat
            net.minecraft.world.phys.Vec3 pos = shape.anchored(label.pos(), followPos);
            Particles.drawParticleLine(audience, particleFor(shape, level), pos, pos, 1.0);
            return;
        }
        if (shape instanceof final Shape.Sprite sprite)
        {
            // un blocco o un item non si approssimano con delle linee: si segna il punto con la
            // particella che almeno dice di che blocco si tratta
            net.minecraft.world.phys.Vec3 pos = shape.anchored(sprite.pos(), followPos);
            Particles.drawParticleLine(audience, spriteParticle(sprite, level), pos, pos, 1.0);
            return;
        }
        ParticleOptions particle = particleFor(shape, level);
        List<Shape.Segment> segments = new ArrayList<>(shape.particleOutline());
        for (Shape.Segment segment : segments)
        {
            Particles.drawParticleLine(audience, particle, shape.anchored(segment.from(), followPos),
                    shape.anchored(segment.to(), followPos), PARTICLE_DENSITY);
        }
    }

    /** Dov'e' adesso l'entita' seguita, o {@code null} se non c'e' - o non c'e' piu'. */
    private static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 followPositionOf(
            Shape shape, ServerLevel level)
    {
        if (shape.follow() < 0)
        {
            return null;
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(shape.follow());
        return entity == null ? null : entity.position();
    }

    /**
     * La particella che segna dov'è un blocco o un item disegnato a mezz'aria.
     *
     * <p>{@code block_marker} mostra la texture del blocco, quindi chi non ha il client vede almeno
     * <em>quale</em> blocco è; per un item si usa {@code item}, che mostra il suo sprite. Se il nome
     * non si lascia usare così — un item senza sprite, un blocco sparito da un aggiornamento — si
     * ripiega su una particella qualunque: meglio un punto luminoso che niente.
     */
    private static ParticleOptions spriteParticle(Shape.Sprite sprite, ServerLevel level)
    {
        // il nome di un blocco arriva con i suoi stati fra parentesi: alla particella non servono
        String name = sprite.name();
        int properties = name.indexOf('[');
        if (properties >= 0)
        {
            name = name.substring(0, properties);
        }
        try
        {
            return Particles.byName((sprite.isItem() ? "item " : "block_marker ") + name,
                    level.registryAccess());
        }
        catch (RuntimeException ignored)
        {
            return Particles.byName("end_rod", level.registryAccess());
        }
    }

    /**
     * La particella che somiglia di più al colore chiesto.
     *
     * <p>Se il reticolo è spento si guarda il riempimento: le particelle disegnano comunque la
     * sagoma - è l'unica cosa che sanno fare - e tanto vale che ne prendano il colore giusto.
     */
    private static ParticleOptions particleFor(Shape shape, ServerLevel level)
    {
        int rgb = (shape.hasLines() ? shape.color() : shape.fillColor()) >>> 8;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        String name;
        if (red > 160 && green < 100 && blue < 100)
        {
            name = "flame";
        }
        else if (green > 160 && red < 140 && blue < 140)
        {
            name = "happy_villager";
        }
        else if (blue > 160 && red < 140 && green < 180)
        {
            name = "soul_fire_flame";
        }
        else if (red > 180 && green > 180 && blue < 120)
        {
            name = "wax_on";
        }
        else
        {
            name = "end_rod";
        }
        return Particles.byName(name, level.registryAccess());
    }
}
