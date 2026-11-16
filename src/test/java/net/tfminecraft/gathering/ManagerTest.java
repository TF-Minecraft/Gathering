package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spawn.NearbySpawnService;
import net.tfminecraft.gathering.spot.GatheringSpot;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Test;

class ManagerTest extends GatheringTestSupport {
  @Test
  void indexLookupAndRemoval() {
    World world = server.addSimpleWorld("world");
    World other = server.addSimpleWorld("other");
    SpotManager manager = new SpotManager();
    GatheringSpot spot = GatheringSpot.create("world", 1, 64, 1, "herb", Material.STONE);
    GatheringSpot second = GatheringSpot.create("world", 18, 64, 1, "ore", Material.STONE);
    manager.register(spot);
    manager.register(second);
    assertEquals(2, manager.getAllSpots().size());
    assertEquals(2, manager.getActiveSpotCount());
    assertEquals(spot, manager.get(spot.getId()));
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
    assertEquals(world, manager.getConfiguredWorld("world"));
    assertNull(manager.getConfiguredWorld("missing"));
    GatheringSpot replacement = GatheringSpot.create("world", 2, 64, 2, "herb", Material.STONE);
    manager.register(replacement);
    manager.remove(spot);
    assertTrue(manager.hasActiveSpotInChunk(key));
    manager.remove(replacement);
    assertFalse(manager.hasActiveSpotInChunk(key));
    manager.remove(null);
    assertNull(manager.get(replacement.getId()));
    manager.clearSpots();
    assertEquals(0, manager.getActiveSpotCount());
    assertFalse(manager.hasActiveSpotInChunk(new ChunkKey("world", 1, 0)));
  }

  @Test
  void stalePublicIndexEntriesAreIgnoredAndMovedIdentitiesAppearOnce() {
    World world = server.addSimpleWorld("world");
    SpotManager manager = new SpotManager();
    UUID id = UUID.randomUUID();
    manager.register(new GatheringSpot(id, "world", 1, 64, 1, "herb", Material.STONE, 1));
    GatheringSpot moved = new GatheringSpot(id, "world", 17, 64, 1, "herb", Material.STONE, 1);
    manager.register(moved);
    assertEquals(List.of(moved), manager.getSpotsNear(new Location(world, 8, 64, 1), 32));
    manager.getAllSpots().clear();
    assertNull(manager.getAtBlock(moved.getBlockCorner()));
    assertTrue(manager.getSpotsNear(new Location(world, 8, 64, 1), 32).isEmpty());
  }

  @Test
  void sweepRemovesOldAndUnattendedSpotsButKeepsWatchedOnes() {
    World world = server.addSimpleWorld("world");
    SpotManager manager = new SpotManager();
    Cache.spotLifetimeMinutes = 15;
    Cache.spotOrphanGraceSeconds = 60;
    Cache.spotKeepaliveRadius = 48;
    long start = 1_000_000L;
    GatheringSpot near = new GatheringSpot(UUID.randomUUID(), "world", 0, 64, 0, "h", Material.STONE, start);
    GatheringSpot far = new GatheringSpot(UUID.randomUUID(), "world", 500, 64, 0, "h", Material.STONE, start);
    GatheringSpot old =
        new GatheringSpot(UUID.randomUUID(), "world", 32, 64, 0, "h", Material.STONE, start - 15 * 60_000L);
    manager.register(near);
    manager.register(far);
    manager.register(old);
    Player gatherer = mock(Player.class);
    when(gatherer.getLocation()).thenReturn(new Location(world, 10, 64, 0));
    Player elsewhere = mock(Player.class);
    when(elsewhere.getLocation()).thenReturn(new Location(world, 2000, 64, 0));
    assertEquals(1, manager.sweep(start + 30_000, List.of(elsewhere, gatherer)));
    assertNull(manager.get(old.getId()));
    assertEquals(start + 30_000, near.getLastAttendedMs());
    assertEquals(start, far.getLastAttendedMs());
    // The far spot keeps its grace period, then goes; the watched one stays.
    assertEquals(0, manager.sweep(start + 60_000, List.of(gatherer)));
    assertEquals(1, manager.sweep(start + 60_001, List.of(gatherer)));
    assertNull(manager.get(far.getId()));
    assertNotNull(manager.get(near.getId()));
    // Nobody gathering: the last watched spot expires after the grace period.
    assertEquals(0, manager.sweep(start + 61_000, List.of()));
    assertEquals(1, manager.sweep(start + 60_001 + 60_001, List.of()));
    assertEquals(0, manager.getActiveSpotCount());
  }

