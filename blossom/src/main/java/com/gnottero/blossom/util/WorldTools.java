package com.gnottero.blossom.util;

//import carpet.fakes.MinecraftServerInterface;
import com.gnottero.blossom.access.Vanilla;
//import net.fabricmc.api.EnvType;
//import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class WorldTools
{

    public static boolean canHasChunk(ServerLevel world, ChunkPos chpos, @Nullable Map<String, RegionFile> regionCache, boolean deepcheck)
    {
        if (world.getChunk(chpos.x(), chpos.z(), ChunkStatus.STRUCTURE_STARTS, false) != null)
        {
            return true;
        }
        String currentRegionName = "r." + chpos.getRegionX() + "." + chpos.getRegionZ() + ".mca";
        if (regionCache != null && regionCache.containsKey(currentRegionName))
        {
            RegionFile region = regionCache.get(currentRegionName);
            if (region == null)
            {
                return false;
            }
            return region.hasChunk(chpos);
        }
        Path regionsFolder = Vanilla.MinecraftServer_storageSource(world.getServer()).getDimensionPath(world.dimension()).resolve("region");
        Path regionPath = regionsFolder.resolve(currentRegionName);
        if (!regionPath.toFile().exists())
        {
            if (regionCache != null)
            {
                regionCache.put(currentRegionName, null);
            }
            return false;
        }
        if (!deepcheck)
        {
            return true; // not using cache in this case.
        }
        try
        {
            RegionStorageInfo levelStorageInfo = new RegionStorageInfo(Vanilla.MinecraftServer_storageSource(world.getServer()).getLevelId(), world.dimension(), "chunk");
            RegionFile region = new RegionFile(levelStorageInfo, regionPath, regionsFolder, true);
            if (regionCache != null)
            {
                regionCache.put(currentRegionName, region);
            }
            return region.hasChunk(chpos);
        }
        catch (IOException ignored)
        {
        }
        return true;
    }

    public static void forceChunkUpdate(BlockPos pos, ServerLevel world)
    {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk worldChunk = world.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), false);
        if (worldChunk != null)
        {
            List<ServerPlayer> players = world.getChunkSource().chunkMap.getPlayers(chunkPos, false);
            if (!players.isEmpty())
            {
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(worldChunk, world.getLightEngine(), null, null); // false seems to update neighbours as well.
                players.forEach(p -> p.connection.send(packet));
            }
        }
    }
}
