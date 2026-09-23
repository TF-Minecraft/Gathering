package net.tfminecraft.gathering.manager;

import java.util.Objects;

public final class ChunkKey {

    public final String world;
    public final int chunkX;
    public final int chunkZ;

    public ChunkKey(String world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static ChunkKey of(String world, int blockX, int blockZ) {
        return new ChunkKey(world, blockX >> 4, blockZ >> 4);
    }

    public String serialize() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public static ChunkKey deserialize(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(":");
        if (parts.length < 3) return null;
        try {
            return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey that)) return false;
        return chunkX == that.chunkX && chunkZ == that.chunkZ && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return serialize();
    }
}
