package net.tfminecraft.gathering.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.ConfigLoader;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spot.GatheringSpot;
import net.tfminecraft.gathering.utils.CharacterBridge;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.attributes.AttributeModifier;

/**
 * Spawns spots around players in gathering mode. Only chunks that are already loaded are probed, and
 * worked-over chunks are accepted less often (see {@link SpotManager#freshness}).
 */
public final class NearbySpawnService {

    /** Forced spawns skip most checks, so they get more tries at finding a surface. */
    static final int FORCE_ATTEMPT_MULTIPLIER = 4;

    private final SpotManager manager;
    private final GatheringModeService mode;
    private final Random random;
    private BukkitTask task;

    public NearbySpawnService(SpotManager manager, GatheringModeService mode) {
        this(manager, mode, new Random());
    }

    public NearbySpawnService(SpotManager manager, GatheringModeService mode, Random random) {
        this.manager = manager;
        this.mode = mode;
        this.random = random;
    }

    public void start() {
        stop();
        long ticks = Cache.spawnIntervalSeconds * 20L;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Gathering.plugin, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void tick() {
        List<Player> gatherers = new ArrayList<>(mode.activePlayers());
        Collections.shuffle(gatherers, random);
        long now = System.currentTimeMillis();
        for (Player player : gatherers) {
            trySpawnFor(player, gatherers, now);
        }
    }

    /** How many spots may be up around a gatherer who has {@code otherGatherers} others nearby. */
    public static int capFor(int otherGatherers) {
        return Math.min(Cache.capMax, Cache.capBase + Cache.capPerExtraGatherer * otherGatherers);
    }

    public static double spawnChance(RPCharacter character) {
        double chance = Cache.spawnBaseChance;
        if (character != null) {
            int wisdom = character.getAttributeData().getAmount(new AttributeModifier("wisdom", 0));
            int intelligence = character.getAttributeData().getAmount(new AttributeModifier("intelligence", 0));
            chance += wisdom * Cache.wisdomWeight + intelligence * Cache.intelligenceWeight;
        }
        return Math.max(0.0, Math.min(Cache.spawnMaxChance, chance));
    }

    public GatheringSpot trySpawnFor(Player player, List<Player> gatherers, long now) {
        Location center = player.getLocation();
        World world = center.getWorld();
        ConfigLoader.WorldBounds bounds = Cache.worldBounds.get(world.getName());
        if (bounds == null) return null;

        double radiusSq = Cache.spawnMaxRadius * Cache.spawnMaxRadius;
        int others = 0;
        for (Player other : gatherers) {
            if (other == player || other.getWorld() != world) continue;
            if (other.getLocation().distanceSquared(center) <= radiusSq) others++;
        }
        if (manager.getSpotsNear(center, Cache.spawnMaxRadius).size() >= capFor(others)) return null;
        if (random.nextDouble() >= spawnChance(CharacterBridge.getActiveCharacter(player))) return null;

        List<SpotTypeLoader.SpotTypeDefinition> types = new ArrayList<>();
        for (SpotTypeLoader.SpotTypeDefinition type : SpotTypeLoader.all()) {
            if (type.spawnWeight > 0) types.add(type);
        }
        return findAndSpawn(center, types, bounds, Cache.spawnLocationAttempts, now);
    }

    /** Spawns near the player while skipping the chance, cap, freshness and world-bounds checks. */
    public GatheringSpot forceSpawn(Player player, SpotTypeLoader.SpotTypeDefinition onlyType) {
        List<SpotTypeLoader.SpotTypeDefinition> types = onlyType != null ? List.of(onlyType) : SpotTypeLoader.all();
        return findAndSpawn(player.getLocation(), types, null,
                Cache.spawnLocationAttempts * FORCE_ATTEMPT_MULTIPLIER, System.currentTimeMillis());
    }

    /** With {@code bounds} set this is a natural spawn: bounds and chunk freshness apply. */
    private GatheringSpot findAndSpawn(Location center, List<SpotTypeLoader.SpotTypeDefinition> types,
            ConfigLoader.WorldBounds bounds, int attempts, long now) {
        if (types.isEmpty()) return null;
        World world = center.getWorld();
        double minSq = Cache.spawnMinRadius * Cache.spawnMinRadius;
        double maxSq = Cache.spawnMaxRadius * Cache.spawnMaxRadius;

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(minSq + random.nextDouble() * (maxSq - minSq));
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (bounds != null && !bounds.containsChunk(chunkX, chunkZ)) continue;
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            ChunkKey key = manager.chunkKey(world.getName(), chunkX, chunkZ);
            if (manager.hasActiveSpotInChunk(key)) continue;
            if (bounds != null && random.nextDouble() >= manager.freshness(key, now)) continue;

            SpawnPlanner.ProbeResult probe = SpawnPlanner.probeColumn(world, x, z, center.getBlockY(), types);
            if (probe == null) continue;
            Location spotCenter = probe.location.clone().add(0.5, 0.5, 0.5);
            if (!manager.getSpotsNear(spotCenter, Cache.spawnMinSpacing).isEmpty()) continue;

            SpotTypeLoader.SpotTypeDefinition type = SpawnPlanner.pickWeighted(probe.types, random.nextDouble());
            GatheringSpot spot = GatheringSpot.create(world.getName(), x, probe.location.getBlockY(), z,
                    type.id, probe.surfaceMaterial);
            manager.register(spot);
            manager.recordSpawn(key, now);
            return spot;
        }
        return null;
    }
}
