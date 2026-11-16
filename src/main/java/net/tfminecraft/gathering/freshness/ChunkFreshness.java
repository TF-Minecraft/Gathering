package net.tfminecraft.gathering.freshness;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.manager.ChunkKey;

/**
 * How worked-over each chunk is. Harvests (and optionally spawns) add depletion, which halves every
 * {@link Cache#freshnessHalfLifeHours}. Decay is computed on read, so no task has to tick it down.
 */
public final class ChunkFreshness {

    /** Entries whose depletion has decayed below this are dropped when saving. */
    public static final double PRUNE_BELOW = 0.05;

    public static final class Entry {
        public final double value;
        public final long updatedMs;

        public Entry(double value, long updatedMs) {
            this.value = value;
            this.updatedMs = updatedMs;
        }
    }

    private final Map<ChunkKey, Entry> entries = new HashMap<>();

    public static double decay(double value, long elapsedMs, double halfLifeHours) {
        if (elapsedMs <= 0) return value;
        if (halfLifeHours <= 0) return 0.0;
        double halfLifeMs = halfLifeHours * 3_600_000.0;
        return value * Math.pow(0.5, elapsedMs / halfLifeMs);
    }

    public static double freshnessOf(double depletion) {
        return 1.0 / (1.0 + depletion * Cache.freshnessStrength);
    }

    public double depletion(ChunkKey key, long now) {
        Entry entry = entries.get(key);
        if (entry == null) return 0.0;
        return decay(entry.value, now - entry.updatedMs, Cache.freshnessHalfLifeHours);
    }

    public double freshness(ChunkKey key, long now) {
        return freshnessOf(depletion(key, now));
    }

    /** Adds depletion; returns true if anything changed. */
    public boolean add(ChunkKey key, double amount, long now) {
        if (key == null || amount <= 0) return false;
        entries.put(key, new Entry(depletion(key, now) + amount, now));
        return true;
    }

    public void prune(long now) {
        entries.keySet().removeIf(key -> depletion(key, now) < PRUNE_BELOW);
    }

    /** Clears every chunk, or only chunks in one world when {@code world} is set. */
    public void clear(String world) {
        if (world == null || world.isBlank()) {
            entries.clear();
        } else {
            entries.keySet().removeIf(key -> world.equals(key.world));
        }
    }

    public int size() {
        return entries.size();
    }

    public int countBelow(double freshness, long now) {
        int count = 0;
        for (ChunkKey key : entries.keySet()) {
            if (freshness(key, now) < freshness) count++;
        }
        return count;
    }

    public Map<ChunkKey, Entry> entries() {
        return entries;
    }

    public void replaceAll(Map<ChunkKey, Entry> loaded) {
        entries.clear();
        entries.putAll(loaded);
    }
}
