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
 * Sends shapes to whoever needs to see them, one way or another.
 *
 * <p>There is a single rule and it lives entirely here: whoever has the Blossom client receives
 * the shape and draws it; whoever doesn't receives particles arranged along the same geometry.
 * Neither path is a fallback for the other, the first is precise, the second works everywhere,
 * and the script author doesn't need to know which one any given viewer got.
 *
 * <h2>Why particles need to be repeated</h2>
 *
 * <p>A particle lives for a moment and then disappears, while a shape lasts as long as it was
 * told to. Shapes still alive are therefore redrawn every {@link #PARTICLE_PERIOD_TICKS} ticks,
 * but only for players without the client and only as long as there is at least one: on a server
 * where everyone has the client, this list stays empty and costs nothing.
 */
public final class ShapeDispatcher
{
    /**
     * How often, in ticks, particles get redrawn.
     *
     * <p>Half a second: frequent enough that the drawing doesn't flicker, rare enough not to
     * flood the network with particle packets for something that is just a visual aid.
     */
    private static final int PARTICLE_PERIOD_TICKS = 10;

    /** How many particles per block along a segment. */
    private static final double PARTICLE_DENSITY = 2.0;

    /** A live shape, with who needs to see it as particles. */
    private record Pending(Shape shape, ServerLevel level, List<ServerPlayer> audience)
    {
    }

    private final List<Pending> alive = new CopyOnWriteArrayList<>();
    private long tick = 0;

    /**
     * Draws {@code shape} for {@code audience}.
     *
     * @return how many players received it as a real shape (the rest got it as particles)
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
                // only if it will last long enough to need redrawing: a one-tick shape
                // shouldn't even enter the list
                alive.add(new Pending(shape, level, List.copyOf(withoutClient)));
            }
        }
        return withClient.size();
    }

    /** To be called on every server tick. */
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

    /** Removes all pending shapes: used by shutdown and by {@code clear_shapes}. */
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
     * The particle approximation.
     *
     * <p>The shape's color drives the particle choice: vanilla particles can't be colored at
     * will, so the closest match is picked. This is the honest limit of this approach, and the
     * reason the client exists.
     */
    private static void drawWithParticles(Shape shape, ServerLevel level, List<ServerPlayer> everyone)
    {
        // a particle is shown in the dimension of whoever receives it: only those who are where
        // the shape is now, and the others see it when they get there, at the next redraw
        List<ServerPlayer> audience = everyone.stream().filter(player -> player.level() == level).toList();
        if (audience.isEmpty())
        {
            return;
        }
        // if the shape follows an entity, the point is recomputed on every redraw: that's half
        // the point of 'follow', and the entity has moved in the meantime
        if (!shape.hasLines() && !shape.hasFill())
        {
            return; // a shape invisible on both sides: there is nothing to approximate
        }
        net.minecraft.world.phys.Vec3 followPos = followPositionOf(shape, level);
        if (shape instanceof final Shape.Label label)
        {
            // text isn't made of segments: mark the point, the text was already sent in chat
            net.minecraft.world.phys.Vec3 pos = shape.anchored(label.pos(), followPos);
            Particles.drawParticleLine(audience, particleFor(shape, level), pos, pos, 1.0);
            return;
        }
        if (shape instanceof final Shape.Sprite sprite)
        {
            // a block or an item can't be approximated with lines: mark the point with the
            // particle that at least says which block it is
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

    /** Where the followed entity is now, or {@code null} if it doesn't exist, or no longer does. */
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
     * The particle that marks where a block or item drawn in mid-air is.
     *
     * <p>{@code block_marker} shows the block's texture, so whoever lacks the client at least
     * sees <em>which</em> block it is; for an item, {@code item} is used, showing its sprite. If
     * the name can't be used this way (an item with no sprite, a block removed by an update), it
     * falls back to a generic particle: a glowing point is better than nothing.
     */
    private static ParticleOptions spriteParticle(Shape.Sprite sprite, ServerLevel level)
    {
        // a block's name arrives with its states in brackets: the particle doesn't need them
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
     * The particle that most closely matches the requested color.
     *
     * <p>If the outline is off, the fill is used instead: particles draw the shape's silhouette
     * regardless (it's the only thing they know how to do), so they might as well have the right
     * color.
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
