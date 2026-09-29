package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.tfminecraft.gathering.loader.SpotTypeLoader.*;
import net.tfminecraft.gathering.loot.DropCategory;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.spot.GatheringSpot;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DomainTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
  }

  @Test
  void chunkKeysRoundTripAndUseFloorCoordinates() {
    ChunkKey key = ChunkKey.of("world", -1, -17);
    assertEquals(-1, key.chunkX);
    assertEquals(-2, key.chunkZ);
    assertEquals("world:-1:-2", key.serialize());
    assertEquals(key.serialize(), key.toString());
    assertEquals(key, ChunkKey.deserialize(key.serialize()));
    assertEquals(key.hashCode(), ChunkKey.deserialize(key.serialize()).hashCode());
    assertEquals(key, key);
    assertNotEquals(key, null);
    assertNotEquals(key, "key");
    assertNotEquals(key, new ChunkKey("world", 1, -2));
    assertNotEquals(key, new ChunkKey("world", -1, 2));
    assertNotEquals(key, new ChunkKey("other", -1, -2));
    assertNull(ChunkKey.deserialize(null));
    assertNull(ChunkKey.deserialize("world"));
    assertNull(ChunkKey.deserialize("world:bad:2"));
  }

  @Test
  void spotsPreserveIdentityDiscoveriesAndBlockGeometry() {
    World world = server.addSimpleWorld("world");
    World other = server.addSimpleWorld("other");
    UUID id = UUID.randomUUID(), character = UUID.randomUUID();
    Map<UUID, Long> discoveries = new HashMap<>(Map.of(character, 123L));
    GatheringSpot spot =
        new GatheringSpot(
            id, "world", -1, 64, -17, "herb", Material.GRASS_BLOCK, 100L, discoveries);
    discoveries.clear();
    assertEquals(123L, spot.getDiscoveredByCharacter().get(character));
    assertEquals(id, spot.getId());
    assertEquals("world", spot.getWorldName());
    assertEquals(-1, spot.getBlockX());
    assertEquals(64, spot.getBlockY());
    assertEquals(-17, spot.getBlockZ());
    assertEquals("herb", spot.getSpotTypeId());
    assertEquals(Material.GRASS_BLOCK, spot.getSpawnBlockMaterial());
    assertEquals(100L, spot.getSpawnedAtMs());
    assertEquals(-1, spot.getChunkX());
    assertEquals(-2, spot.getChunkZ());
    spot.markDiscovered(character);
    assertEquals(123L, spot.getDiscoveredByCharacter().get(character));
    spot.markDiscovered(null);
    assertFalse(spot.isDiscoveredBy(null));
    UUID unseen = UUID.randomUUID();
    assertFalse(spot.isDiscoveredBy(unseen));
    spot.markDiscovered(unseen);
    assertTrue(spot.isDiscoveredBy(unseen));
    assertEquals(world, spot.resolveWorld());
    assertEquals(new Location(world, -.5, 64.5, -16.5), spot.getAnchor());
    assertEquals(64.85, spot.getParticleLocation().getY(), 1e-9);
    assertEquals(64.75, spot.getGatherEffectLocation().getY(), 1e-9);
    assertEquals(new Location(world, -1, 64, -17), spot.getBlockCorner());
    assertEquals(0, spot.distanceSquaredTo(spot.getAnchor()));
    assertEquals(14, spot.distanceSquaredTo(spot.getAnchor().add(1, 2, 3)));
    assertEquals(Double.MAX_VALUE, spot.distanceSquaredTo(null));
    assertEquals(Double.MAX_VALUE, spot.distanceSquaredTo(new Location(null, 0, 0, 0)));
    assertEquals(Double.MAX_VALUE, spot.distanceSquaredTo(new Location(other, 0, 0, 0)));
    assertTrue(spot.matchesBlock(new Location(world, -.1, 64.9, -16.1)));
    assertFalse(spot.matchesBlock(null));
    assertFalse(spot.matchesBlock(new Location(null, 0, 0, 0)));
    assertFalse(spot.matchesBlock(new Location(other, -1, 64, -17)));
    assertFalse(spot.matchesBlock(new Location(world, 0, 64, -17)));
    assertFalse(spot.matchesBlock(new Location(world, -1, 65, -17)));
    assertFalse(spot.matchesBlock(new Location(world, -1, 64, -16)));
    assertEquals(spot, spot);
    assertNotEquals(spot, null);
    assertNotEquals(spot, "spot");
    GatheringSpot same = new GatheringSpot(id, "other", 0, 0, 0, null, null, 0, null);
    assertEquals(spot, same);
    assertEquals(spot.hashCode(), same.hashCode());
    GatheringSpot made = GatheringSpot.create("world", 0, 0, 0, "herb", Material.STONE);
    assertNotEquals(spot, made);
    assertTrue(made.getSpawnedAtMs() > 0);
    World mocked = mock(World.class);
    when(mocked.getName()).thenReturn("unloaded");
    GatheringSpot missing = GatheringSpot.create("missing", 0, 0, 0, "herb", Material.STONE);
    assertNull(missing.getAnchor());
    assertNull(missing.getParticleLocation());
    assertNull(missing.getGatherEffectLocation());
    assertNull(missing.getBlockCorner());
    assertFalse(missing.isChunkLoaded());
    world.loadChunk(-1, -2);
    assertTrue(spot.isChunkLoaded());
  }

  @Test
  void dropLinesValidateRangesCommentsAndWeights() {
    assertTrue(new DropCategory("empty", null).isEmpty());
    DropCategory category =
        new DropCategory(
            "herbs",
            Arrays.asList(
                null,
                " ",
                "# comment",
                "bad",
                "STONE 2 1",
                "STONE x-2 1",
                "STONE 1-2 nope",
                "STONE 3-1 2 # note",
                "DIRT 0-0 -5"));
    assertEquals("herbs", category.getName());
    assertFalse(category.isEmpty());
    assertEquals(2, category.getEntries().size());
    DropCategory.Entry first = category.getEntries().getFirst();
    assertEquals("STONE", first.type);
    assertEquals(1, first.minAmount);
    assertEquals(3, first.maxAmount);
    assertEquals(2, first.weight);
    assertThrows(UnsupportedOperationException.class, () -> category.getEntries().clear());
    ThreadLocalRandom rng = mock(ThreadLocalRandom.class);
    when(rng.nextInt(1, 4)).thenReturn(2);
    assertEquals(2, first.rollAmount(rng));
    assertEquals(0, new DropCategory.Entry("X", -1, -2, 1).rollAmount(rng));
    assertNull(new DropCategory("empty", List.of()).pickOne(rng));
    assertNull(new DropCategory("zero", List.of("DIRT 1-1 0")).pickOne(rng));
    assertNull(new DropCategory("zeroamount", List.of("DIRT 0-0 1")).rollOne(rng));
    assertNull(new DropCategory("empty", List.of()).rollOne(rng));
    DropCategory.Drop drop = category.rollOne(rng);
    assertEquals("STONE", drop.type);
    assertEquals(2, drop.amount);
    assertEquals(3, category.rollMany(3, rng).size());
    assertTrue(category.rollMany(-1, rng).isEmpty());
    assertTrue(new DropCategory("zero", List.of("DIRT 0-0 1")).rollMany(2, rng).isEmpty());
    when(rng.nextDouble()).thenReturn(.75);
    assertEquals(
        "DIRT", new DropCategory("two", List.of("STONE 1-1 1", "DIRT 1-1 1")).rollOne(rng).type);
  }

  @Test
  void zeroWeightEntryMustNeverBeSelectedAtRandomLowerBound() {
    ThreadLocalRandom rng = mock(ThreadLocalRandom.class);
    when(rng.nextDouble()).thenReturn(0.0);
    DropCategory category = new DropCategory("weighted", List.of("DIRT 1-1 0", "STONE 1-1 1"));
    assertEquals("STONE", category.rollOne(rng).type);
  }

  @Test
  void spotDefinitionsRespectInclusiveLimitsAndDefaults() {
    SpotTypeDefinition open =
        new SpotTypeDefinition("a", null, null, null, null, null, 2, 5, null, List.of());
    assertTrue(open.acceptsBiome(Biome.PLAINS));
    assertTrue(open.acceptsAltitude(-100));
    assertTrue(open.acceptsSpawnBlock(Material.STONE));
    assertEquals(Material.DIRT, open.resolveParticleMaterial(Material.DIRT));
    SpotTypeDefinition bounded =
        new SpotTypeDefinition(
            "b",
            Set.of(Biome.PLAINS),
            10,
            20,
            Material.STONE,
            Material.DIRT,
            2,
            5,
            "herbalism",
            List.of());
    assertTrue(bounded.acceptsBiome(Biome.PLAINS));
    assertFalse(bounded.acceptsBiome(Biome.DESERT));
    assertFalse(bounded.acceptsAltitude(9));
    assertTrue(bounded.acceptsAltitude(10));
    assertTrue(bounded.acceptsAltitude(20));
    assertFalse(bounded.acceptsAltitude(21));
    assertTrue(bounded.acceptsSpawnBlock(Material.STONE));
    assertFalse(bounded.acceptsSpawnBlock(Material.DIRT));
    assertEquals(Material.DIRT, bounded.resolveParticleMaterial(Material.STONE));
    assertTrue(
        new SpotTypeDefinition("c", Set.of(), null, null, null, null, 1, 0, null, List.of())
            .acceptsBiome(Biome.DESERT));
    ThreadLocalRandom rng = mock(ThreadLocalRandom.class);
    CategoryWeight single = new CategoryWeight("a", -1, -1, 0);
    assertEquals(0, single.weight);
    assertEquals(1, single.rollDropCount(rng));
    CategoryWeight range = new CategoryWeight("b", 2, 2, 4);
    when(rng.nextInt(2, 5)).thenReturn(3);
    assertEquals(3, range.rollDropCount(rng));
  }
}
