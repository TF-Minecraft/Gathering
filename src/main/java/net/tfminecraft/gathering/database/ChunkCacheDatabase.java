package net.tfminecraft.gathering.database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.manager.ChunkKey;

public final class ChunkCacheDatabase {

    public static final class ExcludedChunk {
        public String world;
        public int chunkX;
        public int chunkZ;
        public String reason;

        public ExcludedChunk() {}

        public ExcludedChunk(String world, int chunkX, int chunkZ, String reason) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.reason = reason;
        }

        public ChunkKey toKey() {
            return new ChunkKey(world, chunkX, chunkZ);
        }
    }

    public static final class CacheData {
        public Map<String, Long> cooldowns = new HashMap<>();
        public List<ExcludedChunk> excluded = new ArrayList<>();
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
        } catch (IOException ex) {
            Gathering.plugin.getLogger().warning("Failed to load chunk cache: " + ex.getMessage());
            return new CacheData();
        }
    }

    public static void save(Map<ChunkKey, Long> cooldowns, Set<ChunkKey> excluded, Map<ChunkKey, String> reasons) {
        CacheData data = new CacheData();
        for (Map.Entry<ChunkKey, Long> e : cooldowns.entrySet()) {
            data.cooldowns.put(e.getKey().serialize(), e.getValue());
        }
        for (ChunkKey key : excluded) {
            String reason = reasons.getOrDefault(key, "no_valid_surface");
            data.excluded.add(new ExcludedChunk(key.world, key.chunkX, key.chunkZ, reason));
        }

        File file = getFile();
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException ex) {
            Gathering.plugin.getLogger().warning("Failed to save chunk cache: " + ex.getMessage());
        }
    }

    public static Map<ChunkKey, Long> parseCooldowns(CacheData data) {
        Map<ChunkKey, Long> out = new HashMap<>();
        if (data.cooldowns == null) return out;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : data.cooldowns.entrySet()) {
            ChunkKey key = ChunkKey.deserialize(e.getKey());
            if (key == null) continue;
            if (e.getValue() != null && e.getValue() > now) {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    public static Set<ChunkKey> parseExcluded(CacheData data) {
        Set<ChunkKey> out = new HashSet<>();
        if (data.excluded == null) return out;
        for (ExcludedChunk chunk : data.excluded) {
            if (chunk == null || chunk.world == null) continue;
            out.add(new ChunkKey(chunk.world, chunk.chunkX, chunk.chunkZ));
        }
        return out;
    }

    public static Map<ChunkKey, String> parseExclusionReasons(CacheData data) {
        Map<ChunkKey, String> out = new HashMap<>();
        if (data.excluded == null) return out;
        for (ExcludedChunk chunk : data.excluded) {
            if (chunk == null || chunk.world == null) continue;
            ChunkKey key = new ChunkKey(chunk.world, chunk.chunkX, chunk.chunkZ);
            out.put(key, chunk.reason != null ? chunk.reason : "no_valid_surface");
        }
        return out;
    }
}
