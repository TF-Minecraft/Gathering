package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;

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
    assertTrue(plugin.getDataFolder().toPath().resolve("Data").toFile().isDirectory());
    var file = plugin.getDataFolder().toPath().resolve("config.yml");
    Files.writeString(file, "spawn-interval-minutes: 17\n");
    plugin.createFolders();
    plugin.createConfigs();
    plugin.reloadAll();
    assertEquals(17, net.tfminecraft.gathering.cache.Cache.spawnIntervalMinutes);
    assertEquals("spawn-interval-minutes: 17\n", Files.readString(file));
    plugin.onDisable();
    try (var paths = Files.walk(plugin.getDataFolder().toPath())) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.delete(path);
    }
    plugin.createFolders();
    assertTrue(Files.isDirectory(plugin.getDataFolder().toPath().resolve("Data")));
  }
}
