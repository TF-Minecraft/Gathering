package net.tfminecraft.gathering.spawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.utils.Provinces;

public final class SpawnPlanner {

    public static final class ProbeResult {
        public final Location location;
        public final Material surfaceMaterial;
        /** Every candidate type that accepts this surface. Never empty. */
        public final List<SpotTypeLoader.SpotTypeDefinition> types;

        public ProbeResult(Location location, Material surfaceMaterial,
                List<SpotTypeLoader.SpotTypeDefinition> types) {
            this.location = location;
            this.surfaceMaterial = surfaceMaterial;
            this.types = types;
        }
    }

    private SpawnPlanner() {}

    /**
     * Scans one column from {@link Cache#spawnMaxVertical} above {@code centerY} down to the same distance
     * below it, and returns the first solid surface with two air blocks above that at least one of
     * {@code types} accepts. The province is only looked up when a type needs it.
     */
    public static ProbeResult probeColumn(World world, int x, int z, int centerY,
            Collection<SpotTypeLoader.SpotTypeDefinition> types) {
        if (world == null || types == null || types.isEmpty()) return null;
        int top = Math.min(world.getMaxHeight() - 2, centerY + Cache.spawnMaxVertical);
        int bottom = Math.max(world.getMinHeight() + 1, centerY - Cache.spawnMaxVertical);

        Integer province = null;
        for (int y = top; y >= bottom; y--) {
            Block surface = world.getBlockAt(x, y, z);
            if (!surface.getType().isSolid()) continue;
            if (!world.getBlockAt(x, y + 1, z).getType().isAir()) continue;
            if (!world.getBlockAt(x, y + 2, z).getType().isAir()) continue;

            Material surfaceMat = surface.getType();
            Biome biome = surface.getBiome();
            List<SpotTypeLoader.SpotTypeDefinition> eligible = new ArrayList<>();
            for (SpotTypeLoader.SpotTypeDefinition type : types) {
                if (!type.acceptsSpawnBlock(surfaceMat)) continue;
                if (!type.acceptsAltitude(y)) continue;
                if (!type.acceptsBiome(biome)) continue;
                if (type.requiresProvince()) {
                    if (province == null) province = Provinces.at(x, z);
                    if (!type.acceptsProvince(province)) continue;
                }
                eligible.add(type);
            }
            if (!eligible.isEmpty()) {
                return new ProbeResult(new Location(world, x, y, z), surfaceMat, eligible);
            }
        }
        return null;
    }

    /**
     * Picks a type by {@code spawnWeight} using {@code roll} in [0, 1). When no type has a positive
     * weight (a forced spawn of a weight-0 type), the first type is used.
     */
    public static SpotTypeLoader.SpotTypeDefinition pickWeighted(List<SpotTypeLoader.SpotTypeDefinition> types,
            double roll) {
        double total = 0.0;
        for (SpotTypeLoader.SpotTypeDefinition type : types) {
            total += type.spawnWeight;
        }
        if (total <= 0.0) return types.get(0);

        double r = roll * total;
        double acc = 0.0;
        for (int i = 0; i < types.size() - 1; i++) {
            acc += types.get(i).spawnWeight;
            if (r < acc) return types.get(i);
        }
        return types.get(types.size() - 1);
    }
}
