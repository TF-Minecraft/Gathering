package net.tfminecraft.gathering.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;

public class ConfigLoader {

    /** Top-level keys from the pre-gathering-mode config that no longer do anything. */
    static final List<String> REMOVED_KEYS = List.of(
            "spawn-interval-minutes", "chunk-cooldown-minutes", "spawn-attempts-per-tick",
            "probe-column-attempts", "passive-discovery", "attributes", "profession");

    public static final class WorldBounds {
        public final String world;
        public final int chunkXMin;
        public final int chunkXMax;
        public final int chunkZMin;
        public final int chunkZMax;

        public WorldBounds(String world, int chunkXMin, int chunkXMax, int chunkZMin, int chunkZMax) {
            this.world = world;
            this.chunkXMin = Math.min(chunkXMin, chunkXMax);
            this.chunkXMax = Math.max(chunkXMin, chunkXMax);
            this.chunkZMin = Math.min(chunkZMin, chunkZMax);
            this.chunkZMax = Math.max(chunkZMin, chunkZMax);
        }

        public boolean containsChunk(int chunkX, int chunkZ) {
            return chunkX >= chunkXMin && chunkX <= chunkXMax && chunkZ >= chunkZMin && chunkZ <= chunkZMax;
        }
    }

    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        Map<String, WorldBounds> bounds = new HashMap<>();
        if (config.isConfigurationSection("worlds")) {
            for (String worldKey : config.getConfigurationSection("worlds").getKeys(false)) {
                String path = "worlds." + worldKey + ".";
                int cxMin = config.getInt(path + "chunk-x-min", 0);
                int cxMax = config.getInt(path + "chunk-x-max", 400);
                int czMin = config.getInt(path + "chunk-z-min", 0);
                int czMax = config.getInt(path + "chunk-z-max", 400);
                bounds.put(worldKey, new WorldBounds(worldKey, cxMin, cxMax, czMin, czMax));
            }
        }
        Cache.worldBounds = bounds;

        Cache.drainAmount = Math.max(0, config.getInt("mode.drain-amount", 1));
        Cache.drainIntervalSeconds = Math.max(1, config.getInt("mode.drain-interval-seconds", 90));
        Cache.actionbarFormat = config.getString("mode.actionbar", "&7Gathering &8· &e{mp} MP");
        Cache.messageEnter = config.getString("mode.messages.enter",
                "&7You begin searching your surroundings carefully.");
        Cache.messageLeave = config.getString("mode.messages.leave", "&7You stop searching.");
        Cache.messageOutOfFocus = config.getString("mode.messages.out-of-focus",
                "&7Your focus fades; you stop searching.");
        Cache.messageNoFocus = config.getString("mode.messages.no-focus", "&cYou are too unfocused to search.");
        Cache.messageNoCharacter = config.getString("mode.messages.no-character",
                "&cYou need an active character to gather.");

        Cache.spawnIntervalSeconds = Math.max(1, config.getInt("spawn.interval-seconds", 20));
        Cache.spawnMinRadius = Math.max(0.0, config.getDouble("spawn.min-radius", 8.0));
        Cache.spawnMaxRadius = Math.max(Cache.spawnMinRadius, config.getDouble("spawn.max-radius", 32.0));
        Cache.spawnMaxVertical = Math.max(0, config.getInt("spawn.max-vertical", 16));
        Cache.spawnMinSpacing = Math.max(0.0, config.getDouble("spawn.min-spacing", 6.0));
        Cache.spawnLocationAttempts = Math.max(1, config.getInt("spawn.location-attempts", 8));
        Cache.capBase = Math.max(0, config.getInt("spawn.cap-base", 3));
        Cache.capPerExtraGatherer = Math.max(0, config.getInt("spawn.cap-per-extra-gatherer", 1));
        Cache.capMax = Math.max(0, config.getInt("spawn.cap-max", 6));
        Cache.spawnBaseChance = config.getDouble("spawn.base-chance", 0.30);
        Cache.spawnMaxChance = Math.min(1.0, config.getDouble("spawn.max-chance", 0.85));
        Cache.wisdomWeight = config.getDouble("spawn.wisdom-weight", 0.010);
        Cache.intelligenceWeight = config.getDouble("spawn.intelligence-weight", 0.0075);

        Cache.spotLifetimeMinutes = Math.max(1, config.getInt("spot.lifetime-minutes", 15));
        Cache.spotKeepaliveRadius = Math.max(1.0, config.getDouble("spot.keepalive-radius", 48.0));
        Cache.spotOrphanGraceSeconds = Math.max(0, config.getInt("spot.orphan-grace-seconds", 60));

        Cache.freshnessPerHarvest = Math.max(0.0, config.getDouble("freshness.per-harvest", 1.0));
        Cache.freshnessPerSpawn = Math.max(0.0, config.getDouble("freshness.per-spawn", 0.0));
        Cache.freshnessHalfLifeHours = Math.max(0.0, config.getDouble("freshness.half-life-hours", 24.0));
        Cache.freshnessStrength = Math.max(0.0, config.getDouble("freshness.strength", 1.0));

        Cache.particleIntervalTicks = Math.max(1, config.getInt("particles.interval-ticks", 10));
        Cache.particleRingRadius = config.getDouble("particles.ring-radius", 0.45);

        Cache.gatherBurstParticles = config.getBoolean("gather-fx.burst-particles", false);
        Cache.gatherKickVelocityMin = config.getDouble("gather-fx.kick-velocity-min", 0.42);
        Cache.gatherKickVelocityMax = config.getDouble("gather-fx.kick-velocity-max", 0.78);
        Cache.gatherKickHorizontalMin = config.getDouble("gather-fx.kick-horizontal-min", 0.015);
        Cache.gatherKickHorizontalMax = config.getDouble("gather-fx.kick-horizontal-max", 0.045);
        if (Cache.gatherKickVelocityMax < Cache.gatherKickVelocityMin) {
            double t = Cache.gatherKickVelocityMin;
            Cache.gatherKickVelocityMin = Cache.gatherKickVelocityMax;
            Cache.gatherKickVelocityMax = t;
        }
        if (Cache.gatherKickHorizontalMax < Cache.gatherKickHorizontalMin) {
            double t = Cache.gatherKickHorizontalMin;
            Cache.gatherKickHorizontalMin = Cache.gatherKickHorizontalMax;
            Cache.gatherKickHorizontalMax = t;
        }

        List<String> removed = new ArrayList<>();
        for (String key : REMOVED_KEYS) {
            if (config.contains(key)) removed.add(key);
        }
        if (!removed.isEmpty()) {
            Gathering.plugin.getLogger().warning(configFile.getName() + ": ignoring settings removed by"
                    + " gathering mode: " + String.join(", ", removed));
        }
    }
}
