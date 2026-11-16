package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spot.*;
import net.tfminecraft.gathering.utils.*;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.*;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.map.ProvinceGrid;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class IntegrationTest extends GatheringTestSupport {
  @Test
  void characterBridgeWaitsForAProviderAndReadySessionBeforeReadingRpcAttributes() {
    Player player = server.addPlayer();
    try (MockedStatic<PlayerManager> players = mockStatic(PlayerManager.class)) {
      assertNull(CharacterBridge.getActiveCharacter(null));
      assertNull(CharacterBridge.getActiveCharacterUuid(null));
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      CharacterIdentity identity = mock(CharacterIdentity.class);
      when(identity.currentSession(player)).thenReturn(Optional.empty());
      server.getServicesManager()
          .register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      players.verifyNoInteractions();
    }
  }

  @Test
  void characterBridgeUsesSharedUuidAndOnlyReturnsTheMatchingRpcAttributeModel() {
    Player player = server.addPlayer();
    UUID id = UUID.randomUUID();
    CharacterIdentity identity = mock(CharacterIdentity.class);
    when(identity.currentSession(player))
        .thenReturn(Optional.of(new CharacterSession(new CharacterRef(player.getUniqueId(), id), 1)));
    server.getServicesManager()
        .register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
    try (MockedStatic<PlayerManager> players = mockStatic(PlayerManager.class)) {
      assertEquals(id, CharacterBridge.getActiveCharacterUuid(player));
      players.verifyNoInteractions();
      assertNull(CharacterBridge.getActiveCharacter(player));
      PlayerData data = mock(PlayerData.class);
      players.when(() -> PlayerManager.get(player)).thenReturn(data);
      assertNull(CharacterBridge.getActiveCharacter(player));
      RPCharacter character = mock(RPCharacter.class);
      when(data.getActiveCharacter()).thenReturn(character);
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertEquals(id, CharacterBridge.getActiveCharacterUuid(player));
      when(character.getId()).thenReturn("invalid");
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertEquals(id, CharacterBridge.getActiveCharacterUuid(player));
      when(character.getId()).thenReturn(UUID.randomUUID().toString());
      assertNull(CharacterBridge.getActiveCharacter(player));
      when(character.getId()).thenReturn(id.toString().toUpperCase(Locale.ROOT));
      assertSame(character, CharacterBridge.getActiveCharacter(player));
      assertEquals(id, CharacterBridge.getActiveCharacterUuid(player));
    }
  }

  @Test
  void characterBridgeRechecksSessionsAndProviderRegistrationWithoutCachingOldIdentity() {
    Player player = server.addPlayer();
    UUID aliceId = UUID.randomUUID(), bobId = UUID.randomUUID();
    CharacterIdentity identity = mock(CharacterIdentity.class);
    server.getServicesManager()
        .register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
    RPCharacter alice = mock(RPCharacter.class), bob = mock(RPCharacter.class);
    when(alice.getId()).thenReturn(aliceId.toString());
    when(bob.getId()).thenReturn(bobId.toString());
    PlayerData data = mock(PlayerData.class);
    when(data.getActiveCharacter()).thenReturn(alice);
    try (MockedStatic<PlayerManager> players = mockStatic(PlayerManager.class)) {
      players.when(() -> PlayerManager.get(player)).thenReturn(data);
      when(identity.currentSession(player))
          .thenReturn(Optional.of(new CharacterSession(new CharacterRef(player.getUniqueId(), aliceId), 1)));
      assertSame(alice, CharacterBridge.getActiveCharacter(player));
      when(identity.currentSession(player))
          .thenReturn(Optional.of(new CharacterSession(new CharacterRef(player.getUniqueId(), bobId), 2)));
      assertEquals(bobId, CharacterBridge.getActiveCharacterUuid(player));
      assertNull(CharacterBridge.getActiveCharacter(player));
      when(data.getActiveCharacter()).thenReturn(bob);
      assertSame(bob, CharacterBridge.getActiveCharacter(player));
      when(identity.currentSession(player))
          .thenReturn(Optional.of(new CharacterSession(new CharacterRef(player.getUniqueId(), aliceId), 3)));
      assertEquals(aliceId, CharacterBridge.getActiveCharacterUuid(player));
      assertNull(CharacterBridge.getActiveCharacter(player));
      when(data.getActiveCharacter()).thenReturn(alice);
      assertSame(alice, CharacterBridge.getActiveCharacter(player));
      when(identity.currentSession(player)).thenReturn(Optional.empty());
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      server.getServicesManager().unregister(CharacterIdentity.class, identity);
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
    }
  }

  @Test
  void itemResolutionRejectsMissingItemsAndClampsAmounts() {
    assertNull(ItemResolver.fromPath(null, 1));
    assertNull(ItemResolver.fromPath(" ", 1));
    try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      tlibs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("missing")).thenReturn(null);
      assertNull(ItemResolver.fromPath("missing", 1));
      when(api.getCreator().getItemFromPath("air")).thenReturn(new ItemStack(Material.AIR));
      assertNull(ItemResolver.fromPath("air", 1));
      when(api.getCreator().getItemFromPath("stone"))
          .thenAnswer(a -> new ItemStack(Material.STONE));
      assertEquals(1, ItemResolver.fromPath("stone", -4).getAmount());
      assertEquals(64, ItemResolver.fromPath("stone", 100).getAmount());
      assertEquals(3, ItemResolver.fromPath("stone", 3).getAmount());
      when(api.getCreator().getItemFromPath("broken"))
          .thenThrow(new IllegalArgumentException("bad"));
      assertNull(ItemResolver.fromPath("broken", 1));
    }
  }

  @Test
  void provincesNeedAnEnabledSimpleFactionsWithALoadedGrid() {
    assertFalse(Provinces.available());
    assertEquals(Provinces.UNKNOWN, Provinces.at(1, 2));
    MockBukkit.createMockPlugin("SimpleFactions");
    try (MockedStatic<SimpleFactions> factions = mockStatic(SimpleFactions.class)) {
      assertFalse(Provinces.available());
      assertEquals(Provinces.UNKNOWN, Provinces.at(1, 2));
      SimpleFactions sf = mock(SimpleFactions.class);
      factions.when(SimpleFactions::getInstance).thenReturn(sf);
      assertFalse(Provinces.available());
      assertEquals(Provinces.UNKNOWN, Provinces.at(1, 2));
      ProvinceGrid grid = mock(ProvinceGrid.class);
      when(sf.getProvinceGrid()).thenReturn(grid);
      when(grid.getAt(1, 2)).thenReturn(47);
      assertTrue(Provinces.available());
      assertEquals(47, Provinces.at(1, 2));
    }
  }

  @Test
  void focusComesFromRpCharactersAndFreeChargesAlwaysSucceed() {
    Player player = server.addPlayer();
    try (MockedStatic<RPCharacters> rp = mockStatic(RPCharacters.class)) {
      assertEquals(0, FocusBridge.points(player));
      assertEquals(0, FocusBridge.max());
      assertFalse(FocusBridge.trySpend(player, 1));
      assertTrue(FocusBridge.trySpend(player, 0));
      FocusService focus = mock(FocusService.class);
      rp.when(RPCharacters::getFocusService).thenReturn(focus);
      when(focus.getPoints(player)).thenReturn(42);
      when(focus.getMax()).thenReturn(150);
      assertEquals(150, FocusBridge.max());
      when(focus.trySpend(player, 2)).thenReturn(true);
      assertEquals(42, FocusBridge.points(player));
      assertTrue(FocusBridge.trySpend(player, 2));
      assertFalse(FocusBridge.trySpend(player, 3));
      assertTrue(FocusBridge.trySpend(player, -1));
      verify(focus, never()).trySpend(player, -1);
    }
  }

  @Test
  void visualsShowSpotsOnlyToGatherersAndAdmins() {
    Player gatherer = server.addPlayer(), bystander = server.addPlayer(), admin = server.addPlayer();
    SpotManager manager = mock(SpotManager.class);
    GatheringSpot spot =
        GatheringSpot.create(gatherer.getWorld().getName(), 0, 64, 0, "herb", Material.STONE);
    when(manager.getSpotsNear(any(), anyDouble())).thenReturn(List.of(spot));
    GatheringModeService modeService = mock(GatheringModeService.class);
    when(modeService.isActive(gatherer)).thenReturn(true);
    try (MockedStatic<SpotParticles> particles = mockStatic(SpotParticles.class)) {
      AdminModeService.get().enable(admin);
      SpotVisualManager.get().tickAllPlayers(manager, modeService);
      particles.verify(() -> SpotParticles.tickRing(gatherer, spot, null, false));
      particles.verify(() -> SpotParticles.tickRing(admin, spot, null, true));
      particles.verify(() -> SpotParticles.tickRing(eq(bystander), any(), any(), anyBoolean()), never());
      verify(manager, times(2)).getSpotsNear(any(), eq(Cache.spotKeepaliveRadius));
    } finally {
      AdminModeService.get().disable(admin);
    }
  }
}
