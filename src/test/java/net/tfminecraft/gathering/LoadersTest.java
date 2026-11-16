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
    assertTrue(Cache.worldBounds.isEmpty());
    assertEquals(1, Cache.drainAmount);
    assertEquals(90, Cache.drainIntervalSeconds);
    assertEquals("&7Gathering &8· &e{mp} MP", Cache.statusBarFormat);
    assertEquals(20, Cache.spawnIntervalSeconds);
    assertEquals(8, Cache.spawnMinRadius);
    assertEquals(32, Cache.spawnMaxRadius);
    assertEquals(16, Cache.spawnMaxVertical);
    assertEquals(6, Cache.spawnMinSpacing);
    assertEquals(8, Cache.spawnLocationAttempts);
    assertEquals(3, Cache.capBase);
    assertEquals(1, Cache.capPerExtraGatherer);
    assertEquals(6, Cache.capMax);
    assertEquals(.30, Cache.spawnBaseChance);
    assertEquals(.85, Cache.spawnMaxChance);
    assertEquals(.010, Cache.wisdomWeight);
    assertEquals(.0075, Cache.intelligenceWeight);
    assertEquals(15, Cache.spotLifetimeMinutes);
    assertEquals(48, Cache.spotKeepaliveRadius);
    assertEquals(60, Cache.spotOrphanGraceSeconds);
    assertEquals(1, Cache.freshnessPerHarvest);
    assertEquals(0, Cache.freshnessPerSpawn);
    assertEquals(24, Cache.freshnessHalfLifeHours);
    assertEquals(1, Cache.freshnessStrength);
    loader.load(
        yaml(
                "config.yml",
                """
                spawn-interval-minutes: 0
                chunk-cooldown-minutes: -1
                profession:
                  enabled: false
                worlds:
                  world:
                    chunk-x-min: 8
                    chunk-x-max: -5
                    chunk-z-min: -7
                    chunk-z-max: 9
                mode:
                  drain-amount: -3
                  drain-interval-seconds: 0
                  status-bar: ''
                  messages:
                    enter: in
                    leave: out
                    out-of-focus: tired
                    no-focus: none
                    no-character: who
                spawn:
                  interval-seconds: 0
                  min-radius: -4
                  max-radius: -8
                  max-vertical: -1
                  min-spacing: -2
                  location-attempts: 0
                  cap-base: -1
                  cap-per-extra-gatherer: -1
                  cap-max: -1
                  base-chance: 0.5
                  max-chance: 4
                  wisdom-weight: 0.3
                  intelligence-weight: 0.2
                spot:
                  lifetime-minutes: 0
                  keepalive-radius: 0
                  orphan-grace-seconds: -5
                freshness:
                  per-harvest: -1
                  per-spawn: -1
                  half-life-hours: -1
                  strength: -1
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
    var bounds = Cache.worldBounds.get("world");
    assertEquals("world", bounds.world);
    assertEquals(-5, bounds.chunkXMin);
    assertEquals(8, bounds.chunkXMax);
    assertEquals(-7, bounds.chunkZMin);
    assertEquals(9, bounds.chunkZMax);
    assertTrue(bounds.containsChunk(-5, 9));
    assertTrue(bounds.containsChunk(8, -7));
    assertFalse(bounds.containsChunk(-6, 0));
    assertFalse(bounds.containsChunk(9, 0));
    assertFalse(bounds.containsChunk(0, -8));
    assertFalse(bounds.containsChunk(0, 10));
    assertEquals(0, Cache.drainAmount);
    assertEquals(1, Cache.drainIntervalSeconds);
    assertEquals("", Cache.statusBarFormat);
    assertEquals("in", Cache.messageEnter);
    assertEquals("out", Cache.messageLeave);
    assertEquals("tired", Cache.messageOutOfFocus);
    assertEquals("none", Cache.messageNoFocus);
    assertEquals("who", Cache.messageNoCharacter);
    assertEquals(1, Cache.spawnIntervalSeconds);
    assertEquals(0, Cache.spawnMinRadius);
    assertEquals(0, Cache.spawnMaxRadius);
    assertEquals(0, Cache.spawnMaxVertical);
    assertEquals(0, Cache.spawnMinSpacing);
    assertEquals(1, Cache.spawnLocationAttempts);
    assertEquals(0, Cache.capBase);
    assertEquals(0, Cache.capPerExtraGatherer);
    assertEquals(0, Cache.capMax);
    assertEquals(.5, Cache.spawnBaseChance);
    assertEquals(1, Cache.spawnMaxChance);
    assertEquals(.3, Cache.wisdomWeight);
    assertEquals(.2, Cache.intelligenceWeight);
    assertEquals(1, Cache.spotLifetimeMinutes);
    assertEquals(1, Cache.spotKeepaliveRadius);
    assertEquals(0, Cache.spotOrphanGraceSeconds);
    assertEquals(0, Cache.freshnessPerHarvest);
    assertEquals(0, Cache.freshnessPerSpawn);
    assertEquals(0, Cache.freshnessHalfLifeHours);
    assertEquals(0, Cache.freshnessStrength);
    assertEquals(1, Cache.particleIntervalTicks);
    assertEquals(.7, Cache.particleRingRadius);
    assertTrue(Cache.gatherBurstParticles);
    assertEquals(1, Cache.gatherKickVelocityMin);
    assertEquals(2, Cache.gatherKickVelocityMax);
    assertEquals(.2, Cache.gatherKickHorizontalMin);
    assertEquals(.4, Cache.gatherKickHorizontalMax);
    loader.load(yaml("radius.yml", "spawn:\n  min-radius: 10\n  max-radius: 4\n").toFile());
    assertEquals(10, Cache.spawnMinRadius);
    assertEquals(10, Cache.spawnMaxRadius);
    loader.load(temp.resolve("missing.yml").toFile());
    assertEquals(20, Cache.spawnIntervalSeconds);
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
                  spawn-weight: 4
                  provinces: [12, '47', ' 9 ', bad]
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
                  spawn-weight: -1
                  provinces: [nope]
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
    assertEquals(4, herb.spawnWeight);
    assertEquals(Set.of(12, 47, 9), herb.provinces);
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
    assertEquals(0, bad.spawnWeight);
    assertEquals(Set.of(), bad.provinces);
    assertFalse(bad.acceptsProvince(12));
    assertEquals(5, bad.categories.get(0).dropsMax);
    assertEquals(1, bad.categories.get(1).dropsMin);
    loader.load(
        yaml(
                "biome-inputs.yml",
                "missing: {}\nempty:\n  biome: []\n  provinces: []\nnulls:\n  biome: [null, plains]\n"
                    + "single:\n  provinces: 5\n")
            .toFile());
    assertEquals(Set.of(), SpotTypeLoader.getByString("missing").biomes);
    assertEquals(Set.of(), SpotTypeLoader.getByString("empty").biomes);
    assertEquals(Set.of(Biome.PLAINS), SpotTypeLoader.getByString("nulls").biomes);
    assertEquals(1, SpotTypeLoader.getByString("missing").spawnWeight);
    assertNull(SpotTypeLoader.getByString("missing").provinces);
    assertNull(SpotTypeLoader.getByString("empty").provinces);
    assertEquals(Set.of(5), SpotTypeLoader.getByString("single").provinces);
    loader.load(temp.resolve("missing.yml").toFile());
    assertTrue(SpotTypeLoader.all().isEmpty());
    loader.load(yaml("badtypes.yml", "key: [unterminated").toFile());
    assertTrue(SpotTypeLoader.all().isEmpty());
  }
}
