package net.tfminecraft.gathering.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gathering.Gathering;

public class SpotTypeLoader {

    public static final class CategoryWeight {
        public final String id;
        public final double weight;
        public final int dropsMin;
        public final int dropsMax;

        public CategoryWeight(String id, double weight, int dropsMin, int dropsMax) {
            this.id = id;
            this.weight = Math.max(0.0, weight);
            this.dropsMin = Math.max(1, dropsMin);
            this.dropsMax = Math.max(this.dropsMin, dropsMax);
        }

        public int rollDropCount(ThreadLocalRandom rng) {
            if (dropsMin >= dropsMax) return dropsMin;
            return rng.nextInt(dropsMin, dropsMax + 1);
        }
    }

    public static final class SpotTypeDefinition {
        public final String id;
        public final Set<Biome> biomes;
        public final Integer altitudeMin;
        public final Integer altitudeMax;
        public final Material spawnBlock;
        public final Material particleDust;
        public final int maxActive;
        public final int cooldownMinutes;
        public final String professionId;
        public final List<CategoryWeight> categories;

        public SpotTypeDefinition(String id, Set<Biome> biomes, Integer altitudeMin, Integer altitudeMax,
                Material spawnBlock, Material particleDust, int maxActive, int cooldownMinutes,
                String professionId, List<CategoryWeight> categories) {
            this.id = id;
            this.biomes = biomes;
            this.altitudeMin = altitudeMin;
            this.altitudeMax = altitudeMax;
            this.spawnBlock = spawnBlock;
            this.particleDust = particleDust;
            this.maxActive = maxActive;
            this.cooldownMinutes = cooldownMinutes;
            this.professionId = professionId;
            this.categories = categories;
        }

        public boolean acceptsBiome(Biome biome) {
            return biomes == null || biomes.isEmpty() || biomes.contains(biome);
        }

        public boolean acceptsAltitude(int y) {
            if (altitudeMin != null && y < altitudeMin) return false;
            if (altitudeMax != null && y > altitudeMax) return false;
            return true;
        }

        public boolean acceptsSpawnBlock(Material material) {
            return spawnBlock == null || spawnBlock == material;
        }

        public Material resolveParticleMaterial(Material spawnSurface) {
            return particleDust != null ? particleDust : spawnSurface;
        }
    }

    private static final HashMap<String, SpotTypeDefinition> oList = new HashMap<>();

    public static void clear() {
        oList.clear();
    }

    public static HashMap<String, SpotTypeDefinition> get() {
        return oList;
    }

    public static SpotTypeDefinition getByString(String id) {
        return oList.get(id);
    }

    public static List<SpotTypeDefinition> all() {
        return new ArrayList<>(oList.values());
    }

    public void load(File configFile) {
        clear();
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec == null) continue;
            oList.put(key, parse(key, sec));
        }
    }

    private SpotTypeDefinition parse(String id, ConfigurationSection sec) {
        Set<Biome> biomes = parseBiomes(sec.getStringList("biome"));
        Integer altMin = sec.contains("altitude-min") ? sec.getInt("altitude-min") : null;
        Integer altMax = sec.contains("altitude-max") ? sec.getInt("altitude-max") : null;
        Material spawnBlock = parseMaterial(sec.getString("spawn-block"));
        Material particleDust = parseMaterial(sec.getString("particle-dust"));
        int maxActive = Math.max(1, sec.getInt("max-active", 3));
        int cooldownMinutes = Math.max(0, sec.getInt("cooldown-minutes", 10));
        String professionId = sec.getString("profession-id");

        List<CategoryWeight> categories = new ArrayList<>();
        List<?> rawCategories = sec.getMapList("categories");
        for (Object raw : rawCategories) {
            if (!(raw instanceof java.util.Map<?, ?> map)) continue;
            Object catId = map.get("id");
            Object weight = map.get("weight");
            Object drops = map.get("drops");
            if (catId == null) continue;
            double w = 1.0;
            if (weight instanceof Number n) {
                w = n.doubleValue();
            } else if (weight != null) {
                try {
                    w = Double.parseDouble(weight.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            int[] dropRange = parseDropRange(drops != null ? drops.toString() : null);
            categories.add(new CategoryWeight(catId.toString(), w, dropRange[0], dropRange[1]));
        }

        return new SpotTypeDefinition(id, biomes, altMin, altMax, spawnBlock, particleDust,
                maxActive, cooldownMinutes, professionId, Collections.unmodifiableList(categories));
    }

    private int[] parseDropRange(String raw) {
        int min = 1;
        int max = 1;
        if (raw == null || raw.isBlank()) return new int[]{min, max};

        String s = raw.trim();
        int dash = s.indexOf('-');
        try {
            if (dash > 0) {
                min = Integer.parseInt(s.substring(0, dash).trim());
                max = Integer.parseInt(s.substring(dash + 1).trim());
            } else {
                min = max = Integer.parseInt(s);
            }
        } catch (NumberFormatException ex) {
            Gathering.plugin.getLogger().warning("Bad drops range in spot-types (use min-max or N): " + raw);
            min = 1;
            max = 1;
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        return new int[]{min, max};
    }

    private Set<Biome> parseBiomes(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptySet();
        Set<Biome> out = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            try {
                out.add(Biome.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                Gathering.plugin.getLogger().warning("Unknown biome in spot-types: " + name);
            }
        }
        return out;
    }

    private Material parseMaterial(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Gathering.plugin.getLogger().warning("Unknown material in spot-types: " + name);
            return null;
        }
    }
}
