package net.tfminecraft.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.*;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

class LoadersTest extends GatheringTestSupport {
  Path yaml(String name, String text) throws Exception {
    return Files.writeString(temp.resolve(name), text);
  }

  @Test
  void configDefaultsClampsBoundsAndReversedEffects() throws Exception {
    ConfigLoader loader = new ConfigLoader();
    loader.load(yaml("empty.yml", "{}").toFile());
    assertEquals(10, Cache.spawnIntervalMinutes);
    assertEquals(4320, Cache.chunkCooldownMinutes);
    assertTrue(Cache.worldBounds.isEmpty());
    assertTrue(Cache.passiveDiscoveryEnabled);
    assertEquals("herbalism", Cache.professionId);
    loader.load(
        yaml(
                "config.yml",
                """
                spawn-interval-minutes: 0
                chunk-cooldown-minutes: -1
                spawn-attempts-per-tick: 0
                probe-column-attempts: 0
                worlds:
                  world:
                    chunk-x-min: -5
                    chunk-x-max: 8
                    chunk-z-min: -7
                    chunk-z-max: 9
                passive-discovery:
                  enabled: false
                  interval-seconds: 0
                  radius: 12
                  base-chance: 0.4
                  message: hello
                attributes:
                  wisdom-weight: 0.3
                  intelligence-weight: 0.2
                profession:
                  enabled: false
                  id: mining
                  level-weight: 0.5
                particles:
                  interval-ticks: 0
                  ring-radius: 0.7
                gather-fx:
                  burst-particles: true
                  kick-velocity-min: 2
                  kick-velocity-max: 1
                  kick-horizontal-min: 0.4
                  kick-horizontal-max: 0.2
                """)
            .toFile());
    assertEquals(1, Cache.spawnIntervalMinutes);
    assertEquals(0, Cache.chunkCooldownMinutes);
    assertEquals(1, Cache.spawnAttemptsPerTick);
    assertEquals(1, Cache.probeColumnAttempts);
    var bounds = Cache.worldBounds.get("world");
    assertEquals("world", bounds.world);
    assertEquals(-5, bounds.chunkXMin);
    assertEquals(8, bounds.chunkXMax);
    assertEquals(-7, bounds.chunkZMin);
    assertEquals(9, bounds.chunkZMax);
    assertFalse(Cache.passiveDiscoveryEnabled);
    assertEquals(1, Cache.passiveIntervalSeconds);
    assertEquals(12, Cache.passiveRadius);
    assertEquals(.4, Cache.passiveBaseChance);
    assertEquals("hello", Cache.passiveMessage);
    assertEquals(.3, Cache.wisdomWeight);
    assertEquals(.2, Cache.intelligenceWeight);
    assertFalse(Cache.professionEnabled);
    assertEquals("mining", Cache.professionId);
    assertEquals(.5, Cache.professionLevelWeight);
    assertEquals(1, Cache.particleIntervalTicks);
    assertEquals(.7, Cache.particleRingRadius);
    assertTrue(Cache.gatherBurstParticles);
    assertEquals(1, Cache.gatherKickVelocityMin);
    assertEquals(2, Cache.gatherKickVelocityMax);
    assertEquals(.2, Cache.gatherKickHorizontalMin);
    assertEquals(.4, Cache.gatherKickHorizontalMax);
    loader.load(temp.resolve("missing.yml").toFile());
    assertEquals(10, Cache.spawnIntervalMinutes);
    loader.load(yaml("invalid.yml", "key: [unterminated").toFile());
    assertTrue(Cache.worldBounds.isEmpty());
  }

