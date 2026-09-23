package net.tfminecraft.gathering.spot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class GatheringSpot {

    private final UUID id;
    private final String world;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final String spotTypeId;
    private final Material spawnBlockMaterial;
    private final long spawnedAtMs;
    private final Map<UUID, Long> discoveredByCharacter = new HashMap<>();

    public GatheringSpot(UUID id, String world, int blockX, int blockY, int blockZ,
            String spotTypeId, Material spawnBlockMaterial, long spawnedAtMs,
            Map<UUID, Long> discoveredByCharacter) {
        this.id = id;
        this.world = world;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.spotTypeId = spotTypeId;
        this.spawnBlockMaterial = spawnBlockMaterial;
        this.spawnedAtMs = spawnedAtMs;
        if (discoveredByCharacter != null) {
            this.discoveredByCharacter.putAll(discoveredByCharacter);
        }
    }

    public static GatheringSpot create(String world, int blockX, int blockY, int blockZ,
            String spotTypeId, Material spawnBlockMaterial) {
        return new GatheringSpot(UUID.randomUUID(), world, blockX, blockY, blockZ,
                spotTypeId, spawnBlockMaterial, System.currentTimeMillis(), null);
    }

    public UUID getId() {
        return id;
    }

    public String getWorldName() {
        return world;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public String getSpotTypeId() {
        return spotTypeId;
    }

    public Material getSpawnBlockMaterial() {
        return spawnBlockMaterial;
    }

    public long getSpawnedAtMs() {
        return spawnedAtMs;
    }

    public Map<UUID, Long> getDiscoveredByCharacter() {
        return discoveredByCharacter;
    }

    public boolean isDiscoveredBy(UUID characterUuid) {
        return characterUuid != null && discoveredByCharacter.containsKey(characterUuid);
    }

    public void markDiscovered(UUID characterUuid) {
        if (characterUuid == null) return;
        discoveredByCharacter.putIfAbsent(characterUuid, System.currentTimeMillis());
    }

    public int getChunkX() {
        return blockX >> 4;
    }

    public int getChunkZ() {
        return blockZ >> 4;
    }

    public World resolveWorld() {
        return Bukkit.getWorld(world);
    }

    public Location getAnchor() {
        World w = resolveWorld();
        if (w == null) return null;
        return new Location(w, blockX + 0.5, blockY + 0.5, blockZ + 0.5);
    }

    public Location getParticleLocation() {
        Location anchor = getAnchor();
        return anchor == null ? null : anchor.clone().add(0, PARTICLE_Y_OFFSET, 0);
    }

    public Location getGatherEffectLocation() {
        Location anchor = getAnchor();
        return anchor == null ? null : anchor.clone().add(0, GATHER_EFFECT_Y_OFFSET, 0);
    }

    public Location getBlockCorner() {
        World w = resolveWorld();
        if (w == null) return null;
        return new Location(w, blockX, blockY, blockZ);
    }

    private static final double PARTICLE_Y_OFFSET = 0.35;
    private static final double GATHER_EFFECT_Y_OFFSET = 0.25;

    public boolean isChunkLoaded() {
        World w = resolveWorld();
        if (w == null) return false;
        return w.isChunkLoaded(getChunkX(), getChunkZ());
    }

    public double distanceSquaredTo(Location location) {
        if (location == null || location.getWorld() == null) return Double.MAX_VALUE;
        if (!world.equals(location.getWorld().getName())) return Double.MAX_VALUE;
        double dx = (blockX + 0.5) - location.getX();
        double dy = (blockY + 0.5) - location.getY();
        double dz = (blockZ + 0.5) - location.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    public boolean matchesBlock(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return false;
        return world.equals(blockLoc.getWorld().getName())
                && blockX == blockLoc.getBlockX()
                && blockY == blockLoc.getBlockY()
                && blockZ == blockLoc.getBlockZ();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GatheringSpot that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