  @Test
  void unloadingAChunkRemovesItsSpot() {
    World world = server.addSimpleWorld("world");
    SpotManager manager = new SpotManager();
    GatheringSpot spot = GatheringSpot.create("world", 1, 64, 1, "herb", Material.STONE);
    manager.register(spot);
    manager.onChunkUnload(new ChunkUnloadEvent(world.getChunkAt(5, 5)));
    assertEquals(1, manager.getActiveSpotCount());
    manager.onChunkUnload(new ChunkUnloadEvent(world.getChunkAt(0, 0)));
    assertEquals(0, manager.getActiveSpotCount());
  }

  @Test
  void freshnessIsRecordedSavedAndRestored() throws Exception {
    SpotManager manager = new SpotManager();
    ChunkKey key = new ChunkKey("world", 1, 1), other = new ChunkKey("other", 2, 2);
    long now = System.currentTimeMillis();
    assertEquals(1, manager.freshness(key, now));
    manager.recordSpawn(key, now);
    Cache.freshnessPerHarvest = 0;
    manager.recordHarvest(key, now);
    assertEquals(0, manager.getTrackedChunkCount());
    manager.shutdown();
    Cache.freshnessPerHarvest = 1;
    assertFalse(Files.exists(temp.resolve("Data/chunk-cache.json")));
    manager.recordHarvest(key, now);
    manager.recordHarvest(key, now);
    manager.recordHarvest(other, now);
    Cache.freshnessPerSpawn = .5;
    manager.recordSpawn(key, now);
    assertEquals(2.5, manager.depletion(key, now), 1e-9);
    assertEquals(1 / 3.5, manager.freshness(key, now), 1e-9);
    assertEquals(2, manager.getTrackedChunkCount());
    assertEquals(2, manager.countChunksBelow(.6, now));
    assertEquals(1, manager.countChunksBelow(.4, now));
    manager.shutdown();
    SpotManager restored = new SpotManager();
    restored.loadFreshness();
    assertEquals(2.5, restored.depletion(key, now), 1e-6);
    // A failed write keeps the state dirty, so a later save retries it.
    Files.delete(temp.resolve("Data/chunk-cache.json"));
    Files.createDirectory(temp.resolve("Data/chunk-cache.json"));
    restored.recordHarvest(key, now);
    restored.shutdown();
    Files.delete(temp.resolve("Data/chunk-cache.json"));
    restored.shutdown();
    assertTrue(Files.isRegularFile(temp.resolve("Data/chunk-cache.json")));
    restored.clearFreshness("other");
    assertEquals(1, restored.getTrackedChunkCount());
    restored.clearFreshness(null);
    assertEquals(0, restored.getTrackedChunkCount());
  }

