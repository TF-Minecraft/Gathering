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
import java.util.TreeSet;

import java.util.concurrent.ThreadLocalRandom;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.utils.Provinces;

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
        public final double spawnWeight;
        /**
         * SimpleFactions province ids this type is limited to. Null means anywhere; an empty set (a list
         * with no valid ids) means nowhere, so a mistyped list never unlocks a type everywhere.
         */
        public final Set<Integer> provinces;
        public final List<CategoryWeight> categories;

        public SpotTypeDefinition(String id, Set<Biome> biomes, Integer altitudeMin, Integer altitudeMax,
                Material spawnBlock, Material particleDust, double spawnWeight, Set<Integer> provinces,
                List<CategoryWeight> categories) {
            this.id = id;
            this.biomes = biomes;
            this.altitudeMin = altitudeMin;
            this.altitudeMax = altitudeMax;
            this.spawnBlock = spawnBlock;
            this.particleDust = particleDust;
            this.spawnWeight = Math.max(0.0, spawnWeight);
            this.provinces = provinces;
            this.categories = categories;
        }

        public boolean requiresProvince() {
            return provinces != null;
        }

        public boolean acceptsProvince(int provinceId) {
            if (provinces == null) return true;
            return provinceId != Provinces.UNKNOWN && provinces.contains(provinceId);
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

    /** Per-type keys from the pre-gathering-mode format that no longer do anything. */
    static final List<String> REMOVED_KEYS = List.of("profession-id", "max-active", "cooldown-minutes");

    public void load(File configFile) {
        clear();
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        Set<String> removed = new TreeSet<>();
        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec == null) continue;
            for (String old : REMOVED_KEYS) {
                if (sec.contains(old)) removed.add(old);
            }
            oList.put(key, parse(key, sec));
        }
        if (!removed.isEmpty()) {
            Gathering.plugin.getLogger().warning(configFile.getName() + ": ignoring spot type settings removed by"
                    + " gathering mode: " + String.join(", ", removed));
        }
    }

    private SpotTypeDefinition parse(String id, ConfigurationSection sec) {
        Set<Biome> biomes = parseBiomes(sec.getStringList("biome"));
        Integer altMin = sec.contains("altitude-min") ? sec.getInt("altitude-min") : null;
        Integer altMax = sec.contains("altitude-max") ? sec.getInt("altitude-max") : null;
        Material spawnBlock = parseMaterial(sec.getString("spawn-block"));
        Material particleDust = parseMaterial(sec.getString("particle-dust"));
        double spawnWeight = sec.getDouble("spawn-weight", 1.0);
        Set<Integer> provinces = parseProvinces(id, sec.get("provinces"));

        List<CategoryWeight> categories = new ArrayList<>();
        List<java.util.Map<?, ?>> rawCategories = sec.getMapList("categories");
        for (java.util.Map<?, ?> map : rawCategories) {
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
                spawnWeight, provinces, Collections.unmodifiableList(categories));
    }

    private Set<Integer> parseProvinces(String typeId, Object raw) {
        List<?> entries = raw instanceof List<?> list ? list : raw == null ? List.of() : List.of(raw);
        if (entries.isEmpty()) return null;
        Set<Integer> out = new HashSet<>();
        for (Object entry : entries) {
            if (entry instanceof Number n) {
                out.add(n.intValue());
                continue;
            }
            try {
                out.add(Integer.parseInt(String.valueOf(entry).trim()));
            } catch (NumberFormatException ex) {
                Gathering.plugin.getLogger().warning("Bad province id in spot type " + typeId
                        + " (use numeric ids): " + entry);
            }
        }
        if (out.isEmpty()) {
            Gathering.plugin.getLogger().warning("Spot type " + typeId + " lists no valid province ids;"
                    + " it will not spawn anywhere.");
        }
        return Collections.unmodifiableSet(out);
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
        if (names.isEmpty()) return Collections.emptySet();
        Set<Biome> out = new HashSet<>();
        for (String name : names) {
            if (name.isBlank()) continue;
            NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
            Biome biome = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
            if (biome == null) {
                Gathering.plugin.getLogger().warning("Unknown biome in spot-types: " + name);
            } else {
                out.add(biome);
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
