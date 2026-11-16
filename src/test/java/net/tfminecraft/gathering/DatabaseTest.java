package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.gathering.database.*;
import net.tfminecraft.gathering.freshness.ChunkFreshness;
import net.tfminecraft.gathering.manager.ChunkKey;
import org.junit.jupiter.api.Test;

class DatabaseTest extends GatheringTestSupport {
  Path data(String name, String content) throws Exception {
    Files.createDirectories(temp.resolve("Data"));
    return Files.writeString(temp.resolve("Data/" + name), content);
  }

  @Test
  void depletionRoundTripsAndSkipsInvalidEntries() throws Exception {
    assertTrue(ChunkCacheDatabase.load().depletion.isEmpty());
    ChunkKey first = new ChunkKey("world", 1, -2), second = new ChunkKey("other", 3, 4);
    ChunkCacheDatabase.save(
        Map.of(first, new ChunkFreshness.Entry(1.5, 100), second, new ChunkFreshness.Entry(.2, 7)));
    var loaded = ChunkCacheDatabase.parseDepletion(ChunkCacheDatabase.load());
    assertEquals(2, loaded.size());
    assertEquals(1.5, loaded.get(first).value);
    assertEquals(100, loaded.get(first).updatedMs);
    assertEquals(.2, loaded.get(second).value);
    var raw = ChunkCacheDatabase.load();
    raw.depletion.put("invalid", new ChunkCacheDatabase.DepletionRecord(1, 1));
    raw.depletion.put("world:9:9", null);
    raw.depletion.put("world:8:8", new ChunkCacheDatabase.DepletionRecord(0, 1));
    raw.depletion.put("world:7:7", new ChunkCacheDatabase.DepletionRecord(-1, 1));
    raw.depletion.put("world:6:6", new ChunkCacheDatabase.DepletionRecord(Double.NaN, 1));
    raw.depletion.put("world:5:5", new ChunkCacheDatabase.DepletionRecord(Double.POSITIVE_INFINITY, 1));
    assertEquals(2, ChunkCacheDatabase.parseDepletion(raw).size());
    raw.depletion = null;
    assertTrue(ChunkCacheDatabase.parseDepletion(raw).isEmpty());
    assertEquals(0, new ChunkCacheDatabase.DepletionRecord().value);
  }

  @Test
  void oldCooldownFilesAndBrokenFilesLoadAsEmpty() throws Exception {
    data(
        "chunk-cache.json",
        "{\"cooldowns\":{\"world:1:1\":99999999999999},"
            + "\"excluded\":[{\"world\":\"world\",\"chunkX\":2,\"chunkZ\":2,\"reason\":\"x\"}]}");
    assertTrue(ChunkCacheDatabase.parseDepletion(ChunkCacheDatabase.load()).isEmpty());
    data("chunk-cache.json", "null");
    assertTrue(ChunkCacheDatabase.load().depletion.isEmpty());
    data("chunk-cache.json", "{\"depletion\": [not json");
    assertTrue(ChunkCacheDatabase.load().depletion.isEmpty());
    Files.delete(temp.resolve("Data/chunk-cache.json"));
    Files.createDirectory(temp.resolve("Data/chunk-cache.json"));
    assertTrue(ChunkCacheDatabase.load().depletion.isEmpty());
    assertDoesNotThrow(() -> ChunkCacheDatabase.save(Map.of()));
  }

  @Test
  void legacySpotsFileIsSetAsideOnce() throws Exception {
    ChunkCacheDatabase.retireLegacySpotsFile();
    assertFalse(Files.exists(temp.resolve("Data/spots.json.old")));
    data("spots.json", "[]");
    ChunkCacheDatabase.retireLegacySpotsFile();
    assertFalse(Files.exists(temp.resolve("Data/spots.json")));
    assertEquals("[]", Files.readString(temp.resolve("Data/spots.json.old")));
    // A blocked target (a non-empty directory) leaves the file where it is.
    Files.delete(temp.resolve("Data/spots.json.old"));
    Files.createDirectories(temp.resolve("Data/spots.json.old/inner"));
    data("spots.json", "[]");
    ChunkCacheDatabase.retireLegacySpotsFile();
    assertTrue(Files.exists(temp.resolve("Data/spots.json")));
  }
}
