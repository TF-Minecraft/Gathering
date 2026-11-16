package net.tfminecraft.gathering.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.database.ChunkCacheDatabase;
import net.tfminecraft.gathering.freshness.ChunkFreshness;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spot.GatheringSpot;

/**
 * Active spots (memory only) and chunk freshness (saved). Spots disappear when they outlive
 * {@link Cache#spotLifetimeMinutes}, when no gatherer has been near them for
 * {@link Cache#spotOrphanGraceSeconds}, or when their chunk unloads.
 */
public final class SpotManager implements Listener {

    static final long SWEEP_INTERVAL_TICKS = 100L;
    static final long SAVE_INTERVAL_TICKS = 1200L;

    private final Map<UUID, GatheringSpot> byId = new HashMap<>();
    private final Map<ChunkKey, UUID> activeByChunk = new HashMap<>();
    private final ChunkFreshness freshness = new ChunkFreshness();
    private final List<BukkitTask> tasks = new ArrayList<>();

    private boolean cacheDirty = false;

    public void start(GatheringModeService mode) {
        ChunkCacheDatabase.retireLegacySpotsFile();
        loadFreshness();
        startTasks(mode);
    }

    /** (Re)starts the sweep, particle and save tasks, e.g. after a reload changed their intervals. */
    public void startTasks(GatheringModeService mode) {
        stopTasks();
        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                sweep(System.currentTimeMillis(), mode.activePlayers());
            }
        }.runTaskTimer(Gathering.plugin, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS));

        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                SpotVisualManager.get().tickAllPlayers(SpotManager.this, mode);
            }
        }.runTaskTimer(Gathering.plugin, 0L, Cache.particleIntervalTicks));

        tasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                if (cacheDirty) saveFreshness();
            }
        }.runTaskTimer(Gathering.plugin, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS));
    }

    private void stopTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public void shutdown() {
        stopTasks();
        clearSpots();
        if (cacheDirty) saveFreshness();
    }

    public void loadFreshness() {
        freshness.replaceAll(ChunkCacheDatabase.parseDepletion(ChunkCacheDatabase.load()));
    }

    // ----------------------------------------------------------------------
    //  Spots
    // ----------------------------------------------------------------------

    public void register(GatheringSpot spot) {
        byId.put(spot.getId(), spot);
        activeByChunk.put(chunkKey(spot), spot.getId());
    }

    public void remove(GatheringSpot spot) {
        if (spot == null) return;
        byId.remove(spot.getId());
        ChunkKey key = chunkKey(spot);
        UUID existing = activeByChunk.get(key);
        if (spot.getId().equals(existing)) {
            activeByChunk.remove(key);
        }
    }

    public void clearSpots() {
        byId.clear();
        activeByChunk.clear();
    }

    /**
     * Removes spots that are too old, or that no gatherer has been within
     * {@link Cache#spotKeepaliveRadius} of for longer than the grace period. Returns how many went.
     */
    public int sweep(long now, Collection<Player> gatherers) {
        long lifetimeMs = Cache.spotLifetimeMinutes * 60_000L;
        long graceMs = Cache.spotOrphanGraceSeconds * 1000L;
        double keepaliveSq = Cache.spotKeepaliveRadius * Cache.spotKeepaliveRadius;

        List<GatheringSpot> expired = new ArrayList<>();
        for (GatheringSpot spot : byId.values()) {
            if (now - spot.getSpawnedAtMs() >= lifetimeMs) {
                expired.add(spot);
                continue;
            }
            for (Player gatherer : gatherers) {
                if (spot.distanceSquaredTo(gatherer.getLocation()) <= keepaliveSq) {
                    spot.markAttended(now);
                    break;
                }
            }
            if (now - spot.getLastAttendedMs() > graceMs) expired.add(spot);
        }
        for (GatheringSpot spot : expired) {
            remove(spot);
        }
        return expired.size();
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        UUID id = activeByChunk.get(new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
        if (id != null) remove(byId.get(id));
    }

    public boolean hasActiveSpotInChunk(ChunkKey key) {
        return key != null && activeByChunk.containsKey(key);
    }

    public GatheringSpot get(UUID id) {
        return byId.get(id);
    }

    public GatheringSpot getAtBlock(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return null;
        ChunkKey key = ChunkKey.of(blockLoc.getWorld().getName(), blockLoc.getBlockX(), blockLoc.getBlockZ());
        UUID id = activeByChunk.get(key);
        if (id == null) return null;
        GatheringSpot spot = byId.get(id);
        if (spot == null || !spot.matchesBlock(blockLoc)) return null;
        return spot;
    }

    public Collection<GatheringSpot> getAllSpots() {
        return byId.values();
    }

    public List<GatheringSpot> getSpotsNear(Location center, double radius) {
        List<GatheringSpot> result = new ArrayList<>();
        if (center == null || center.getWorld() == null || radius <= 0) return result;

        double radiusSq = radius * radius;
        Set<UUID> seen = new HashSet<>();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(radius / 16.0) + 1;
        String worldName = center.getWorld().getName();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkKey key = new ChunkKey(worldName, centerChunkX + dx, centerChunkZ + dz);
                UUID id = activeByChunk.get(key);
                if (id == null || !seen.add(id)) continue;
                GatheringSpot spot = byId.get(id);
                if (spot == null) continue;
                if (spot.distanceSquaredTo(center) <= radiusSq) {
                    result.add(spot);
                }
            }
        }
        return result;
    }

    public int getActiveSpotCount() {
        return byId.size();
    }

    // ----------------------------------------------------------------------
    //  Chunk freshness
    // ----------------------------------------------------------------------

    public double freshness(ChunkKey key, long now) {
        return freshness.freshness(key, now);
    }

    public double depletion(ChunkKey key, long now) {
        return freshness.depletion(key, now);
    }

    public void recordHarvest(ChunkKey key, long now) {
        if (freshness.add(key, Cache.freshnessPerHarvest, now)) cacheDirty = true;
    }

    public void recordSpawn(ChunkKey key, long now) {
        if (freshness.add(key, Cache.freshnessPerSpawn, now)) cacheDirty = true;
    }

    public void clearFreshness(String world) {
        freshness.clear(world);
        cacheDirty = true;
    }

    public int getTrackedChunkCount() {
        return freshness.size();
    }

    public int countChunksBelow(double threshold, long now) {
        return freshness.countBelow(threshold, now);
    }

    private void saveFreshness() {
        freshness.prune(System.currentTimeMillis());
        if (ChunkCacheDatabase.save(freshness.entries())) cacheDirty = false;
    }

    private ChunkKey chunkKey(GatheringSpot spot) {
        return new ChunkKey(spot.getWorldName(), spot.getChunkX(), spot.getChunkZ());
    }

    public ChunkKey chunkKey(String world, int chunkX, int chunkZ) {
        return new ChunkKey(world, chunkX, chunkZ);
    }

    public World getConfiguredWorld(String worldName) {
        return Bukkit.getWorld(worldName);
    }
}
