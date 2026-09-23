package net.tfminecraft.gathering.database;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.spot.GatheringSpot;

public final class SpotDatabase {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SpotDatabase() {}

    private static File getFile() {
        return new File(Gathering.plugin.getDataFolder(), "Data/spots.json");
    }

    public static List<GatheringSpot> loadAll() {
        File file = getFile();
        if (!file.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<SpotRecord>>() {}.getType();
            List<SpotRecord> records = GSON.fromJson(reader, listType);
            if (records == null) return new ArrayList<>();

            List<GatheringSpot> spots = new ArrayList<>();
            for (SpotRecord record : records) {
                GatheringSpot spot = record.toSpot();
                if (spot != null) spots.add(spot);
            }
            return spots;
        } catch (IOException ex) {
            Gathering.plugin.getLogger().warning("Failed to load spots: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    public static void saveAll(Iterable<GatheringSpot> spots) {
        File file = getFile();
        file.getParentFile().mkdirs();

        List<SpotRecord> records = new ArrayList<>();
        for (GatheringSpot spot : spots) {
            records.add(SpotRecord.from(spot));
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(records, writer);
        } catch (IOException ex) {
            Gathering.plugin.getLogger().warning("Failed to save spots: " + ex.getMessage());
        }
    }

    static final class SpotRecord {
        String id;
        String world;
        int blockX;
        int blockY;
        int blockZ;
        String spotTypeId;
        String spawnBlockMaterial;
        long spawnedAtMs;
        Map<String, Long> discoveredByCharacter = new HashMap<>();

        static SpotRecord from(GatheringSpot spot) {
            SpotRecord r = new SpotRecord();
            r.id = spot.getId().toString();
            r.world = spot.getWorldName();
            r.blockX = spot.getBlockX();
            r.blockY = spot.getBlockY();
            r.blockZ = spot.getBlockZ();
            r.spotTypeId = spot.getSpotTypeId();
            r.spawnBlockMaterial = spot.getSpawnBlockMaterial() != null
                    ? spot.getSpawnBlockMaterial().name() : Material.STONE.name();
            r.spawnedAtMs = spot.getSpawnedAtMs();
            r.discoveredByCharacter = new HashMap<>();
            for (Map.Entry<UUID, Long> e : spot.getDiscoveredByCharacter().entrySet()) {
                r.discoveredByCharacter.put(e.getKey().toString(), e.getValue());
            }
            return r;
        }

        GatheringSpot toSpot() {
            try {
                UUID uuid = UUID.fromString(id);
                Material mat = Material.valueOf(spawnBlockMaterial);
                Map<UUID, Long> discovered = new HashMap<>();
                if (discoveredByCharacter != null) {
                    for (Map.Entry<String, Long> e : discoveredByCharacter.entrySet()) {
                        try {
                            discovered.put(UUID.fromString(e.getKey()), e.getValue());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                return new GatheringSpot(uuid, world, blockX, blockY, blockZ, spotTypeId, mat,
                        spawnedAtMs, discovered);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
