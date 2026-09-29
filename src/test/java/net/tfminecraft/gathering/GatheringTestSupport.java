package net.tfminecraft.gathering;

import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.logging.Logger;
import net.tfminecraft.gathering.loader.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

abstract class GatheringTestSupport {
  @TempDir Path temp;
  ServerMock server;
  Gathering plugin;

  @BeforeEach
  void setupServer() {
    server = MockBukkit.mock();
    plugin = mock(Gathering.class);
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("GatheringTest"));
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getName()).thenReturn("Gathering");
    Gathering.plugin = plugin;
    CategoryLoader.clear();
    SpotTypeLoader.clear();
  }

  @AfterEach
  void closeServer() {
    CategoryLoader.clear();
    SpotTypeLoader.clear();
    MockBukkit.unmock();
    Gathering.plugin = null;
  }
}