  @Test
  void categoriesSupportNestedAndLegacyListsAndReplaceOnReload() throws Exception {
    CategoryLoader loader = new CategoryLoader();
    loader.load(
        yaml(
                "categories.yml",
                """
                herbs:
                  drops: ["STONE 1-2 1"]
                legacy: ["DIRT 1-1 2"]
                empty: []
                """)
            .toFile());
    assertEquals(3, CategoryLoader.get().size());
    assertEquals("STONE", CategoryLoader.getByString("herbs").getEntries().getFirst().type);
    assertEquals("DIRT", CategoryLoader.getByString("legacy").getEntries().getFirst().type);
    assertTrue(CategoryLoader.getByString("empty").isEmpty());
    loader.load(temp.resolve("missing.yml").toFile());
    assertTrue(CategoryLoader.get().isEmpty());
    loader.load(yaml("badcategories.yml", "key: [unterminated").toFile());
    assertTrue(CategoryLoader.get().isEmpty());
    CategoryLoader.clear();
    assertNull(CategoryLoader.getByString("herbs"));
  }

  @Test
  void spotTypesParseFiltersAndFlexibleCategoryRanges() throws Exception {
    SpotTypeLoader loader = new SpotTypeLoader();
    loader.load(
        yaml(
                "types.yml",
                """
                ignored: value
                defaults: {}
                herb:
                  biome: [PLAINS, minecraft:desert, ' ', 'bad:unknown', 'bad name']
                  altitude-min: 10
                  altitude-max: 90
                  spawn-block: stone
                  particle-dust: dirt
                  max-active: 0
                  cooldown-minutes: -3
                  profession-id: herbalism
                  categories:
                    - {id: first, weight: 2, drops: '2-4'}
                    - {id: second, weight: '0.4', drops: '3'}
                    - {id: invalid, weight: 'bad', drops: 'bad'}
                    - {id: default}
                    - {weight: 2}
                bad:
                  spawn-block: unknown
                  particle-dust: ' '
                  categories:
                    - {id: reversed, drops: '5-2'}
                    - {id: negative, drops: '-1'}
                    - {id: blank, drops: ' '}
                """)
            .toFile());
    assertEquals(3, SpotTypeLoader.all().size());
    assertEquals(3, SpotTypeLoader.get().size());
    assertNull(SpotTypeLoader.getByString("ignored"));
    var herb = SpotTypeLoader.getByString("herb");
    assertEquals(Set.of(Biome.PLAINS, Biome.DESERT), herb.biomes);
    assertEquals(10, herb.altitudeMin);
    assertEquals(90, herb.altitudeMax);
    assertEquals(Material.STONE, herb.spawnBlock);
    assertEquals(Material.DIRT, herb.particleDust);
    assertEquals(1, herb.maxActive);
    assertEquals(0, herb.cooldownMinutes);
    assertEquals("herbalism", herb.professionId);
    assertEquals(4, herb.categories.size());
    assertEquals(2, herb.categories.get(0).dropsMin);
    assertEquals(4, herb.categories.get(0).dropsMax);
    assertEquals(.4, herb.categories.get(1).weight);
    assertEquals(3, herb.categories.get(1).dropsMax);
    assertEquals(1, herb.categories.get(2).weight);
    assertEquals(1, herb.categories.get(2).dropsMax);
    assertThrows(UnsupportedOperationException.class, () -> herb.categories.clear());
    var bad = SpotTypeLoader.getByString("bad");
    assertNull(bad.spawnBlock);
    assertNull(bad.particleDust);
    assertEquals(5, bad.categories.get(0).dropsMax);
    assertEquals(1, bad.categories.get(1).dropsMin);
    loader.load(
        yaml(
                "biome-inputs.yml",
                "missing: {}\nempty:\n  biome: []\nnulls:\n  biome: [null, plains]\n")
            .toFile());
    assertEquals(Set.of(), SpotTypeLoader.getByString("missing").biomes);
    assertEquals(Set.of(), SpotTypeLoader.getByString("empty").biomes);
    assertEquals(Set.of(Biome.PLAINS), SpotTypeLoader.getByString("nulls").biomes);
    loader.load(temp.resolve("missing.yml").toFile());
    assertTrue(SpotTypeLoader.all().isEmpty());
    loader.load(yaml("badtypes.yml", "key: [unterminated").toFile());
    assertTrue(SpotTypeLoader.all().isEmpty());
  }
}
