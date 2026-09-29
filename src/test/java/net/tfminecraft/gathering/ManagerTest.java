package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.spawn.*;
import net.tfminecraft.gathering.spot.GatheringSpot;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ManagerTest extends GatheringTestSupport {
  static SpotTypeLoader.SpotTypeDefinition type(String id, int max, int cooldown) {
    return new SpotTypeLoader.SpotTypeDefinition(
        id, Set.of(), null, null, null, null, max, cooldown, null, List.of());
  }

  @Test
  void indexLookupRemovalPersistenceAndCooldowns() throws Exception {
    World world = server.addSimpleWorld("world");
    World other = server.addSimpleWorld("other");
    SpotManager manager = new SpotManager();
    manager.shutdown();
    manager.loadAllFromDisk();
    assertEquals(0, manager.getActiveSpotCount());
    GatheringSpot spot = GatheringSpot.create("world", 1, 64, 1, "herb", Material.STONE);
    GatheringSpot second = GatheringSpot.create("world", 18, 64, 1, "ore", Material.STONE);
    manager.register(spot);
    manager.register(second);
    assertEquals(2, manager.getAllSpots().size());
    assertEquals(spot, manager.get(spot.getId()));
    assertEquals(1, manager.countActiveByType("herb"));
    assertEquals(0, manager.countActiveByType("absent"));
    ChunkKey key = manager.chunkKey("world", 0, 0);
    assertTrue(manager.hasActiveSpotInChunk(key));
    assertFalse(manager.hasActiveSpotInChunk(null));
    assertEquals(spot, manager.getAtBlock(new Location(world, 1, 64, 1)));
    assertNull(manager.getAtBlock(null));
    assertNull(manager.getAtBlock(new Location(null, 0, 0, 0)));
    assertNull(manager.getAtBlock(new Location(world, 1, 65, 1)));
    assertNull(manager.getAtBlock(new Location(other, 1, 64, 1)));
    assertEquals(List.of(spot), manager.getSpotsNear(spot.getAnchor(), 5));
    assertEquals(2, manager.getSpotsNear(spot.getAnchor(), 20).size());
    assertTrue(manager.getSpotsNear(null, 5).isEmpty());
    assertTrue(manager.getSpotsNear(new Location(null, 0, 0, 0), 5).isEmpty());
    assertTrue(manager.getSpotsNear(spot.getAnchor(), 0).isEmpty());
    manager.setChunkCooldown(null);
    assertFalse(manager.isChunkOnCooldown(key));
    Cache.chunkCooldownMinutes = 10;
    manager.setChunkCooldown(key);
    assertTrue(manager.isChunkOnCooldown(key));
    assertEquals(1, manager.getCooldownChunkCount());
    Cache.chunkCooldownMinutes = 0;
    manager.setChunkCooldown(key);
    assertEquals(0, manager.getCooldownChunkCount());
    assertFalse(manager.isChunkOnCooldown(key));
    manager.markChunkExcluded(null, "bad");
    assertFalse(manager.isChunkExcluded(null));
    manager.markChunkExcluded(key, null);
    manager.markChunkExcluded(new ChunkKey("other", 0, 0), "stone");
    assertEquals(2, manager.getExcludedChunkCount());
    assertTrue(manager.isChunkExcluded(key));
    manager.clearExcludedChunks("world");
    assertFalse(manager.isChunkExcluded(key));
    assertEquals(1, manager.getExcludedChunkCount());
    manager.clearExcludedChunks(" ");
    assertEquals(0, manager.getExcludedChunkCount());
    manager.clearExcludedChunks(null);
    manager.shutdown();
    assertTrue(Files.exists(temp.resolve("Data/spots.json")));
    assertTrue(Files.exists(temp.resolve("Data/chunk-cache.json")));
    SpotManager restored = new SpotManager();
    restored.loadAllFromDisk();
    assertEquals(2, restored.getActiveSpotCount());
    assertEquals(spot, restored.getAtBlock(spot.getBlockCorner()));
    assertEquals(world, restored.getConfiguredWorld("world"));
    assertNull(restored.getConfiguredWorld("missing"));
    GatheringSpot replacement = GatheringSpot.create("world", 2, 64, 2, "herb", Material.STONE);
    manager.register(replacement);
    manager.remove(spot);
    assertTrue(manager.hasActiveSpotInChunk(key));
    manager.remove(replacement);
    assertFalse(manager.hasActiveSpotInChunk(key));
    manager.remove(null);
    assertNull(manager.get(replacement.getId()));
  }

  @Test
  void stalePublicIndexEntriesAreIgnoredAndMovedIdentitiesAppearOnce() {
    World world = server.addSimpleWorld("world");
    SpotManager manager = new SpotManager();
    UUID id = UUID.randomUUID();
    GatheringSpot first = new GatheringSpot(id, "world", 1, 64, 1, "herb", Material.STONE, 1, null);
    manager.register(first);
    GatheringSpot moved =
        new GatheringSpot(id, "world", 17, 64, 1, "herb", Material.STONE, 1, null);
    manager.register(moved);
    assertEquals(List.of(moved), manager.getSpotsNear(new Location(world, 8, 64, 1), 32));
    manager.getAllSpots().clear();
    assertNull(manager.getAtBlock(moved.getBlockCorner()));
    assertTrue(manager.getSpotsNear(new Location(world, 8, 64, 1), 32).isEmpty());
  }

  @Test
  void spawnSelectionHonorsCountsAndTypeCooldowns() {
    SpotManager manager = new SpotManager();
    assertNull(manager.pickSpawnableType());
    assertFalse(manager.isTypeOnCooldown(null));
    var herb = type("herb", 1, 5);
    SpotTypeLoader.get().put("herb", herb);
    assertFalse(manager.isTypeOnCooldown(herb));
    assertSame(herb, manager.pickSpawnableType());
    manager.markTypeSpawned("herb");
    assertTrue(manager.isTypeOnCooldown(herb));
    assertNull(manager.pickSpawnableType());
    var immediate = type("herb", 1, 0);
    SpotTypeLoader.get().put("herb", immediate);
    assertFalse(manager.isTypeOnCooldown(immediate));
    assertSame(immediate, manager.pickSpawnableType());
    manager.register(GatheringSpot.create("world", 1, 2, 3, "herb", Material.STONE));
    assertNull(manager.pickSpawnableType());
  }

  @Test
  void scheduledServicesFlushDirtyStateAndCanTickCleanly() {
    Cache.passiveDiscoveryEnabled = false;
    Cache.passiveIntervalSeconds = 1;
    Cache.particleIntervalTicks = 10;
    Cache.spawnIntervalMinutes = 1;
    Cache.worldBounds = Map.of();
    SpotManager manager = new SpotManager();
    manager.start();
    server.getScheduler().performTicks(1200);
    manager.register(GatheringSpot.create("world", 1, 64, 1, "herb", Material.STONE));
    manager.markChunkExcluded(new ChunkKey("world", 1, 1), "blocked");
    server.getScheduler().performTicks(1200);
    assertTrue(Files.exists(temp.resolve("Data/spots.json")));
    assertTrue(Files.exists(temp.resolve("Data/chunk-cache.json")));
    manager.shutdown();
  }

  @Test
  void adminStateTogglesAndCleansUpOnQuit() {
    Player player = server.addPlayer();
    AdminModeService admin = AdminModeService.get();
    assertFalse(admin.isEnabled(null));
    assertFalse(admin.enable(null));
    assertFalse(admin.disable(null));
    assertFalse(admin.toggle(null));
    assertFalse(admin.isEnabled(player));
    assertTrue(admin.enable(player));
    assertFalse(admin.enable(player));
    assertTrue(admin.isEnabled(player));
    assertTrue(admin.disable(player));
    assertFalse(admin.disable(player));
    assertTrue(admin.toggle(player));
    assertFalse(admin.toggle(player));
    admin.enable(player);
    admin.onQuit(new PlayerQuitEvent(player, "bye"));
    assertFalse(admin.isEnabled(player));
  }

  @Test
  void commandsValidatePermissionsAndDispatchAllSubcommands() {
    CommandManager commands = new CommandManager();
    CommandSender sender = mock(CommandSender.class);
    SpotManager manager = mock(SpotManager.class);
    when(plugin.getSpotManager()).thenReturn(manager);
    commands.onCommand(sender, null, "gathering", new String[] {"reload"});
    verify(sender).sendMessage("§cNo permission.");
    verify(plugin, never()).reloadAll();
    assertTrue(commands.onTabComplete(sender, null, "gathering", new String[] {""}).isEmpty());
    when(sender.hasPermission("gathering.admin")).thenReturn(true);
    for (String[] args :
        new String[][] {
          {},
          {"unknown"},
          {"reload"},
          {"status"},
          {"clearcache"},
          {"clearcache", "world"},
          {"forcespawn", "herb"},
          {"adminmode"}
        }) assertTrue(commands.onCommand(sender, null, "gathering", args));
    verify(plugin).reloadAll();
    verify(manager).clearExcludedChunks(null);
    verify(manager).clearExcludedChunks("world");
    verify(sender, times(2)).sendMessage("§cPlayers only.");
    Player player = server.addPlayer();
    player.setOp(true);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn"});
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn", "bad"});
    var type = type("herb", 2, 0);
    SpotTypeLoader.get().put("herb", type);
    ChunkKey key = new ChunkKey(player.getWorld().getName(), 0, 0);
    when(manager.chunkKey(anyString(), anyInt(), anyInt())).thenReturn(key);
    when(manager.hasActiveSpotInChunk(key)).thenReturn(true);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn", "herb"});
    when(manager.hasActiveSpotInChunk(key)).thenReturn(false);
    try (MockedStatic<ChunkLoadHelper> helper = mockStatic(ChunkLoadHelper.class);
        MockedStatic<ChunkProbe> probe = mockStatic(ChunkProbe.class)) {
      probe
          .when(() -> ChunkProbe.probe(eq(manager), any(), eq(type)))
          .thenReturn(ChunkProbe.ProbeOutcome.SPAWNED);
      helper
          .when(() -> ChunkLoadHelper.withLoadedChunk(any(), anyInt(), anyInt(), any()))
          .thenAnswer(
              a ->
                  ((java.util.function.Function<org.bukkit.Chunk, Object>) a.getArgument(3))
                      .apply(player.getWorld().getChunkAt(0, 0)));
      commands.onCommand(
          player, null, "gathering", new String[] {"forcespawn", "herb", "1", "bad"});
      helper
          .when(() -> ChunkLoadHelper.withLoadedChunk(any(), anyInt(), anyInt(), any()))
          .thenReturn(ChunkProbe.ProbeOutcome.EXCLUDED);
      commands.onCommand(player, null, "gathering", new String[] {"forcespawn", "herb"});
    }
    commands.onCommand(player, null, "gathering", new String[] {"adminmode"});
    assertTrue(AdminModeService.get().isEnabled(player));
    commands.onCommand(player, null, "gathering", new String[] {"adminmode"});
    assertFalse(AdminModeService.get().isEnabled(player));
    for (String mode : List.of("on", "enable", "true")) {
      commands.onCommand(player, null, "gathering", new String[] {"adminmode", mode});
      assertTrue(AdminModeService.get().isEnabled(player));
    }
    for (String mode : List.of("off", "disable", "false")) {
      commands.onCommand(player, null, "gathering", new String[] {"adminmode", mode});
      assertFalse(AdminModeService.get().isEnabled(player));
    }
    commands.onCommand(player, null, "gathering", new String[] {"adminmode", "bad"});
    assertEquals(
        List.of("reload"), commands.onTabComplete(sender, null, "gathering", new String[] {"RE"}));
    assertEquals(
        List.of("on", "off"),
        commands.onTabComplete(sender, null, "gathering", new String[] {"adminmode", "o"}));
    assertEquals(
        List.of("herb"),
        commands.onTabComplete(sender, null, "gathering", new String[] {"forcespawn", "h"}));
    assertFalse(
        commands
            .onTabComplete(sender, null, "gathering", new String[] {"clearcache", ""})
            .isEmpty());
    assertTrue(
        commands.onTabComplete(sender, null, "gathering", new String[] {"other", ""}).isEmpty());
    assertTrue(commands.onTabComplete(sender, null, "gathering", new String[] {}).isEmpty());
    assertTrue(
        commands
            .onTabComplete(sender, null, "gathering", new String[] {"forcespawn", "herb", ""})
            .isEmpty());
  }
}
