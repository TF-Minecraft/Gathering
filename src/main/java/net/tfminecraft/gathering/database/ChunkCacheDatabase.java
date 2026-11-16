package net.tfminecraft.gathering.database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.freshness.ChunkFreshness;
import net.tfminecraft.gathering.manager.ChunkKey;

/**
 * Chunk depletion, saved in Data/chunk-cache.json. Older files also carried {@code cooldowns} and
 * {@code excluded}; those sections are ignored on load and dropped on the next save.
 */
public final class ChunkCacheDatabase {

    public static final class DepletionRecord {
        public double value;
        public long updated;

        public DepletionRecord() {}

        public DepletionRecord(double value, long updated) {
            this.value = value;
            this.updated = updated;
        }
    }

    public static final class CacheData {
        public Map<String, DepletionRecord> depletion = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ChunkCacheDatabase() {}

    private static File getFile() {
        return new File(Gathering.plugin.getDataFolder(), "Data/chunk-cache.json");
    }

    public static CacheData load() {
        File file = getFile();
        if (!file.exists()) return new CacheData();

        try (FileReader reader = new FileReader(file)) {
            CacheData data = GSON.fromJson(reader, CacheData.class);
            return data != null ? data : new CacheData();
        } catch (IOException | JsonParseException ex) {
            Gathering.plugin.getLogger().warning("Failed to load chunk cache: " + ex.getMessage());
            return new CacheData();
        }
    }

    /** Returns false if the file could not be written, so the caller can retry later. */
    public static boolean save(Map<ChunkKey, ChunkFreshness.Entry> entries) {
        CacheData data = new CacheData();
        for (Map.Entry<ChunkKey, ChunkFreshness.Entry> e : entries.entrySet()) {
            data.depletion.put(e.getKey().serialize(), new DepletionRecord(e.getValue().value, e.getValue().updatedMs));
        }

        File file = getFile();
        file.getParentFile().mkdirs();
        // Serialise first so a failed write always surfaces as an IOException, never a JsonIOException.
        String json = GSON.toJson(data);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
            return true;
        } catch (IOException ex) {
            Gathering.plugin.getLogger().warning("Failed to save chunk cache: " + ex.getMessage());
            return false;
        }
    }

    public static Map<ChunkKey, ChunkFreshness.Entry> parseDepletion(CacheData data) {
        Map<ChunkKey, ChunkFreshness.Entry> out = new HashMap<>();
        if (data.depletion == null) return out;
        for (Map.Entry<String, DepletionRecord> e : data.depletion.entrySet()) {
            ChunkKey key = ChunkKey.deserialize(e.getKey());
            DepletionRecord record = e.getValue();
            if (key == null || record == null) continue;
            if (!Double.isFinite(record.value) || record.value <= 0) continue;
            out.put(key, new ChunkFreshness.Entry(record.value, record.updated));
        }
        return out;
    }

    /** Old builds saved every spot; spots are now memory-only, so set the file aside once. */
    public static void retireLegacySpotsFile() {
        File legacy = new File(Gathering.plugin.getDataFolder(), "Data/spots.json");
        if (!legacy.isFile()) return;
        File retired = new File(Gathering.plugin.getDataFolder(), "Data/spots.json.old");
        if (legacy.renameTo(retired)) {
            Gathering.plugin.getLogger().info("Spots are no longer saved; moved Data/spots.json to spots.json.old.");
        } else {
            Gathering.plugin.getLogger().warning("Could not move the unused Data/spots.json aside.");
        }
    }
}