  @Test
  void scheduledServicesSweepShowAndFlushState() throws Exception {
    Cache.particleIntervalTicks = 10;
    World world = server.addSimpleWorld("world");
    Files.createDirectories(temp.resolve("Data"));
    Files.writeString(temp.resolve("Data/spots.json"), "[]");
    GatheringModeService modeService = mock(GatheringModeService.class);
    when(modeService.activePlayers()).thenReturn(List.of());
    SpotManager manager = new SpotManager();
    manager.start(modeService);
    assertTrue(Files.exists(temp.resolve("Data/spots.json.old")));
    manager.register(
        new GatheringSpot(UUID.randomUUID(), "world", 1, 64, 1, "herb", Material.STONE, 0));
    manager.recordHarvest(new ChunkKey("world", 3, 3), System.currentTimeMillis());
    server.getScheduler().performTicks(1200);
    verify(modeService, atLeastOnce()).activePlayers();
    assertEquals(0, manager.getActiveSpotCount());
    assertTrue(Files.exists(temp.resolve("Data/chunk-cache.json")));
    server.getScheduler().performTicks(1200);
    manager.startTasks(modeService);
    manager.shutdown();
    assertEquals("world", world.getName());
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
    NearbySpawnService spawns = mock(NearbySpawnService.class);
    when(plugin.getSpotManager()).thenReturn(manager);
    when(plugin.getSpawnService()).thenReturn(spawns);
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
          {"adminmode"},
          {"freshness"}
        }) assertTrue(commands.onCommand(sender, null, "gathering", args));
    verify(plugin).reloadAll();
    verify(manager).clearFreshness(null);
    verify(manager).clearFreshness("world");
    verify(manager).getTrackedChunkCount();
    verify(manager).countChunksBelow(eq(.5), anyLong());
    verify(sender, times(3)).sendMessage("§cPlayers only.");

    Player player = server.addPlayer();
    player.setOp(true);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn", "bad"});
    verifyNoInteractions(spawns);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn"});
    verify(spawns).forceSpawn(player, null);
    var herb = type("herb");
    SpotTypeLoader.get().put("herb", herb);
    GatheringSpot spot =
        GatheringSpot.create(player.getWorld().getName(), 4, 65, 6, "herb", Material.STONE);
    when(spawns.forceSpawn(player, herb)).thenReturn(spot);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn", "herb"});
    assertEquals("§aSpawned §fherb §aat §f4, 65, 6", nextMessage(player, "§aSpawned"));

    when(manager.depletion(any(), anyLong())).thenReturn(1.0);
    when(manager.freshness(any(), anyLong())).thenReturn(.5);
    player.teleport(new Location(player.getWorld(), -1, 64, 17));
    commands.onCommand(player, null, "gathering", new String[] {"freshness"});
    assertEquals(
        "§7Chunk §f" + player.getWorld().getName() + ":-1:1§7: depletion §f1.00§7, freshness §f50%",
        nextMessage(player, "§7Chunk"));

    commands.onCommand(player, null, "gathering", new String[] {"adminmode"});
    assertTrue(AdminModeService.get().isEnabled(player));
    commands.onCommand(player, null, "gathering", new String[] {"adminmode"});
    assertFalse(AdminModeService.get().isEnabled(player));
    for (String state : List.of("on", "enable", "true")) {
      commands.onCommand(player, null, "gathering", new String[] {"adminmode", state});
      assertTrue(AdminModeService.get().isEnabled(player));
    }
    for (String state : List.of("off", "disable", "false")) {
      commands.onCommand(player, null, "gathering", new String[] {"adminmode", state});
      assertFalse(AdminModeService.get().isEnabled(player));
    }
    commands.onCommand(player, null, "gathering", new String[] {"adminmode", "bad"});
    assertEquals(
        List.of("reload"), commands.onTabComplete(sender, null, "gathering", new String[] {"RE"}));
    assertEquals(
        List.of("forcespawn", "freshness"),
        commands.onTabComplete(sender, null, "gathering", new String[] {"f"}));
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

  @Test
  void forceSpawnReportsWhenNothingFits() {
    CommandManager commands = new CommandManager();
    NearbySpawnService spawns = mock(NearbySpawnService.class);
    when(plugin.getSpawnService()).thenReturn(spawns);
    Player player = server.addPlayer();
    player.setOp(true);
    commands.onCommand(player, null, "gathering", new String[] {"forcespawn"});
    assertEquals("§cNo valid surface found nearby.", nextMessage(player, "§cNo valid"));
  }

  static String nextMessage(Player player, String prefix) {
    var mock = (org.mockbukkit.mockbukkit.entity.PlayerMock) player;
    String message;
    while ((message = mock.nextMessage()) != null) {
      if (message.startsWith(prefix)) return message;
    }
    return null;
  }
}
