package net.tfminecraft.gathering.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gathering.cache.Cache;

public class ConfigLoader {

    public static final class WorldBounds {
        public final String world;
        public final int chunkXMin;
        public final int chunkXMax;
        public final int chunkZMin;
        public final int chunkZMax;

        public WorldBounds(String world, int chunkXMin, int chunkXMax, int chunkZMin, int chunkZMax) {
            this.world = world;
            this.chunkXMin = chunkXMin;
            this.chunkXMax = chunkXMax;
            this.chunkZMin = chunkZMin;
            this.chunkZMax = chunkZMax;
        }
    }

    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        Cache.spawnIntervalMinutes = Math.max(1, config.getInt("spawn-interval-minutes", 10));
        Cache.chunkCooldownMinutes = Math.max(0, config.getInt("chunk-cooldown-minutes", 4320));
        Cache.spawnAttemptsPerTick = Math.max(1, config.getInt("spawn-attempts-per-tick", 3));
        Cache.probeColumnAttempts = Math.max(1, config.getInt("probe-column-attempts", 12));

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

        Cache.passiveDiscoveryEnabled = config.getBoolean("passive-discovery.enabled", true);
        Cache.passiveIntervalSeconds = Math.max(1, config.getInt("passive-discovery.interval-seconds", 12));
        Cache.passiveRadius = config.getDouble("passive-discovery.radius", 6.0);
        Cache.passiveBaseChance = config.getDouble("passive-discovery.base-chance", 0.06);
        Cache.passiveMessage = config.getString("passive-discovery.message",
                "&7*You notice something glinting nearby...*");

        Cache.wisdomWeight = config.getDouble("attributes.wisdom-weight", 0.02);
        Cache.intelligenceWeight = config.getDouble("attributes.intelligence-weight", 0.015);

        Cache.professionEnabled = config.getBoolean("profession.enabled", true);
        Cache.professionId = config.getString("profession.id", "herbalism");
        Cache.professionLevelWeight = config.getDouble("profession.level-weight", 0.01);

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
    }
}
