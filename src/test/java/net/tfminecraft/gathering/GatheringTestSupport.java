package net.tfminecraft.gathering;

import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.tfminecraft.gathering.loader.*;
import net.tfminecraft.gathering.mode.GatheringModeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

abstract class GatheringTestSupport {
  @TempDir Path temp;
  ServerMock server;
  Gathering plugin;
  GatheringModeService mode;

  static SpotTypeLoader.SpotTypeDefinition type(String id) {
    return type(id, 1, null);
  }

  static SpotTypeLoader.SpotTypeDefinition type(String id, double weight, Set<Integer> provinces) {
    return new SpotTypeLoader.SpotTypeDefinition(
        id, Set.of(), null, null, null, null, weight, provinces, List.of());
  }

  @BeforeEach
  void setupServer() throws Exception {
    server = MockBukkit.mock();
    plugin = mock(Gathering.class);
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("GatheringTest"));
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getName()).thenReturn("Gathering");
    Gathering.plugin = plugin;
    mode = new GatheringModeService();
    when(plugin.getModeService()).thenReturn(mode);
    CategoryLoader.clear();
    SpotTypeLoader.clear();
    // Reset every Cache value to its documented default.
    new ConfigLoader().load(Files.writeString(temp.resolve("defaults.yml"), "{}").toFile());
  }

  @AfterEach
  void closeServer() {
    CategoryLoader.clear();
    SpotTypeLoader.clear();
    MockBukkit.unmock();
    Gathering.plugin = null;
  }
}
