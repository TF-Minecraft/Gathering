package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class LifecycleTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    MockBukkit.createMockPlugin("TLibs");
    MockBukkit.createMockPlugin("RPCharacters");
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
    Gathering.plugin = null;
  }

  @Test
  void pluginCreatesLoadsReloadsAndPreservesExistingConfiguration() throws Exception {
    Gathering plugin = MockBukkit.load(Gathering.class);
    assertSame(plugin, Gathering.plugin);
    assertNotNull(plugin.getSpotManager());
    assertNotNull(plugin.getCommand("gathering").getExecutor());
    assertNotNull(plugin.getCommand("gathering").getTabCompleter());
    assertNotNull(plugin.getCommand("gather").getExecutor());
    assertNotNull(plugin.getCommand("gather").getTabCompleter());
    assertNotNull(plugin.getModeService());
    assertNotNull(plugin.getSpawnService());
    assertTrue(plugin.getDataFolder().toPath().resolve("Data").toFile().isDirectory());
    var file = plugin.getDataFolder().toPath().resolve("config.yml");
    Files.writeString(file, "spawn:\n  interval-seconds: 17\n");
    var types = plugin.getDataFolder().toPath().resolve("spot-types.yml");
    Files.writeString(types, "local:\n  provinces: [4]\n");
    plugin.createFolders();
    plugin.createConfigs();
    plugin.getSpotManager()
        .register(
            net.tfminecraft.gathering.spot.GatheringSpot.create(
                "world", 0, 64, 0, "local", org.bukkit.Material.STONE));
    plugin.reloadAll();
    assertEquals(0, plugin.getSpotManager().getActiveSpotCount());
    assertEquals(17, net.tfminecraft.gathering.cache.Cache.spawnIntervalSeconds);
    assertEquals("spawn:\n  interval-seconds: 17\n", Files.readString(file));
    try (var provinces = mockStatic(net.tfminecraft.gathering.utils.Provinces.class)) {
      provinces.when(net.tfminecraft.gathering.utils.Provinces::available).thenReturn(true);
      plugin.reloadAll();
    }
    plugin.onDisable();
    try (var paths = Files.walk(plugin.getDataFolder().toPath())) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.delete(path);
    }
    plugin.createFolders();
    assertTrue(Files.isDirectory(plugin.getDataFolder().toPath().resolve("Data")));
  }
}
