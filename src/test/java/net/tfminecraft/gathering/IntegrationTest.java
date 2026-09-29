package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmocore.manager.profession.ProfessionManager;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.discovery.SpotDiscoveryService;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.spot.*;
import net.tfminecraft.gathering.utils.*;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.*;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class IntegrationTest extends GatheringTestSupport {
  @Test
  void characterBridgeHandlesAbsentInactiveAndMalformedIdentities() {
    Player player = server.addPlayer();
    try (MockedStatic<PlayerManager> players = mockStatic(PlayerManager.class)) {
      assertNull(CharacterBridge.getActiveCharacter(null));
      assertNull(CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      PlayerData data = mock(PlayerData.class);
      players.when(() -> PlayerManager.get(player)).thenReturn(data);
      assertNull(CharacterBridge.getActiveCharacter(player));
      when(data.hasActiveCharacter()).thenReturn(true);
      RPCharacter character = mock(RPCharacter.class);
      when(data.getActiveCharacter()).thenReturn(character);
      assertSame(character, CharacterBridge.getActiveCharacter(player));
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      when(character.getId()).thenReturn("invalid");
      assertNull(CharacterBridge.getActiveCharacterUuid(player));
      UUID id = UUID.randomUUID();
      when(character.getId()).thenReturn(id.toString());
      assertEquals(id, CharacterBridge.getActiveCharacterUuid(player));
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
  void professionIntegrationIsOptionalAndUsesConfiguredOverride() throws Exception {
    Player player = server.addPlayer();
    Cache.professionEnabled = false;
    assertEquals(0, ProfessionBridge.professionBonus(player, null));
    Cache.professionEnabled = true;
    assertEquals(0, ProfessionBridge.professionBonus(player, null));
    MockBukkit.createMockPlugin("MMOCore");
    assertEquals(0, ProfessionBridge.professionBonus(null, null));
    Cache.professionId = null;
    assertEquals(0, ProfessionBridge.professionBonus(player, null));
    Cache.professionId = " ";
    assertEquals(0, ProfessionBridge.professionBonus(player, null));
    Cache.professionId = "herbalism";
    Cache.professionLevelWeight = .1;
    MMOCore original = MMOCore.plugin;
    try (MockedStatic<net.Indyuce.mmocore.api.player.PlayerData> players =
        mockStatic(net.Indyuce.mmocore.api.player.PlayerData.class)) {
      MMOCore core = mock(MMOCore.class);
      MMOCore.plugin = core;
      ProfessionManager professions = mock(ProfessionManager.class);
      var field = MMOCore.class.getField("professionManager");
      field.setAccessible(true);
      field.set(core, professions);
      assertEquals(0, ProfessionBridge.professionBonus(player, null));
      Profession profession = mock(Profession.class);
      when(professions.get("herbalism")).thenReturn(profession);
      when(professions.get("mining")).thenReturn(profession);
      var data = mock(net.Indyuce.mmocore.api.player.PlayerData.class, RETURNS_DEEP_STUBS);
      players.when(() -> net.Indyuce.mmocore.api.player.PlayerData.get(player)).thenReturn(data);
      when(data.getCollectionSkills().getLevel(profession)).thenReturn(3);
      assertEquals(.3, ProfessionBridge.professionBonus(player, ManagerTest.type("x", 1, 0)), 1e-9);
      var override =
          new SpotTypeLoader.SpotTypeDefinition(
              "ore", Set.of(), null, null, null, null, 1, 0, "mining", List.of());
      assertEquals(.3, ProfessionBridge.professionBonus(player, override), 1e-9);
      verify(professions).get("mining");
      when(data.getCollectionSkills().getLevel(profession)).thenReturn(-1);
      assertEquals(0, ProfessionBridge.professionBonus(player, null));
      when(professions.get("herbalism")).thenThrow(new IllegalStateException("unavailable"));
      assertEquals(0, ProfessionBridge.professionBonus(player, null));
    } finally {
      MMOCore.plugin = original;
    }
  }

  @Test
  void discoveryUsesAttributesClampsProbabilityAndNotifiesOnlyOnce() {
    Player player = server.addPlayer();
    SpotManager manager = mock(SpotManager.class);
    RPCharacter character = mock(RPCharacter.class, RETURNS_DEEP_STUBS);
    UUID id = UUID.randomUUID();
    GatheringSpot spot =
        GatheringSpot.create(player.getWorld().getName(), 0, 64, 0, "herb", Material.STONE);
    Cache.passiveBaseChance = .1;
    Cache.wisdomWeight = .02;
    Cache.intelligenceWeight = .015;
    when(character.getAttributeData().getAmount(any(AttributeModifier.class)))
        .thenAnswer(a -> ((AttributeModifier) a.getArgument(0)).getType().equals("wisdom") ? 3 : 4);
    try (MockedStatic<CharacterBridge> characters = mockStatic(CharacterBridge.class);
        MockedStatic<ProfessionBridge> professions = mockStatic(ProfessionBridge.class);
        MockedStatic<SpotVisualManager> visuals = mockStatic(SpotVisualManager.class)) {
      SpotVisualManager visual = mock(SpotVisualManager.class);
      visuals.when(SpotVisualManager::get).thenReturn(visual);
      assertEquals(.22, SpotDiscoveryService.computeChance(character, player, null), 1e-9);
      Cache.passiveBaseChance = -10;
      assertEquals(0, SpotDiscoveryService.computeChance(character, player, null));
      Cache.passiveBaseChance = 10;
      assertEquals(1, SpotDiscoveryService.computeChance(character, player, null));
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(null, character, spot, manager));
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(player, null, spot, manager));
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(player, character, null, manager));
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(player, character, spot, manager));
      characters.when(() -> CharacterBridge.getActiveCharacterUuid(player)).thenReturn(id);
      Cache.passiveBaseChance = -10;
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(player, character, spot, manager));
      Cache.passiveBaseChance = 10;
      Cache.passiveMessage = "&aFound";
      assertTrue(SpotDiscoveryService.tryPassiveDiscovery(player, character, spot, manager));
      assertTrue(spot.isDiscoveredBy(id));
      verify(manager).markDirty();
      verify(visual).refreshPlayer(player, manager);
      assertFalse(SpotDiscoveryService.tryPassiveDiscovery(player, character, spot, manager));
      for (String message : Arrays.asList(null, " ")) {
        Cache.passiveMessage = message;
        assertTrue(
            SpotDiscoveryService.tryPassiveDiscovery(
                player,
                character,
                GatheringSpot.create("world", 0, 0, 0, "herb", Material.STONE),
                manager));
      }
      Cache.passiveDiscoveryEnabled = false;
      SpotDiscoveryService.tickPassive(manager);
      characters.verify(() -> CharacterBridge.getActiveCharacter(player), never());
      Cache.passiveDiscoveryEnabled = true;
      SpotDiscoveryService.tickPassive(manager);
      characters.when(() -> CharacterBridge.getActiveCharacter(player)).thenReturn(character);
      when(manager.getSpotsNear(any(), anyDouble())).thenReturn(List.of(spot));
      SpotDiscoveryService.tickPassive(manager);
    }
  }

  @Test
  void visualsRespectOnlineCharacterDiscoveryAndAdminBypass() {
    Player player = server.addPlayer();
    SpotManager manager = mock(SpotManager.class);
    UUID id = UUID.randomUUID();
    GatheringSpot
        visible =
            GatheringSpot.create(player.getWorld().getName(), 0, 64, 0, "herb", Material.STONE),
        hidden =
            GatheringSpot.create(player.getWorld().getName(), 16, 64, 0, "herb", Material.STONE);
    visible.markDiscovered(id);
    when(manager.getSpotsNear(any(), anyDouble())).thenReturn(List.of(visible, hidden));
    try (MockedStatic<CharacterBridge> characters = mockStatic(CharacterBridge.class);
        MockedStatic<SpotParticles> particles = mockStatic(SpotParticles.class)) {
      SpotVisualManager visual = SpotVisualManager.get();
      visual.refreshPlayer(null, manager);
      visual.refreshPlayer(mock(Player.class), manager);
      visual.refreshPlayer(player, manager);
      verify(manager, never()).getSpotsNear(any(), anyDouble());
      characters.when(() -> CharacterBridge.getActiveCharacterUuid(player)).thenReturn(id);
      visual.tickAllPlayers(manager);
      particles.verify(() -> SpotParticles.tickRing(player, visible, null, false));
      particles.verify(() -> SpotParticles.tickRing(player, hidden, null, false), never());
      AdminModeService.get().enable(player);
      visual.refreshPlayer(player, manager);
      particles.verify(() -> SpotParticles.tickRing(player, visible, null, true));
      particles.verify(() -> SpotParticles.tickRing(player, hidden, null, true));
      AdminModeService.get().disable(player);
    }
  }
}
