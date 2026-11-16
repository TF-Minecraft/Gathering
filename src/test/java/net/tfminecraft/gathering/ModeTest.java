package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.manager.GatherCommand;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.mode.GatheringModeService.EnterResult;
import net.tfminecraft.gathering.utils.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class ModeTest extends GatheringTestSupport {
  MockedStatic<CharacterBridge> characters;
  MockedStatic<FocusBridge> focus;
  Map<Player, Integer> points = new HashMap<>();

  @BeforeEach
  void mockBridges() {
    characters = mockStatic(CharacterBridge.class);
    focus = mockStatic(FocusBridge.class);
    focus.when(() -> FocusBridge.points(any())).thenAnswer(a -> points.getOrDefault(a.getArgument(0), 0));
    focus
        .when(() -> FocusBridge.trySpend(any(), anyInt()))
        .thenAnswer(
            a -> {
              Player player = a.getArgument(0);
              int amount = a.getArgument(1), have = points.getOrDefault(player, 0);
              if (have < amount) return false;
              points.put(player, have - amount);
              return true;
            });
  }

  @AfterEach
  void closeBridges() {
    characters.close();
    focus.close();
  }

  PlayerMock gatherer(int mp) {
    PlayerMock player = server.addPlayer();
    characters.when(() -> CharacterBridge.getActiveCharacterUuid(player)).thenReturn(UUID.randomUUID());
    points.put(player, mp);
    return player;
  }

  static List<String> messages(PlayerMock player) {
    List<String> out = new ArrayList<>();
    String message;
    while ((message = player.nextMessage()) != null) out.add(message);
    return out;
  }

  @Test
  void enteringNeedsACharacterAndChargesOnce() {
    PlayerMock nobody = server.addPlayer();
    assertEquals(EnterResult.NO_CHARACTER, mode.enter(nobody));
    assertEquals(List.of("§cYou need an active character to gather."), messages(nobody));
    PlayerMock broke = gatherer(0);
    assertEquals(EnterResult.NO_FOCUS, mode.enter(broke));
    assertEquals(List.of("§cYou are too unfocused to search."), messages(broke));
    assertFalse(mode.isActive(broke));
    assertFalse(mode.isActive(null));

    PlayerMock player = gatherer(5);
    assertEquals(EnterResult.ENTERED, mode.enter(player));
    assertEquals(4, points.get(player));
    assertEquals(List.of("§7You begin searching your surroundings carefully."), messages(player));
    assertTrue(mode.isActive(player));
    assertEquals(EnterResult.ALREADY_ACTIVE, mode.enter(player));
    assertEquals(4, points.get(player));
    assertEquals(1, mode.activeCount());
    assertEquals(List.of(player), mode.activePlayers());

    assertTrue(mode.leave(player));
    assertEquals(List.of("§7You stop searching."), messages(player));
    assertFalse(mode.leave(player));
    assertFalse(mode.leave(null));
  }

  @Test
  void drainChargesEveryIntervalAndRunningOutEndsTheMode() {
    Cache.drainIntervalSeconds = 3;
    PlayerMock player = gatherer(3);
    mode.start();
    mode.start(); // restarting replaces the old timer instead of doubling it
    mode.enter(player);
    messages(player);
    server.getScheduler().performTicks(40);
    assertEquals(2, points.get(player));
    server.getScheduler().performTicks(20);
    assertEquals(1, points.get(player));
    server.getScheduler().performTicks(60);
    assertEquals(0, points.get(player));
    assertTrue(mode.isActive(player));
    server.getScheduler().performTicks(60);
    assertFalse(mode.isActive(player));
    assertEquals(List.of("§7Your focus fades; you stop searching."), messages(player));
    mode.shutdown();
    mode.shutdown();
  }

  @Test
  void freeModeNeverEndsOnItsOwnAndOfflinePlayersAreDropped() {
    Cache.drainAmount = 0;
    Cache.drainIntervalSeconds = 1;
    PlayerMock player = gatherer(0);
    assertEquals(EnterResult.ENTERED, mode.enter(player));
    for (int i = 0; i < 5; i++) mode.tick();
    assertTrue(mode.isActive(player));
    player.disconnect();
    assertTrue(mode.activePlayers().isEmpty());
    mode.tick();
    assertEquals(0, mode.activeCount());
  }

  @Test
  void quittingClearsTheModeWithoutARefund() {
    PlayerMock player = gatherer(2);
    mode.enter(player);
    mode.onQuit(new PlayerQuitEvent(player, "bye"));
    assertFalse(mode.isActive(player));
    assertEquals(1, points.get(player));
  }

  static String barText(PlayerMock player) {
    var bars = player.getBossBars();
    if (bars.isEmpty()) return null;
    var bar = bars.iterator().next();
    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(bar.name())
        + " @" + bar.progress();
  }

  @Test
  void statusBarShowsPointsUpdatesAndHidesOnEveryExit() {
    focus.when(FocusBridge::max).thenReturn(100);
    PlayerMock player = gatherer(51);
    mode.enter(player);
    assertEquals("Gathering · 50 MP @0.5", barText(player));
    points.put(player, 250);
    mode.tick();
    assertEquals("Gathering · 50 MP @0.5", barText(player)); // refreshes every 2 s
    mode.tick();
    assertEquals("Gathering · 250 MP @1.0", barText(player));
    assertEquals(1, player.getBossBars().size());
    mode.leave(player);
    assertTrue(player.getBossBars().isEmpty());

    // Running out hides it too.
    Cache.drainIntervalSeconds = 1;
    points.put(player, 1);
    mode.enter(player);
    assertEquals("Gathering · 0 MP @0.0", barText(player));
    mode.tick();
    assertFalse(mode.isActive(player));
    assertTrue(player.getBossBars().isEmpty());

    // So does a shutdown.
    points.put(player, 10);
    mode.enter(player);
    mode.shutdown();
    assertFalse(mode.isActive(player));
    assertTrue(player.getBossBars().isEmpty());
  }

  @Test
  void statusBarCanBeTurnedOffAndCopesWithoutFocusData() {
    PlayerMock player = gatherer(10);
    mode.enter(player);
    assertEquals("Gathering · 9 MP @0.0", barText(player)); // no max known
    Cache.statusBarFormat = " ";
    mode.tick();
    mode.tick();
    assertTrue(player.getBossBars().isEmpty());
    mode.tick();
    mode.tick();
    Cache.statusBarFormat = null;
    mode.leave(player);
    mode.enter(player);
    assertTrue(player.getBossBars().isEmpty());
    mode.leave(player);
    assertFalse(mode.isActive(player));
    Cache.statusBarFormat = "&7{mp}";
    mode.enter(player);
    player.disconnect();
    mode.tick();
    assertEquals(0, mode.activeCount());
    // Quitting forgets the bar along with the mode.
    PlayerMock other = gatherer(10);
    mode.enter(other);
    mode.onQuit(new PlayerQuitEvent(other, "bye"));
    assertFalse(mode.isActive(other));
    Cache.messageLeave = "";
    Cache.messageEnter = null;
    mode.enter(other);
    mode.leave(other);
  }

  @Test
  void toggleSwitchesBothWays() {
    PlayerMock player = gatherer(5), broke = gatherer(0);
    assertTrue(mode.toggle(player));
    assertFalse(mode.toggle(player));
    assertFalse(mode.toggle(broke));
  }

  @Test
  void gatherCommandTogglesAndAcceptsOnOff() {
    GatherCommand command = new GatherCommand();
    CommandSender console = mock(CommandSender.class);
    assertTrue(command.onCommand(console, null, "gather", new String[] {}));
    verify(console).sendMessage("§cPlayers only.");
    PlayerMock player = gatherer(10);
    command.onCommand(player, null, "gather", new String[] {});
    assertTrue(mode.isActive(player));
    command.onCommand(player, null, "gather", new String[] {});
    assertFalse(mode.isActive(player));
    messages(player);
    command.onCommand(player, null, "gather", new String[] {"ON"});
    assertTrue(mode.isActive(player));
    messages(player);
    command.onCommand(player, null, "gather", new String[] {"on"});
    assertEquals(List.of("§7You are already searching."), messages(player));
    command.onCommand(player, null, "gather", new String[] {"off"});
    assertFalse(mode.isActive(player));
    messages(player);
    command.onCommand(player, null, "gather", new String[] {"off"});
    assertEquals(List.of("§7You are not searching."), messages(player));
    PlayerMock broke = gatherer(0);
    command.onCommand(broke, null, "gather", new String[] {"on"});
    assertEquals(List.of("§cYou are too unfocused to search."), messages(broke));
    command.onCommand(player, null, "gs", new String[] {"maybe"});
    assertEquals(List.of("§cUsage: /gs [on|off]"), messages(player));
    assertEquals(List.of("on", "off"), command.onTabComplete(player, null, "gather", new String[] {""}));
    assertEquals(List.of("off"), command.onTabComplete(player, null, "gather", new String[] {"OF"}));
    assertTrue(command.onTabComplete(player, null, "gather", new String[] {}).isEmpty());
    assertTrue(command.onTabComplete(player, null, "gather", new String[] {"on", ""}).isEmpty());
  }
}
