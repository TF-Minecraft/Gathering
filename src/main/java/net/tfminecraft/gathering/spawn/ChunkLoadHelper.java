package net.tfminecraft.gathering.spawn;

import java.util.function.Function;

import org.bukkit.Chunk;
import org.bukkit.World;

import net.tfminecraft.gathering.Gathering;

public final class ChunkLoadHelper {

    private ChunkLoadHelper() {}

    public static <T> T withLoadedChunk(World world, int chunkX, int chunkZ, Function<Chunk, T> callback) {
        if (world == null || callback == null) return null;

        boolean wasLoaded = world.isChunkLoaded(chunkX, chunkZ);
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        boolean wasForceLoaded = chunk.isForceLoaded();

        try {
            chunk.setForceLoaded(true);
            return callback.apply(chunk);
        } finally {
            chunk.setForceLoaded(wasForceLoaded);
            if (!wasLoaded) {
                try {
                    world.unloadChunk(chunk.getX(), chunk.getZ(), false);
                } catch (Exception ex) {
                    Gathering.plugin.getLogger().fine("Could not unload probed chunk " + chunkX + "," + chunkZ
                            + ": " + ex.getMessage());
                }
            }
        }
    }
}
