package com.gnottero.blossom.util;

import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;

/**
 * Drawing with particles.
 *
 * <p>Particles only, separate from the shape dispatcher: they are vanilla and everyone sees them,
 * with no modded client and no extra packets. Real shapes live in {@code core.mc.shape}.
 */
public final class Particles
{
    private static final java.util.Random RANDOM = new java.util.Random();

    private Particles()
    {
    }

    /** @throws ThrowStatement if the name is not a known particle */
    public static ParticleOptions byName(String name, RegistryAccess registries)
    {
        try
        {
            return ParticleParser.getEffect(name, registries);
        }
        catch (IllegalArgumentException e)
        {
            throw new ThrowStatement(name, Throwables.UNKNOWN_PARTICLE);
        }
    }

    private static boolean isStraight(Vec3 from, Vec3 to, double density)
    {
        if ((from.x == to.x && from.y == to.y) || (from.x == to.x && from.z == to.z) || (from.y == to.y && from.z == to.z))
        {
            return from.distanceTo(to) / density > 20;
        }
        return false;
    }

    private static int drawOptimizedParticleLine(List<ServerPlayer> playerList, ParticleOptions particle, Vec3 from, Vec3 to, double density)
    {
        double distance = from.distanceTo(to);
        int particles = (int) (distance / density);
        Vec3 towards = to.subtract(from);
        int parts = 0;
        for (ServerPlayer player : playerList)
        {
            ServerLevel world = player.level();
            world.sendParticles(player, particle, true, true,
                    (towards.x) / 2 + from.x, (towards.y) / 2 + from.y, (towards.z) / 2 + from.z, particles / 3,
                    towards.x / 6, towards.y / 6, towards.z / 6, 0.0);
            world.sendParticles(player, particle, true, true,
                    from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.sendParticles(player, particle, true, true,
                    to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
            parts += particles / 3 + 2;
        }
        int divider = 6;
        while (particles / divider > 1)
        {
            int center = (divider * 2) / 3;
            int dev = 2 * divider;
            for (ServerPlayer player : playerList)
            {
                ServerLevel world = player.level();
                world.sendParticles(player, particle, true, true,
                        (towards.x) / center + from.x, (towards.y) / center + from.y, (towards.z) / center + from.z, particles / divider,
                        towards.x / dev, towards.y / dev, towards.z / dev, 0.0);
                world.sendParticles(player, particle, true, true,
                        (towards.x) * (1.0 - 1.0 / center) + from.x, (towards.y) * (1.0 - 1.0 / center) + from.y, (towards.z) * (1.0 - 1.0 / center) + from.z, particles / divider,
                        towards.x / dev, towards.y / dev, towards.z / dev, 0.0);
            }
            parts += 2 * particles / divider;
            divider = 2 * divider;
        }
        return parts;
    }

    public static int drawParticleLine(List<ServerPlayer> players, ParticleOptions particle, Vec3 from, Vec3 to, double density)
    {
        double distance = from.distanceToSqr(to);
        if (distance == 0)
        {
            return 0;
        }
        int pcount = 0;
        if (distance < 100)
        {
            RandomSource rand = players.get(0).level().getRandom();
            int particles = (int) (distance / density) + 1;
            Vec3 towards = to.subtract(from);
            for (int i = 0; i < particles; i++)
            {
                Vec3 at = from.add(towards.scale(rand.nextDouble()));
                for (ServerPlayer player : players)
                {
                    player.level().sendParticles(player, particle, true, true,
                            at.x, at.y, at.z, 1,
                            0.0, 0.0, 0.0, 0.0);
                    pcount++;
                }
            }
            return pcount;
        }

        if (isStraight(from, to, density))
        {
            return drawOptimizedParticleLine(players, particle, from, to, density);
        }
        Vec3 incvec = to.subtract(from).scale(2 * density / Math.sqrt(distance));

        for (Vec3 delta = new Vec3(0.0, 0.0, 0.0);
             delta.lengthSqr() < distance;
             delta = delta.add(incvec.scale(RANDOM.nextFloat())))
        {
            for (ServerPlayer player : players)
            {
                player.level().sendParticles(player, particle, true, true,
                        delta.x + from.x, delta.y + from.y, delta.z + from.z, 1,
                        0.0, 0.0, 0.0, 0.0);
                pcount++;
            }
        }
        return pcount;
    }

    /** Le dodici spigole di un parallelepipedo, disegnate a particelle. */
    public static int particleMesh(List<ServerPlayer> playerList, ParticleOptions particle, double density,
            Vec3 from, Vec3 to)
    {
        double x1 = from.x;
        double y1 = from.y;
        double z1 = from.z;
        double x2 = to.x;
        double y2 = to.y;
        double z2 = to.z;
        return
                drawParticleLine(playerList, particle, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), density) +
                        drawParticleLine(playerList, particle, new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y2, z1), new Vec3(x2, y1, z1), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y1, z1), new Vec3(x1, y1, z1), density) +

                        drawParticleLine(playerList, particle, new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x1, y2, z2), new Vec3(x2, y2, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y2, z2), new Vec3(x2, y1, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), density) +

                        drawParticleLine(playerList, particle, new Vec3(x1, y1, z1), new Vec3(x1, y1, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x1, y2, z1), new Vec3(x1, y2, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), density) +
                        drawParticleLine(playerList, particle, new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), density);
    }
}
