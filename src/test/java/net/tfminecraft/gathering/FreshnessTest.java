package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.freshness.ChunkFreshness;
import net.tfminecraft.gathering.manager.ChunkKey;
import org.junit.jupiter.api.Test;

class FreshnessTest extends GatheringTestSupport {
  static final long HOUR = 3_600_000L;

  @Test
  void depletionHalvesEveryHalfLife() {
    assertEquals(4, ChunkFreshness.decay(4, 0, 24));
    assertEquals(4, ChunkFreshness.decay(4, -HOUR, 24));
    assertEquals(2, ChunkFreshness.decay(4, 24 * HOUR, 24), 1e-12);
    assertEquals(1, ChunkFreshness.decay(4, 48 * HOUR, 24), 1e-12);
    assertEquals(0, ChunkFreshness.decay(4, 1, 0));
  }

  @Test
  void specTableHolds() {
    ChunkFreshness freshness = new ChunkFreshness();
    ChunkKey key = new ChunkKey("world", 0, 0);
    assertEquals(1.0, freshness.freshness(key, 0));
    freshness.add(key, 1, 0);
    assertEquals(.5, freshness.freshness(key, 0), 1e-12);
    freshness.add(key, 1, 0);
    freshness.add(key, 1, 0);
    assertEquals(.25, freshness.freshness(key, 0), 1e-12);
    assertEquals(.571, freshness.freshness(key, 48 * HOUR), 1e-3);
    Cache.freshnessStrength = 0;
    assertEquals(1.0, freshness.freshness(key, 0));
  }

  @Test
  void addingDecaysTheOldValueFirstAndIgnoresNothing() {
    ChunkFreshness freshness = new ChunkFreshness();
    ChunkKey key = new ChunkKey("world", 0, 0);
    assertFalse(freshness.add(null, 1, 0));
    assertFalse(freshness.add(key, 0, 0));
    assertFalse(freshness.add(key, -1, 0));
    assertEquals(0, freshness.size());
    assertTrue(freshness.add(key, 2, 0));
    assertTrue(freshness.add(key, 1, 24 * HOUR));
    assertEquals(2, freshness.depletion(key, 24 * HOUR), 1e-12);
    assertEquals(24 * HOUR, freshness.entries().get(key).updatedMs);
  }

  @Test
  void pruningClearingAndCounting() {
    ChunkFreshness freshness = new ChunkFreshness();
    ChunkKey a = new ChunkKey("world", 0, 0), b = new ChunkKey("world", 1, 0), c = new ChunkKey("other", 0, 0);
    freshness.replaceAll(
        Map.of(
            a, new ChunkFreshness.Entry(.04, 0),
            b, new ChunkFreshness.Entry(3, 0),
            c, new ChunkFreshness.Entry(1, 0)));
    assertEquals(3, freshness.size());
    assertEquals(1, freshness.countBelow(.3, 0));
    assertEquals(2, freshness.countBelow(.6, 0));
    freshness.prune(0);
    assertEquals(2, freshness.size());
    assertNull(freshness.entries().get(a));
    freshness.clear("other");
    assertEquals(1, freshness.size());
    freshness.clear(" ");
    assertEquals(0, freshness.size());
    freshness.add(a, 1, 0);
    freshness.clear(null);
    assertEquals(0, freshness.size());
  }
}
