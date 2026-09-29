package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Function;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.*;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.spawn.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SpawnTest extends GatheringTestSupport {
  @Test
  void chunkLoadingRestoresForceStateEvenWhenCallbackFails() {
    World world = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    when(world.getChunkAt(2, 3)).thenReturn(chunk);
    when(chunk.getX()).thenReturn(2);
    when(chunk.getZ()).thenReturn(3);
    assertNull(ChunkLoadHelper.withLoadedChunk(null, 2, 3, c -> "x"));
    assertNull(ChunkLoadHelper.withLoadedChunk(world, 2, 3, null));
    assertEquals(
        "ok",
        ChunkLoadHelper.withLoadedChunk(
            world,
            2,
            3,
            c -> {
              assertSame(chunk, c);
              return "ok";
            }));
    verify(chunk).setForceLoaded(true);
    verify(chunk).setForceLoaded(false);
    verify(world).unloadChunk(2, 3, false);
    when(world.isChunkLoaded(2, 3)).thenReturn(true);
    when(chunk.isForceLoaded()).thenReturn(true);
    assertThrows(
        IllegalStateException.class,
        () ->
            ChunkLoadHelper.withLoadedChunk(
                world,
                2,
                3,
                c -> {
                  throw new IllegalStateException("test");
                }));
    verify(world, times(1)).unloadChunk(2, 3, false);
    verify(chunk, times(3)).setForceLoaded(true);
    when(world.isChunkLoaded(2, 3)).thenReturn(false);
    when(world.unloadChunk(2, 3, false)).thenThrow(new IllegalStateException("busy"));
    assertEquals("ok", ChunkLoadHelper.withLoadedChunk(world, 2, 3, c -> "ok"));
  }

  @Test
  void plannerRequiresSolidSurfaceHeadroomBiomeAndAltitude() {
    World world = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    when(chunk.getWorld()).thenReturn(world);
    when(chunk.getX()).thenReturn(-1);
    when(chunk.getZ()).thenReturn(2);
    when(world.getMaxHeight()).thenReturn(5);
    when(world.getMinHeight()).thenReturn(0);
    Map<Integer, Material> materials = new HashMap<>();
    materials.put(1, Material.STONE);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            a -> {
              Block b = mock(Block.class);
              when(b.getType()).thenReturn(materials.getOrDefault(a.getArgument(1), Material.AIR));
              when(b.getBiome()).thenReturn(Biome.PLAINS);
              return b;
            });
    var type = ManagerTest.type("herb", 1, 0);
    Cache.probeColumnAttempts = 2;
    assertNull(SpawnPlanner.tryFindLocation(null, type));
    assertNull(SpawnPlanner.tryFindLocation(chunk, null));
    var result = SpawnPlanner.tryFindLocation(chunk, type);
    assertNotNull(result);
    assertEquals(1, result.location.getBlockY());
    assertEquals(Material.STONE, result.surfaceMaterial);
    assertTrue(result.location.getBlockX() >= -16 && result.location.getBlockX() < 0);
    assertTrue(result.location.getBlockZ() >= 32 && result.location.getBlockZ() < 48);
    materials.put(2, Material.WATER);
    assertNull(SpawnPlanner.tryFindLocation(chunk, type));
    materials.remove(2);
    materials.put(3, Material.WATER);
    assertNull(SpawnPlanner.tryFindLocation(chunk, type));
    materials.remove(3);
    assertNull(
        SpawnPlanner.tryFindLocation(
            chunk,
            new SpotTypeLoader.SpotTypeDefinition(
                "x", Set.of(), null, null, Material.DIRT, null, 1, 0, null, List.of())));
    assertNull(
        SpawnPlanner.tryFindLocation(
            chunk,
            new SpotTypeLoader.SpotTypeDefinition(
                "x", Set.of(), 2, null, null, null, 1, 0, null, List.of())));
    assertNull(
        SpawnPlanner.tryFindLocation(
            chunk,
            new SpotTypeLoader.SpotTypeDefinition(
                "x", Set.of(Biome.DESERT), null, null, null, null, 1, 0, null, List.of())));
    materials.clear();
    assertNull(SpawnPlanner.tryFindLocation(chunk, type));
    Cache.probeColumnAttempts = 0;
    assertNull(SpawnPlanner.tryFindLocation(chunk, type));
  }

  @Test
  void probesRegisterValidLocationsOrRememberExcludedChunks() {
    World world = server.addSimpleWorld("world");
    Chunk chunk = world.getChunkAt(1, 2);
    SpotManager manager = new SpotManager();
    var type = ManagerTest.type("herb", 1, 2);
    ChunkKey key = new ChunkKey("world", 1, 2);
    try (MockedStatic<SpawnPlanner> planner = mockStatic(SpawnPlanner.class)) {
      assertEquals(ChunkProbe.ProbeOutcome.EXCLUDED, ChunkProbe.probe(manager, chunk, type));
      assertTrue(manager.isChunkExcluded(key));
      planner
          .when(() -> SpawnPlanner.tryFindLocation(chunk, type))
          .thenReturn(new SpawnPlanner.ProbeResult(null, Material.STONE));
      assertEquals(ChunkProbe.ProbeOutcome.EXCLUDED, ChunkProbe.probe(manager, chunk, type));
      Location location = new Location(world, 17, 64, 33);
      planner
          .when(() -> SpawnPlanner.tryFindLocation(chunk, type))
          .thenReturn(new SpawnPlanner.ProbeResult(location, Material.STONE));
      assertEquals(ChunkProbe.ProbeOutcome.SPAWNED, ChunkProbe.probe(manager, chunk, type));
      assertEquals(location, manager.getAtBlock(location).getBlockCorner());
      assertTrue(manager.isTypeOnCooldown(type));
    }
  }

  @Test
  void schedulerSkipsUnavailableTypesWorldsAndOccupiedChunks() {
    SpotManager manager = mock(SpotManager.class);
    World world = server.addSimpleWorld("world");
    when(manager.getConfiguredWorld("world")).thenReturn(world);
    var type = ManagerTest.type("herb", 1, 0);
    when(manager.pickSpawnableType()).thenReturn(type);
    ChunkKey key = new ChunkKey("world", 2, 3);
    when(manager.chunkKey(eq("world"), anyInt(), anyInt())).thenReturn(key);
    Cache.spawnIntervalMinutes = 1;
    Cache.spawnAttemptsPerTick = 1;
    Cache.worldBounds = new LinkedHashMap<>();
    Cache.worldBounds.put("missing", new ConfigLoader.WorldBounds("missing", 0, 0, 0, 0));
    Cache.worldBounds.put("world", new ConfigLoader.WorldBounds("world", 2, 2, 4, 3));
    try (MockedStatic<ChunkLoadHelper> helper = mockStatic(ChunkLoadHelper.class);
        MockedStatic<ChunkProbe> probe = mockStatic(ChunkProbe.class)) {
      helper
          .when(() -> ChunkLoadHelper.withLoadedChunk(eq(world), anyInt(), anyInt(), any()))
          .thenAnswer(
              a ->
                  ((Function<Chunk, Object>) a.getArgument(3))
                      .apply(world.getChunkAt((int) a.getArgument(1), (int) a.getArgument(2))));
      new SpawnScheduler(manager).start();
      server.getScheduler().performTicks(1200);
      probe.verify(() -> ChunkProbe.probe(eq(manager), any(), eq(type)));
      when(manager.hasActiveSpotInChunk(key)).thenReturn(true);
      server.getScheduler().performTicks(1200);
      when(manager.hasActiveSpotInChunk(key)).thenReturn(false);
      when(manager.isChunkOnCooldown(key)).thenReturn(true);
      server.getScheduler().performTicks(1200);
      when(manager.isChunkOnCooldown(key)).thenReturn(false);
      when(manager.isChunkExcluded(key)).thenReturn(true);
      server.getScheduler().performTicks(1200);
      when(manager.isChunkExcluded(key)).thenReturn(false);
      when(manager.pickSpawnableType()).thenReturn(null);
      server.getScheduler().performTicks(1200);
      probe.verifyNoMoreInteractions();
    }
  }
}
