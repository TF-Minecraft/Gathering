package net.tfminecraft.gathering.spot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;

public final class SpotParticles {

    private static double angle = 0.0;

    private SpotParticles() {}

    public static void tickRing(Player viewer, GatheringSpot spot, SpotTypeLoader.SpotTypeDefinition type,
            boolean adminMode) {
        if (viewer == null || spot == null) return;
        Location center = spot.getParticleLocation();
        if (center == null || center.getWorld() == null) return;
        if (!spot.isChunkLoaded()) return;

        Material dustMat = type != null
                ? type.resolveParticleMaterial(spot.getSpawnBlockMaterial())
                : spot.getSpawnBlockMaterial();
        if (dustMat == null) dustMat = Material.STONE;

        BlockData blockData = dustMat.createBlockData();
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();
        double radius = Cache.particleRingRadius;

        double xOffA = Math.cos(angle) * radius;
        double zOffA = Math.sin(angle) * radius;
        double xOffB = -xOffA;
        double zOffB = -zOffA;

        viewer.spawnParticle(Particle.BLOCK, cx + xOffA, cy, cz + zOffA, 1, 0, 0, 0, 0, blockData);
        viewer.spawnParticle(Particle.BLOCK, cx + xOffB, cy + 0.25, cz + zOffB, 1, 0, 0, 0, 0, blockData);

        if (adminMode) {
            viewer.spawnParticle(Particle.END_ROD, cx, cy + 0.5, cz, 2, 0.05, 0.1, 0.05, 0.0);
        }

        angle += Math.PI / 16.0;
        if (angle >= Math.PI * 2) angle -= Math.PI * 2;
    }
}
