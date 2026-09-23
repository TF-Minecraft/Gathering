package net.tfminecraft.gathering.spawn;

import org.bukkit.Chunk;
import org.bukkit.Location;

import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.spot.GatheringSpot;

public final class ChunkProbe {

    private ChunkProbe() {}

    public static ProbeOutcome probe(SpotManager manager, Chunk chunk, SpotTypeLoader.SpotTypeDefinition type) {
        ChunkKey key = manager.chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        SpawnPlanner.ProbeResult result = SpawnPlanner.tryFindLocation(chunk, type);
        if (result == null || result.location == null) {
            manager.markChunkExcluded(key, "no_valid_surface");
            return ProbeOutcome.EXCLUDED;
        }

        Location loc = result.location;
        GatheringSpot spot = GatheringSpot.create(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                type.id,
                result.surfaceMaterial);
        manager.register(spot);
        manager.markTypeSpawned(type.id);
        return ProbeOutcome.SPAWNED;
    }

    public enum ProbeOutcome {
        SPAWNED,
        EXCLUDED
    }
}
