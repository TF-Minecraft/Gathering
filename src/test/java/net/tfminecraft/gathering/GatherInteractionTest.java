package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.tfminecraft.gathering.loader.*;
import net.tfminecraft.gathering.loot.DropCategory;
import net.tfminecraft.gathering.manager.*;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spot.*;
import net.tfminecraft.gathering.utils.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GatherInteractionTest extends GatheringTestSupport {
  PlayerInteractEvent event(Player player, Block block, Action action, EquipmentSlot hand) {
    return new PlayerInteractEvent(player, action, null, block, BlockFace.UP, hand);
  }

  void categories(List<SpotTypeLoader.CategoryWeight> categories) {
    SpotTypeLoader.get()
        .put(
            "herb",
            new SpotTypeLoader.SpotTypeDefinition(
                "herb", Set.of(), null, null, null, null, 1, null, categories));
  }

  @Test
  void interactionGuardsGatheringModeAndUnavailableRewards() {
    Player player = server.addPlayer();
    Block block = player.getWorld().getBlockAt(1, 64, 1);
    block.setType(Material.STONE);
    SpotManager manager = new SpotManager();
    when(plugin.getSpotManager()).thenReturn(manager);
    SpotGatherHandler listener = new SpotGatherHandler();
    UUID character = UUID.randomUUID();
    try (MockedStatic<CharacterBridge> bridge = mockStatic(CharacterBridge.class);
        MockedStatic<FocusBridge> focus = mockStatic(FocusBridge.class);
        MockedStatic<ItemResolver> items = mockStatic(ItemResolver.class)) {
      listener.onInteract(event(player, block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND));
      listener.onInteract(event(player, null, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      GatheringSpot spot =
          GatheringSpot.create(player.getWorld().getName(), 1, 64, 1, "herb", Material.STONE);
      manager.register(spot);
      var absent = event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
      listener.onInteract(absent);
      assertFalse(absent.isCancelled());
      bridge.when(() -> CharacterBridge.getActiveCharacterUuid(player)).thenReturn(character);
      focus.when(() -> FocusBridge.trySpend(eq(player), anyInt())).thenReturn(true);
      var outsideMode = event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
      listener.onInteract(outsideMode);
      assertFalse(outsideMode.isCancelled());
      assertEquals(GatheringModeService.EnterResult.ENTERED, mode.enter(player));
      var missing = event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
      listener.onInteract(missing);
      assertTrue(missing.isCancelled());
      assertEquals(1, manager.getActiveSpotCount());
      categories(List.of());
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      categories(List.of(new SpotTypeLoader.CategoryWeight("missing", 0, 1, 1)));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      categories(List.of(new SpotTypeLoader.CategoryWeight("drops", 1, 1, 1)));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      CategoryLoader.get().put("drops", new DropCategory("drops", List.of()));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      CategoryLoader.get().put("drops", new DropCategory("drops", List.of("STONE 1-1 1")));
      listener.onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      assertEquals(1, manager.getActiveSpotCount());
    }
  }

  @Test
  void successfulGatherAwardsEffectsRemovesSpotAndDepletesChunk() {
    Player player = server.addPlayer();
    Block block = player.getWorld().getBlockAt(1, 64, 1);
    SpotManager manager = new SpotManager();
    when(plugin.getSpotManager()).thenReturn(manager);
    GatheringSpot spot =
        GatheringSpot.create(player.getWorld().getName(), 1, 64, 1, "herb", Material.STONE);
    manager.register(spot);
    AdminModeService.get().enable(player);
    categories(List.of(new SpotTypeLoader.CategoryWeight("drops", 1, 1, 1)));
    CategoryLoader.get().put("drops", new DropCategory("drops", List.of("STONE 2-2 1")));
    try (MockedStatic<ItemResolver> resolver = mockStatic(ItemResolver.class);
        MockedStatic<GatherFx> effects = mockStatic(GatherFx.class)) {
      ItemStack reward = new ItemStack(Material.STONE, 2);
      resolver.when(() -> ItemResolver.fromPath("STONE", 2)).thenReturn(reward);
      var event = event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
      new SpotGatherHandler().onInteract(event);
      assertTrue(event.isCancelled());
      assertEquals(0, manager.getActiveSpotCount());
      assertEquals(
          .5,
          manager.freshness(
              new ChunkKey(player.getWorld().getName(), 0, 0), System.currentTimeMillis()),
          1e-6);
      effects.verify(
          () ->
              GatherFx.playGather(
                  eq(player),
                  eq(spot.getGatherEffectLocation()),
                  eq(spot.getBlockCorner()),
                  eq(Material.STONE),
                  eq(List.of(reward))));
      // Stored worlds can disappear during plugin/world reload; rewards still go to the player.
      SpotManager mocked = mock(SpotManager.class);
      when(plugin.getSpotManager()).thenReturn(mocked);
      GatheringSpot missing = GatheringSpot.create("missing", 1, 64, 1, "herb", Material.STONE);
      when(mocked.getAtBlock(any())).thenReturn(missing);
      new SpotGatherHandler()
          .onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      effects.verify(() -> GatherFx.dropAtPlayer(player, List.of(reward)));
      verify(mocked).remove(missing);
      verify(mocked).recordHarvest(eq(new ChunkKey("missing", 0, 0)), anyLong());
      GatheringSpot unloading = mock(GatheringSpot.class);
      when(unloading.getSpotTypeId()).thenReturn("herb");
      when(unloading.getGatherEffectLocation()).thenReturn(player.getLocation());
      when(unloading.getWorldName()).thenReturn("world");
      when(mocked.getAtBlock(any())).thenReturn(unloading);
      new SpotGatherHandler()
          .onInteract(event(player, block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
      effects.verify(() -> GatherFx.dropAtPlayer(player, List.of(reward)), times(2));
    } finally {
      AdminModeService.get().disable(player);
    }
  }

  @Test
  void weightedCategoriesRespectZeroWeightsAndSelectionBoundaries() throws Exception {
    categories(
        List.of(
            new SpotTypeLoader.CategoryWeight("disabled", 0, 1, 1),
            new SpotTypeLoader.CategoryWeight("enabled", 1, 1, 1)));
    ThreadLocalRandom rng = mock(ThreadLocalRandom.class);
    when(rng.nextDouble()).thenReturn(0.0);
    Method pick =
        SpotGatherHandler.class.getDeclaredMethod(
            "pickCategory", SpotTypeLoader.SpotTypeDefinition.class);
    pick.setAccessible(true);
    try (MockedStatic<ThreadLocalRandom> random = mockStatic(ThreadLocalRandom.class)) {
      random.when(ThreadLocalRandom::current).thenReturn(rng);
      assertEquals(
          "enabled",
          ((SpotTypeLoader.CategoryWeight)
                  pick.invoke(new SpotGatherHandler(), SpotTypeLoader.getByString("herb")))
              .id);
      categories(
          List.of(
              new SpotTypeLoader.CategoryWeight("first", 1, 1, 1),
              new SpotTypeLoader.CategoryWeight("second", 1, 1, 1)));
      assertEquals(
          "first",
          ((SpotTypeLoader.CategoryWeight)
                  pick.invoke(new SpotGatherHandler(), SpotTypeLoader.getByString("herb")))
              .id);
      when(rng.nextDouble()).thenReturn(.75);
      assertEquals(
          "second",
          ((SpotTypeLoader.CategoryWeight)
                  pick.invoke(new SpotGatherHandler(), SpotTypeLoader.getByString("herb")))
              .id);
    }
  }
}
