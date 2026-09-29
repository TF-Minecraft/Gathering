package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.gathering.database.*;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.spot.GatheringSpot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DatabaseTest extends GatheringTestSupport {
  Path data(String name, String content) throws Exception {
    Files.createDirectories(temp.resolve("Data"));
    return Files.writeString(temp.resolve("Data/" + name), content);
  }

  @Test
  void chunkCacheRoundTripsAndFiltersInvalidExpiredEntries() throws Exception {
    assertTrue(ChunkCacheDatabase.load().cooldowns.isEmpty());
    ChunkKey first = new ChunkKey("world", 1, -2), second = new ChunkKey("other", 3, 4);
    long future = System.currentTimeMillis() + 600000;
    ChunkCacheDatabase.save(Map.of(first, future), Set.of(first, second), Map.of(first, "blocked"));
    var loaded = ChunkCacheDatabase.load();
    assertEquals(Map.of(first, future), ChunkCacheDatabase.parseCooldowns(loaded));
    assertEquals(Set.of(first, second), ChunkCacheDatabase.parseExcluded(loaded));
    assertEquals("blocked", ChunkCacheDatabase.parseExclusionReasons(loaded).get(first));
    assertEquals("no_valid_surface", ChunkCacheDatabase.parseExclusionReasons(loaded).get(second));
    loaded.cooldowns.put("invalid", future);
    loaded.cooldowns.put("world:2:2", null);
    loaded.cooldowns.put("world:3:3", 1L);
    assertEquals(Map.of(first, future), ChunkCacheDatabase.parseCooldowns(loaded));
    loaded.excluded.add(null);
    loaded.excluded.add(new ChunkCacheDatabase.ExcludedChunk(null, 0, 0, null));
    loaded.excluded.add(new ChunkCacheDatabase.ExcludedChunk("new", 0, 0, null));
    assertEquals(3, ChunkCacheDatabase.parseExcluded(loaded).size());
    assertEquals(
        "no_valid_surface",
        ChunkCacheDatabase.parseExclusionReasons(loaded).get(new ChunkKey("new", 0, 0)));
    assertEquals(first, new ChunkCacheDatabase.ExcludedChunk("world", 1, -2, "x").toKey());
    loaded.cooldowns = null;
    loaded.excluded = null;
    assertTrue(ChunkCacheDatabase.parseCooldowns(loaded).isEmpty());
    assertTrue(ChunkCacheDatabase.parseExcluded(loaded).isEmpty());
    assertTrue(ChunkCacheDatabase.parseExclusionReasons(loaded).isEmpty());
    data("chunk-cache.json", "null");
    assertTrue(ChunkCacheDatabase.load().cooldowns.isEmpty());
    Files.delete(temp.resolve("Data/chunk-cache.json"));
    Files.createDirectory(temp.resolve("Data/chunk-cache.json"));
    assertTrue(ChunkCacheDatabase.load().cooldowns.isEmpty());
    assertDoesNotThrow(() -> ChunkCacheDatabase.save(Map.of(), Set.of(), Map.of()));
  }

  @Test
  void spotsRoundTripMetadataAndSkipInvalidRecords() throws Exception {
    assertTrue(SpotDatabase.loadAll().isEmpty());
    UUID charId = UUID.randomUUID(), id = UUID.randomUUID();
    GatheringSpot spot =
        new GatheringSpot(
            id, "world", 1, 64, 3, "herb", Material.GRASS_BLOCK, 42, Map.of(charId, 123L));
    GatheringSpot fallback =
        new GatheringSpot(UUID.randomUUID(), "world", 4, 5, 6, "herb", null, 43, null);
    SpotDatabase.saveAll(List.of(spot, fallback));
    var loaded = SpotDatabase.loadAll();
    assertEquals(2, loaded.size());
    assertEquals(spot, loaded.get(0));
    assertEquals(Map.of(charId, 123L), loaded.get(0).getDiscoveredByCharacter());
    assertEquals(42, loaded.get(0).getSpawnedAtMs());
    assertEquals(Material.STONE, loaded.get(1).getSpawnBlockMaterial());
    data(
        "spots.json",
        """
[{"id":"%s","world":"world","spawnBlockMaterial":"STONE","discoveredByCharacter":{"bad":4,"%s":5}},
{"id":"%s","world":"world","spawnBlockMaterial":"STONE","discoveredByCharacter":null},
{"id":"bad","spawnBlockMaterial":"STONE"},
{"id":"%s","spawnBlockMaterial":"INVALID"}]
"""
            .formatted(id, charId, UUID.randomUUID(), id));
    loaded = SpotDatabase.loadAll();
    assertEquals(2, loaded.size());
    assertEquals(Map.of(charId, 5L), loaded.get(0).getDiscoveredByCharacter());
    assertTrue(loaded.get(1).getDiscoveredByCharacter().isEmpty());
    data("spots.json", "null");
    assertTrue(SpotDatabase.loadAll().isEmpty());
    Files.delete(temp.resolve("Data/spots.json"));
    Files.createDirectory(temp.resolve("Data/spots.json"));
    assertTrue(SpotDatabase.loadAll().isEmpty());
    assertDoesNotThrow(() -> SpotDatabase.saveAll(List.of(spot)));
  }
}
