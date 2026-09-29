package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.spot.*;
import net.tfminecraft.gathering.utils.GatherFx;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EffectsTest extends GatheringTestSupport {
  Object helper(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = GatherFx.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  @Test
  void particlesRequireLoadedSpotsAndUseConfiguredDust() {
    Player player = mock(Player.class);
    World world = mock(World.class);
    GatheringSpot spot = mock(GatheringSpot.class);
    SpotParticles.tickRing(null, spot, null, false);
    SpotParticles.tickRing(player, null, null, false);
    SpotParticles.tickRing(player, spot, null, false);
    when(spot.getParticleLocation()).thenReturn(new Location(null, 0, 0, 0));
    SpotParticles.tickRing(player, spot, null, false);
    when(spot.getParticleLocation()).thenReturn(new Location(world, 1, 2, 3));
    SpotParticles.tickRing(player, spot, null, false);
    verifyNoInteractions(player);
    when(spot.isChunkLoaded()).thenReturn(true);
    Cache.particleRingRadius = .5;
    SpotParticles.tickRing(player, spot, null, false);
    var type =
        new SpotTypeLoader.SpotTypeDefinition(
            "x", Set.of(), null, null, null, Material.DIRT, 1, 0, null, List.of());
    for (int i = 0; i < 33; i++) SpotParticles.tickRing(player, spot, type, true);
    verify(player, times(68))
        .spawnParticle(
            eq(Particle.BLOCK),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            eq(1),
            eq(0d),
            eq(0d),
            eq(0d),
            eq(0d),
            any(BlockData.class));
    verify(player, times(33))
        .spawnParticle(
            eq(Particle.END_ROD), eq(1d), eq(2.5), eq(3d), eq(2), eq(.05), eq(.1), eq(.05), eq(0d));
    when(spot.getSpawnBlockMaterial()).thenReturn(Material.STONE);
    SpotParticles.tickRing(player, spot, null, false);
  }

  @Test
  void gatherSchedulesBurstsRewardEntitiesAndFiniteTrails() {
    World world = mock(World.class);
    Player player = mock(Player.class);
    Location corner = new Location(world, 1, 64, 2);
    Location effect = corner.clone().add(.5, .75, .5);
    when(player.getLocation()).thenAnswer(a -> corner.clone());
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(Material.STONE);
    when(block.getBlockData()).thenReturn(Material.STONE.createBlockData());
    when(world.getBlockAt(any(Location.class))).thenReturn(block);
    List<Item> spawned = new ArrayList<>();
    when(world.dropItem(any(Location.class), any(ItemStack.class)))
        .thenAnswer(
            a -> {
              Item item = mock(Item.class);
              when(item.isValid()).thenReturn(true);
              Location loc = a.getArgument(0);
              when(item.getLocation()).thenAnswer(b -> loc.clone());
              spawned.add(item);
              return item;
            });
    Cache.gatherBurstParticles = true;
    Cache.gatherKickVelocityMin = .4;
    Cache.gatherKickVelocityMax = .8;
    Cache.gatherKickHorizontalMin = .01;
    Cache.gatherKickHorizontalMax = .05;
    ItemStack plain = new ItemStack(Material.STONE, 2), named = new ItemStack(Material.DIAMOND);
    var meta = named.getItemMeta();
    meta.setDisplayName("Gem");
    named.setItemMeta(meta);
    GatherFx.playGather(null, effect, corner, Material.STONE, List.of(plain));
    GatherFx.playGather(player, null, corner, Material.STONE, List.of(plain));
    GatherFx.playGather(
        player, new Location(null, 0, 0, 0), corner, Material.STONE, List.of(plain));
    GatherFx.playGather(player, effect, corner, Material.STONE, null);
    GatherFx.playGather(player, effect, corner, Material.STONE, List.of());
    verifyNoInteractions(world);
    GatherFx.playGather(player, effect, corner, Material.STONE, Arrays.asList(plain, null, named));
    assertTrue(spawned.isEmpty());
    server.getScheduler().performTicks(15);
    assertEquals(2, spawned.size());
    verify(spawned.get(0)).setCustomName("§f2x Stone");
    verify(spawned.get(1)).setCustomName("§f1x Gem");
    verify(spawned.get(0)).setPickupDelay(0);
    verify(spawned.get(0)).setCustomNameVisible(true);
    ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
    verify(spawned.get(0)).setVelocity(velocity.capture());
    assertTrue(velocity.getValue().getY() >= .4 && velocity.getValue().getY() < .8);
    assertTrue(
        Math.abs(velocity.getValue().getX()) >= .01 && Math.abs(velocity.getValue().getX()) < .05);
    server.getScheduler().performTicks(300);
    verify(world, times(280))
        .spawnParticle(
            eq(Particle.CRIT), any(Location.class), eq(4), eq(.05), eq(.05), eq(.05), eq(0d));
    GatherFx.dropAtPlayer(null, List.of(plain));
    GatherFx.dropAtPlayer(player, null);
    GatherFx.dropAtPlayer(player, List.of());
    Cache.gatherBurstParticles = false;
    GatherFx.dropAtPlayer(player, List.of(plain));
    server.getScheduler().performTicks(15);
    assertEquals(3, spawned.size());
    when(spawned.get(2).isDead()).thenReturn(true);
    server.getScheduler().performTicks(2);
  }

  @Test
  void materialSoundAndLocationFallbacksAreConsistent() throws Exception {
    Class<?>[] resolve = {World.class, Location.class, Material.class};
    assertEquals(
        Material.STONE,
        ((BlockData) helper("resolveBlockData", resolve, null, null, null)).getMaterial());
    assertEquals(
        Material.DIRT,
        ((BlockData) helper("resolveBlockData", resolve, null, null, Material.DIRT)).getMaterial());
    World world = mock(World.class);
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(Material.AIR);
    when(world.getBlockAt(any(Location.class))).thenReturn(block);
    assertEquals(
        Material.STONE,
        ((BlockData) helper("resolveBlockData", resolve, world, null, null)).getMaterial());
    assertEquals(
        Material.DIRT,
        ((BlockData)
                helper(
                    "resolveBlockData", resolve, world, new Location(null, 1, 2, 3), Material.DIRT))
            .getMaterial());
    Location corner = new Location(world, 1.7, 2.8, -.1);
    assertEquals(
        new Location(world, 1, 2, -1),
        helper("normalizeCorner", new Class<?>[] {Location.class}, corner));
    assertEquals(
        Material.STONE,
        ((BlockData) helper("resolveBlockData", resolve, world, corner, null)).getMaterial());
    assertEquals(
        Material.DIRT,
        ((BlockData) helper("resolveBlockData", resolve, world, corner, Material.DIRT))
            .getMaterial());
    Class<?>[] sound = {World.class, Location.class, BlockData.class};
    BlockData dirt = Material.DIRT.createBlockData();
    assertEquals(Sound.BLOCK_GRASS_BREAK, helper("resolveBreakSound", sound, null, null, dirt));
    assertEquals(Sound.BLOCK_GRASS_BREAK, helper("resolveBreakSound", sound, world, null, dirt));
    assertEquals(Sound.BLOCK_GRASS_BREAK, helper("resolveBreakSound", sound, world, corner, dirt));
    Map<Material, Sound> cases =
        Map.of(
            Material.STONE,
            Sound.BLOCK_STONE_BREAK,
            Material.DIRT,
            Sound.BLOCK_GRASS_BREAK,
            Material.SAND,
            Sound.BLOCK_SAND_BREAK,
            Material.GRAVEL,
            Sound.BLOCK_GRAVEL_BREAK,
            Material.SNOW,
            Sound.BLOCK_SNOW_BREAK,
            Material.NETHERRACK,
            Sound.BLOCK_NETHERRACK_BREAK,
            Material.OAK_LOG,
            Sound.BLOCK_WOOD_BREAK);
    for (var e : cases.entrySet())
      assertEquals(
          e.getValue(), helper("fallbackBreakSound", new Class<?>[] {Material.class}, e.getKey()));
    assertEquals(
        Sound.BLOCK_STONE_BREAK,
        helper("fallbackBreakSound", new Class<?>[] {Material.class}, (Object) null));
    assertEquals("Item", helper("displayNameOf", new Class<?>[] {ItemStack.class}, (Object) null));
    ItemStack noMeta = mock(ItemStack.class);
    when(noMeta.getType()).thenReturn(Material.IRON_INGOT);
    assertEquals("Iron ingot", helper("displayNameOf", new Class<?>[] {ItemStack.class}, noMeta));
    Class<?>[] burst = {World.class, Location.class, Location.class, BlockData.class};
    helper("playDigBurst", burst, null, corner, corner, dirt);
    helper("playDigBurst", burst, world, null, corner, dirt);
    helper("playDigBurst", burst, world, corner, corner, null);
    Class<?>[] rewards = {World.class, Location.class, List.class};
    helper("spawnRewards", rewards, null, corner, List.of());
    helper("spawnRewards", rewards, world, null, List.of());
    helper("spawnRewards", rewards, world, corner, null);
    helper("spawnRewards", rewards, world, corner, List.of());
    helper("startCritTrail", new Class<?>[] {Entity.class, int.class}, null, 1);
    Entity invalid = mock(Entity.class);
    helper("startCritTrail", new Class<?>[] {Entity.class, int.class}, invalid, 1);
    server.getScheduler().performTicks(1);
  }

  @Test
  void horizontalKicksUseBothSignsWithoutChangingMagnitude() throws Exception {
    var rng = mock(java.util.concurrent.ThreadLocalRandom.class);
    when(rng.nextDouble(.01, .05)).thenReturn(.03);
    when(rng.nextBoolean()).thenReturn(true, false);
    Class<?>[] types = {java.util.concurrent.ThreadLocalRandom.class, double.class, double.class};
    assertEquals(.03, (double) helper("randomSigned", types, rng, .01, .05));
    assertEquals(-.03, (double) helper("randomSigned", types, rng, .01, .05));
  }

  @Test
  void equalConfiguredKickBoundsProduceFixedVelocity() throws Exception {
    Cache.gatherKickHorizontalMin = .03;
    Cache.gatherKickHorizontalMax = .03;
    Cache.gatherKickVelocityMin = .6;
    Cache.gatherKickVelocityMax = .6;
    Item item = mock(Item.class);
    helper(
        "kickUp",
        new Class<?>[] {Item.class, java.util.concurrent.ThreadLocalRandom.class},
        item,
        java.util.concurrent.ThreadLocalRandom.current());
    ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
    verify(item).setVelocity(velocity.capture());
    assertEquals(.6, velocity.getValue().getY());
    assertEquals(.03, Math.abs(velocity.getValue().getX()));
  }
}
