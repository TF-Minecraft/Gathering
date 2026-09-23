package net.tfminecraft.gathering.spawn;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.ConfigLoader;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.manager.SpotManager;

public final class SpawnScheduler {

    private final SpotManager spotManager;

    public SpawnScheduler(SpotManager spotManager) {
        this.spotManager = spotManager;
    }

    public void start() {
        long intervalTicks = Cache.spawnIntervalMinutes * 60L * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSpawn();
            }
        }.runTaskTimer(Gathering.plugin, intervalTicks, intervalTicks);
    }

    private void tickSpawn() {
        for (ConfigLoader.WorldBounds bounds : Cache.worldBounds.values()) {
            World world = spotManager.getConfiguredWorld(bounds.world);
            if (world == null) continue;

            int attempts = 0;
            while (attempts < Cache.spawnAttemptsPerTick) {
                attempts++;
                SpotTypeLoader.SpotTypeDefinition type = spotManager.pickSpawnableType();
                if (type == null) break;

                int chunkX = randomChunkCoord(bounds.chunkXMin, bounds.chunkXMax);
                int chunkZ = randomChunkCoord(bounds.chunkZMin, bounds.chunkZMax);
                ChunkKey key = spotManager.chunkKey(bounds.world, chunkX, chunkZ);

                if (spotManager.hasActiveSpotInChunk(key)) continue;
                if (spotManager.isChunkOnCooldown(key)) continue;
                if (spotManager.isChunkExcluded(key)) continue;

                final SpotTypeLoader.SpotTypeDefinition chosenType = type;
                ChunkLoadHelper.withLoadedChunk(world, chunkX, chunkZ, chunk -> {
                    ChunkProbe.probe(spotManager, chunk, chosenType);
                    return null;
                });
            }
        }
    }

    private int randomChunkCoord(int min, int max) {
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
