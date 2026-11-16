package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.*;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spawn.*;
import net.tfminecraft.gathering.spot.GatheringSpot;
import net.tfminecraft.gathering.utils.*;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SpawnTest extends GatheringTestSupport {
  /** Hands out queued doubles; anything unqueued is 0. */
  static final class Rolls extends Random {
    final Deque<Double> queue = new ArrayDeque<>();

    Rolls add(double... values) {
      for (double v : values) queue.add(v);
      return this;
    }

    @Override
    public double nextDouble() {
      return queue.isEmpty() ? 0.0 : queue.poll();
    }

    @Override
    public int nextInt(int bound) {
      return 0;
    }
  }

  static SpotTypeLoader.SpotTypeDefinition rules(
      String id, Material block, Integer altMin, Set<Biome> biomes, Set<Integer> provinces) {
    return new SpotTypeLoader.SpotTypeDefinition(
        id, biomes, altMin, null, block, null, 1, provinces, List.of());
  }

  World columnWorld(Map<Integer, Material> materials, int minY, int maxY) {
    World world = mock(World.class);
    when(world.getName()).thenReturn("world");
    when(world.getMinHeight()).thenReturn(minY);
    when(world.getMaxHeight()).thenReturn(maxY);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            a -> {
              Block b = mock(Block.class);
              when(b.getType()).thenReturn(materials.getOrDefault(a.getArgument(1), Material.AIR));
              when(b.getBiome()).thenReturn(Biome.PLAINS);
              return b;
            });
    return world;
  }

  @Test
  void columnProbeFindsTheFirstSurfaceThatSomeTypeAccepts() {
    Map<Integer, Material> materials = new HashMap<>();
    World world = columnWorld(materials, -64, 320);
    Cache.spawnMaxVertical = 16;
    var any = type("any");
    assertNull(SpawnPlanner.probeColumn(null, 0, 0, 64, List.of(any)));
    assertNull(SpawnPlanner.probeColumn(world, 0, 0, 64, null));
    assertNull(SpawnPlanner.probeColumn(world, 0, 0, 64, List.of()));
    assertNull(SpawnPlanner.probeColumn(world, 0, 0, 64, List.of(any)));
    // Leaves on top, grass below with headroom, a cave floor below that.
    materials.put(70, Material.OAK_LEAVES);
    materials.put(60, Material.GRASS_BLOCK);
    materials.put(50, Material.STONE);
    materials.put(52, Material.WATER);
    var grass = rules("grass", Material.GRASS_BLOCK, null, null, null);
    var stone = rules("stone", Material.STONE, null, null, null);
    var probe = SpawnPlanner.probeColumn(world, 3, 4, 64, List.of(grass, stone, any));
    assertEquals(70, probe.location.getBlockY());
    assertEquals(List.of(any), probe.types);
    probe = SpawnPlanner.probeColumn(world, 3, 4, 64, List.of(grass, stone));
    assertEquals(60, probe.location.getBlockY());
    assertEquals(3, probe.location.getBlockX());
    assertEquals(4, probe.location.getBlockZ());
    assertEquals(Material.GRASS_BLOCK, probe.surfaceMaterial);
    assertEquals(List.of(grass), probe.types);
    // Headroom: water above the stone floor, then a solid block directly above it.
    assertNull(SpawnPlanner.probeColumn(world, 3, 4, 64, List.of(stone)));
    materials.remove(52);
    materials.put(51, Material.DIRT);
    assertNull(SpawnPlanner.probeColumn(world, 3, 4, 64, List.of(stone)));
    materials.remove(51);
    assertEquals(50, SpawnPlanner.probeColumn(world, 3, 4, 64, List.of(stone)).location.getBlockY());
    // The scan stays within max-vertical of the player.
    assertNull(SpawnPlanner.probeColumn(world, 3, 4, 90, List.of(stone)));
    assertNull(SpawnPlanner.probeColumn(world, 3, 4, 20, List.of(grass)));
    // Altitude and biome rules.
    assertNull(
        SpawnPlanner.probeColumn(
            world, 3, 4, 64, List.of(rules("high", Material.GRASS_BLOCK, 61, null, null))));
    assertNull(
        SpawnPlanner.probeColumn(
            world,
            3,
            4,
            64,
            List.of(rules("desert", Material.GRASS_BLOCK, null, Set.of(Biome.DESERT), null))));
    // World height clamps the scan.
    World shallow = columnWorld(Map.of(2, Material.STONE, 0, Material.STONE), 0, 6);
    assertEquals(2, SpawnPlanner.probeColumn(shallow, 0, 0, 5, List.of(stone)).location.getBlockY());
    assertNull(SpawnPlanner.probeColumn(shallow, 0, 0, -40, List.of(stone)));
  }

  @Test
  void provinceIsLookedUpOnceAndOnlyWhenATypeNeedsIt() {
    Map<Integer, Material> materials = new HashMap<>(Map.of(60, Material.STONE, 64, Material.STONE));
    World world = columnWorld(materials, -64, 320);
    var anywhere = type("anywhere");
    var local = rules("local", null, null, null, Set.of(47));
    var elsewhere = rules("elsewhere", null, null, null, Set.of(12));
    try (MockedStatic<Provinces> provinces = mockStatic(Provinces.class)) {
      provinces.when(() -> Provinces.at(5, 6)).thenReturn(47);
      assertEquals(
          List.of(anywhere), SpawnPlanner.probeColumn(world, 1, 1, 64, List.of(anywhere)).types);
      provinces.verifyNoInteractions();
      assertEquals(
          List.of(local, anywhere),
          SpawnPlanner.probeColumn(world, 5, 6, 64, List.of(elsewhere, local, anywhere)).types);
      assertNull(SpawnPlanner.probeColumn(world, 5, 6, 64, List.of(elsewhere)));
      provinces.verify(() -> Provinces.at(5, 6), times(2));
    }
  }

  @Test
  void weightedPickRespectsZeroWeightsAndBoundaries() {
    var zero = type("zero", 0, null);
    var one = type("one", 1, null);
    var three = type("three", 3, null);
    assertSame(zero, SpawnPlanner.pickWeighted(List.of(zero), .9));
    assertSame(one, SpawnPlanner.pickWeighted(List.of(zero, one), 0));
    assertSame(one, SpawnPlanner.pickWeighted(List.of(one, three), .249));
    assertSame(three, SpawnPlanner.pickWeighted(List.of(one, three), .25));
    assertSame(three, SpawnPlanner.pickWeighted(List.of(one, three), .999));
  }

  @Test
  void capsAndChanceFollowConfig() {
    assertEquals(3, NearbySpawnService.capFor(0));
    assertEquals(5, NearbySpawnService.capFor(2));
    assertEquals(6, NearbySpawnService.capFor(9));
    assertEquals(.30, NearbySpawnService.spawnChance(null), 1e-9);
    RPCharacter character = mock(RPCharacter.class, RETURNS_DEEP_STUBS);
    when(character.getAttributeData().getAmount(any(AttributeModifier.class)))
        .thenAnswer(
            a -> ((AttributeModifier) a.getArgument(0)).getType().equals("wisdom") ? 10 : 8);
    assertEquals(.30 + .10 + .06, NearbySpawnService.spawnChance(character), 1e-9);
    Cache.spawnBaseChance = -5;
    assertEquals(0, NearbySpawnService.spawnChance(character));
    Cache.spawnBaseChance = 5;
    assertEquals(.85, NearbySpawnService.spawnChance(character));
  }

  World loadedWorld(Set<List<Integer>> loaded) {
    return loadedWorld("world", loaded);
  }

  World loadedWorld(String name, Set<List<Integer>> loaded) {
    World world = mock(World.class);
    when(world.getName()).thenReturn(name);
    when(world.isChunkLoaded(anyInt(), anyInt()))
        .thenAnswer(a -> loaded.contains(List.of((int) a.getArgument(0), (int) a.getArgument(1))));
    return world;
  }

  Player playerAt(World world, double x, double z) {
    Player player = mock(Player.class);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenAnswer(a -> new Location(world, x, 64, z));
    return player;
  }

  SpawnPlanner.ProbeResult probe(World world, int x, int z, SpotTypeLoader.SpotTypeDefinition... types) {
    return new SpawnPlanner.ProbeResult(new Location(world, x, 64, z), Material.STONE, List.of(types));
  }

  @Test
  void naturalSpawnWalksEveryRejectionBeforeSpawning() {
    World world = loadedWorld(Set.of(List.of(0, 0), List.of(-1, 0)));
    World nether = loadedWorld("world_nether", Set.of());
    Cache.worldBounds = Map.of("world", new ConfigLoader.WorldBounds("world", -10, 1, -10, 10));
    Cache.spawnLocationAttempts = 7;
    Cache.freshnessPerSpawn = .5;
    var a = type("a", 1, null);
    var b = type("b", 3, null);
    var zero = type("zero", 0, null);
    SpotTypeLoader.get().put("a", a);
    SpotTypeLoader.get().put("b", b);
    SpotTypeLoader.get().put("zero", zero);
    SpotManager manager = new SpotManager();
    GatheringSpot existing = GatheringSpot.create("world", -5, 64, 0, "a", Material.STONE);
    manager.register(existing);
    long now = System.currentTimeMillis();
    manager.recordHarvest(new ChunkKey("world", 0, 0), now);
    Rolls rolls = new Rolls();
    NearbySpawnService service = new NearbySpawnService(manager, mode, rolls);
    Player player = playerAt(world, .5, .5);
    List<Player> gatherers =
        List.of(
            player,
            playerAt(nether, .5, .5),
            playerAt(world, 200, 0),
            playerAt(world, 10, 10));
    rolls.add(.1); // spawn chance
    rolls.add(0, 1); // x=32: chunk 2 is out of bounds
    rolls.add(.75, 0); // z=-8: chunk (0,-1) is not loaded
    rolls.add(.5, 0); // x=-8: chunk (-1,0) already has a spot
    rolls.add(0, 0, .9); // chunk (0,0) is half fresh; .9 rejects it
    rolls.add(0, 0, .1); // probe finds nothing
    rolls.add(0, 0, .1); // probe lands next to the existing spot
    rolls.add(0, 0, .1, .5); // spawns; .5 of weight 4 picks b
    try (MockedStatic<SpawnPlanner> planner = mockStatic(SpawnPlanner.class, CALLS_REAL_METHODS);
        MockedStatic<CharacterBridge> characters = mockStatic(CharacterBridge.class)) {
      planner
          .when(() -> SpawnPlanner.probeColumn(eq(world), eq(8), eq(0), eq(64), any()))
          .thenReturn(null, probe(world, -4, 0, a, b), probe(world, 8, 0, a, b));
      GatheringSpot spot = service.trySpawnFor(player, gatherers, now);
      assertNotNull(spot);
      assertEquals("b", spot.getSpotTypeId());
      assertEquals(8, spot.getBlockX());
      assertEquals(0, spot.getBlockZ());
      assertTrue(rolls.queue.isEmpty());
      planner.verify(
          () -> SpawnPlanner.probeColumn(eq(world), eq(8), eq(0), eq(64), argThat(t -> !t.contains(zero))),
          times(3));
      assertEquals(1.5, manager.depletion(new ChunkKey("world", 0, 0), now), 1e-9);
      // Cap: base 3 plus 1 for the nearby gatherer; two spots are up already.
      Cache.capBase = 0;
      Cache.capPerExtraGatherer = 1;
      rolls.add(.1);
      assertNull(service.trySpawnFor(player, gatherers, now));
      assertEquals(1, rolls.queue.size());
      rolls.queue.clear();
      Cache.capBase = 3;
      rolls.add(.95);
      assertNull(service.trySpawnFor(player, gatherers, now));
      // No attempt finds a place.
      Cache.spawnLocationAttempts = 1;
      rolls.add(.1, 0, 1);
      assertNull(service.trySpawnFor(player, gatherers, now));
      // A world without bounds never spawns naturally.
      assertNull(service.trySpawnFor(playerAt(nether, 0, 0), List.of(), now));
      // Only weight-0 types left.
      SpotTypeLoader.clear();
      SpotTypeLoader.get().put("zero", zero);
      rolls.add(.1);
      assertNull(service.trySpawnFor(player, gatherers, now));
    }
  }

  @Test
  void forcedSpawnsIgnoreBoundsFreshnessAndWeights() {
    World world = loadedWorld(Set.of(List.of(0, 0)));
    Cache.worldBounds = Map.of();
    var zero = type("zero", 0, null);
    var other = type("other", 2, null);
    SpotTypeLoader.get().put("zero", zero);
    SpotTypeLoader.get().put("other", other);
    SpotManager manager = new SpotManager();
    manager.recordHarvest(new ChunkKey("world", 0, 0), System.currentTimeMillis());
    Cache.freshnessStrength = 1000;
    Rolls rolls = new Rolls();
    NearbySpawnService service = new NearbySpawnService(manager, mode, rolls);
    Player player = playerAt(world, .5, .5);
    try (MockedStatic<SpawnPlanner> planner = mockStatic(SpawnPlanner.class, CALLS_REAL_METHODS)) {
      planner
          .when(() -> SpawnPlanner.probeColumn(eq(world), eq(8), eq(0), eq(64), eq(List.of(zero))))
          .thenReturn(probe(world, 8, 0, zero));
      GatheringSpot spot = service.forceSpawn(player, zero);
      assertEquals("zero", spot.getSpotTypeId());
      manager.remove(spot);
      planner
          .when(() -> SpawnPlanner.probeColumn(eq(world), eq(8), eq(0), eq(64), any()))
          .thenReturn(null);
      Cache.spawnLocationAttempts = 2;
      assertNull(service.forceSpawn(player, null));
      planner.verify(
          () -> SpawnPlanner.probeColumn(eq(world), eq(8), eq(0), eq(64), any()), times(9));
    }
  }

  @Test
  void timerSpawnsForEveryGathererAndCanBeRestarted() {
    GatheringModeService modeService = mock(GatheringModeService.class);
    SpotManager manager = mock(SpotManager.class);
    World world = loadedWorld(Set.of());
    Player player = playerAt(world, 0, 0);
    when(modeService.activePlayers()).thenReturn(List.of(player));
    NearbySpawnService service = new NearbySpawnService(manager, modeService);
    Cache.spawnIntervalSeconds = 1;
    service.stop();
    service.start();
    service.start();
    server.getScheduler().performTicks(20);
    verify(modeService, times(1)).activePlayers();
    service.stop();
    server.getScheduler().performTicks(40);
    verify(modeService, times(1)).activePlayers();
  }
}
