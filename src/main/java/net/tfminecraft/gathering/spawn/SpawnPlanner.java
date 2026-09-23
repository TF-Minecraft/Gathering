package net.tfminecraft.gathering.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;

public final class SpawnPlanner {

    public static final class ProbeResult {
        public final Location location;
        public final Material surfaceMaterial;

        public ProbeResult(Location location, Material surfaceMaterial) {
            this.location = location;
            this.surfaceMaterial = surfaceMaterial;
        }
    }

    private SpawnPlanner() {}

    public static ProbeResult tryFindLocation(Chunk chunk, SpotTypeLoader.SpotTypeDefinition type) {
        if (chunk == null || type == null) return null;
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        List<int[]> columns = new ArrayList<>();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                columns.add(new int[]{baseX + dx, baseZ + dz});
            }
        }
        Collections.shuffle(columns, ThreadLocalRandom.current());

        int attempts = Math.min(Cache.probeColumnAttempts, columns.size());
        for (int i = 0; i < attempts; i++) {
            int[] col = columns.get(i);
            ProbeResult result = probeColumn(world, col[0], col[1], type);
            if (result != null) return result;
        }
        return null;
    }

    private static ProbeResult probeColumn(World world, int x, int z, SpotTypeLoader.SpotTypeDefinition type) {
        int maxY = world.getMaxHeight() - 2;
        int minY = world.getMinHeight() + 1;

        for (int y = maxY; y >= minY; y--) {
            Block surface = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (!surface.getType().isSolid()) continue;
            if (!above.getType().isAir() || !above2.getType().isAir()) continue;

            Material surfaceMat = surface.getType();
            if (surfaceMat == Material.WATER || surfaceMat == Material.LAVA) continue;
            if (!type.acceptsSpawnBlock(surfaceMat)) continue;
            if (!type.acceptsAltitude(y)) continue;

            org.bukkit.block.Biome biome = surface.getBiome();
            if (!type.acceptsBiome(biome)) continue;

            Location loc = new Location(world, x, y, z);
            return new ProbeResult(loc, surfaceMat);
        }
        return null;
    }
}
