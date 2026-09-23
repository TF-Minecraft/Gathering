package net.tfminecraft.gathering.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.database.ChunkCacheDatabase;
import net.tfminecraft.gathering.database.SpotDatabase;
import net.tfminecraft.gathering.discovery.SpotDiscoveryService;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.spawn.SpawnScheduler;
import net.tfminecraft.gathering.spot.GatheringSpot;

public final class SpotManager implements Listener {

    private final Map<UUID, GatheringSpot> byId = new HashMap<>();
    private final Map<ChunkKey, UUID> activeByChunk = new HashMap<>();
    private final Map<ChunkKey, Long> chunkCooldownUntil = new HashMap<>();
    private final Set<ChunkKey> excludedChunks = new HashSet<>();
    private final Map<ChunkKey, String> exclusionReasons = new HashMap<>();
    private final Map<String, Long> lastSpawnByTypeMs = new HashMap<>();

    private boolean dirty = false;
    private boolean cacheDirty = false;
    private SpawnScheduler spawnScheduler;

    public void start() {
        loadAllFromDisk();
        spawnScheduler = new SpawnScheduler(this);
        spawnScheduler.start();

        long passiveTicks = Cache.passiveIntervalSeconds * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                SpotDiscoveryService.tickPassive(SpotManager.this);
            }
        }.runTaskTimer(Gathering.plugin, passiveTicks, passiveTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                SpotVisualManager.get().tickAllPlayers(SpotManager.this);
            }
        }.runTaskTimer(Gathering.plugin, 0L, Cache.particleIntervalTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (dirty) saveSpots();
                if (cacheDirty) saveChunkCache();
            }
        }.runTaskTimer(Gathering.plugin, 1200L, 1200L);
    }

    public void shutdown() {
        if (dirty) saveSpots();
        if (cacheDirty) saveChunkCache();
    }

    public void loadAllFromDisk() {
        byId.clear();
        activeByChunk.clear();
        for (GatheringSpot spot : SpotDatabase.loadAll()) {
            registerInternal(spot);
        }

        ChunkCacheDatabase.CacheData cache = ChunkCacheDatabase.load();
        chunkCooldownUntil.clear();
        chunkCooldownUntil.putAll(ChunkCacheDatabase.parseCooldowns(cache));
        excludedChunks.clear();
        excludedChunks.addAll(ChunkCacheDatabase.parseExcluded(cache));
        exclusionReasons.clear();
        exclusionReasons.putAll(ChunkCacheDatabase.parseExclusionReasons(cache));
    }

    public void register(GatheringSpot spot) {
        registerInternal(spot);
        markDirty();
    }

    private void registerInternal(GatheringSpot spot) {
        byId.put(spot.getId(), spot);
        ChunkKey key = chunkKey(spot);
        activeByChunk.put(key, spot.getId());
    }

    public void remove(GatheringSpot spot) {
        if (spot == null) return;
        byId.remove(spot.getId());
        ChunkKey key = chunkKey(spot);
        UUID existing = activeByChunk.get(key);
        if (spot.getId().equals(existing)) {
            activeByChunk.remove(key);
        }
        markDirty();
    }

    public void setChunkCooldown(ChunkKey key) {
        if (key == null) return;
        long until = System.currentTimeMillis() + (Cache.chunkCooldownMinutes * 60_000L);
        chunkCooldownUntil.put(key, until);
        markCacheDirty();
    }

    public boolean isChunkOnCooldown(ChunkKey key) {
        Long until = chunkCooldownUntil.get(key);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            chunkCooldownUntil.remove(key);
            markCacheDirty();
            return false;
        }
        return true;
    }

    public boolean isChunkExcluded(ChunkKey key) {
        return key != null && excludedChunks.contains(key);
    }

    public void markChunkExcluded(ChunkKey key, String reason) {
        if (key == null) return;
        excludedChunks.add(key);
        exclusionReasons.put(key, reason != null ? reason : "no_valid_surface");
        markCacheDirty();
    }

    public void clearExcludedChunks(String worldFilter) {
        if (worldFilter == null || worldFilter.isBlank()) {
            excludedChunks.clear();
            exclusionReasons.clear();
        } else {
            excludedChunks.removeIf(k -> worldFilter.equals(k.world));
            exclusionReasons.keySet().removeIf(k -> worldFilter.equals(k.world));
        }
        markCacheDirty();
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

    public int countActiveByType(String spotTypeId) {
        int count = 0;
        for (GatheringSpot spot : byId.values()) {
            if (spotTypeId.equals(spot.getSpotTypeId())) count++;
        }
        return count;
    }

    public boolean isTypeOnCooldown(SpotTypeLoader.SpotTypeDefinition type) {
        if (type == null) return false;
        Long last = lastSpawnByTypeMs.get(type.id);
        if (last == null) return false;
        long cooldownMs = type.cooldownMinutes * 60_000L;
        return System.currentTimeMillis() - last < cooldownMs;
    }

    public void markTypeSpawned(String spotTypeId) {
        lastSpawnByTypeMs.put(spotTypeId, System.currentTimeMillis());
    }

    public SpotTypeLoader.SpotTypeDefinition pickSpawnableType() {
        List<SpotTypeLoader.SpotTypeDefinition> candidates = new ArrayList<>();
        for (SpotTypeLoader.SpotTypeDefinition type : SpotTypeLoader.all()) {
            if (countActiveByType(type.id) >= type.maxActive) continue;
            if (isTypeOnCooldown(type)) continue;
            candidates.add(type);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
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

    public int getExcludedChunkCount() {
        return excludedChunks.size();
    }

    public int getCooldownChunkCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Long until : chunkCooldownUntil.values()) {
            if (until != null && until > now) count++;
        }
        return count;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markCacheDirty() {
        cacheDirty = true;
    }

    private void saveSpots() {
        SpotDatabase.saveAll(byId.values());
        dirty = false;
    }

    private void saveChunkCache() {
        ChunkCacheDatabase.save(chunkCooldownUntil, excludedChunks, exclusionReasons);
        cacheDirty = false;
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
