package net.tfminecraft.gathering.utils;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;

public final class GatherFx {

    private static final int DIG_BURSTS = 3;
    private static final int DIG_BURST_INTERVAL_TICKS = 4;
    private static final int SPAWN_DELAY_TICKS = DIG_BURSTS * DIG_BURST_INTERVAL_TICKS + 2;
    private static final float DIG_SOUND_VOLUME = 1.05f;

    private GatherFx() {}

    public static void playGather(Player player, Location effectLoc, Location blockCorner,
            Material surfaceMaterial, List<ItemStack> stacks) {
        if (effectLoc == null || effectLoc.getWorld() == null || stacks == null || stacks.isEmpty()
                || player == null) {
            return;
        }

        World world = effectLoc.getWorld();
        BlockData particleData = resolveBlockData(world, blockCorner, surfaceMaterial);

        for (int burst = 0; burst < DIG_BURSTS; burst++) {
            int delay = burst * DIG_BURST_INTERVAL_TICKS;
            Bukkit.getScheduler().runTaskLater(Gathering.plugin,
                    () -> playDigBurst(world, effectLoc, blockCorner, particleData), delay);
        }

        Bukkit.getScheduler().runTaskLater(Gathering.plugin,
                () -> spawnRewards(world, effectLoc, stacks), SPAWN_DELAY_TICKS);
    }

    public static void dropAtPlayer(Player player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) return;
        Location feet = player.getLocation();
        Location effectLoc = feet.clone().add(0, 1.0, 0);
        Location blockCorner = feet.clone();
        blockCorner.setX(Math.floor(blockCorner.getX()));
        blockCorner.setY(Math.floor(blockCorner.getY()));
        blockCorner.setZ(Math.floor(blockCorner.getZ()));
        playGather(player, effectLoc, blockCorner, Material.STONE, stacks);
    }

    private static void playDigBurst(World world, Location effectLoc, Location blockCorner, BlockData blockData) {
        if (world == null || effectLoc == null || blockData == null) return;

        Sound breakSound = resolveBreakSound(world, blockCorner, blockData);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        world.playSound(effectLoc, breakSound, DIG_SOUND_VOLUME, (float) rng.nextDouble(0.92, 1.08));
        world.spawnParticle(Particle.BLOCK, effectLoc, 14, 0.18, 0.08, 0.18, 0.03, blockData);
    }

    private static void spawnRewards(World world, Location effectLoc, List<ItemStack> stacks) {
        if (world == null || effectLoc == null || stacks == null || stacks.isEmpty()) return;

        world.playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);

        if (Cache.gatherBurstParticles) {
            world.spawnParticle(Particle.CLOUD, effectLoc, 18, 0.35, 0.25, 0.35, 0.0);
            world.spawnParticle(Particle.ENCHANTED_HIT, effectLoc, 12, 0.25, 0.20, 0.25, 0.0);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location pop = effectLoc.clone().add(0, 0.15, 0);
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            spawnPopItem(world, pop, stack, rng);
        }
    }

    private static void spawnPopItem(World world, Location pop, ItemStack stack, ThreadLocalRandom rng) {
        Item ent = world.dropItem(pop.clone(), stack.clone());
        ent.setPickupDelay(0);
        ent.setCustomName(entityNameFor(stack));
        ent.setCustomNameVisible(true);
        kickUp(ent, rng);
        startCritTrail(ent, 140);
    }

    private static BlockData resolveBlockData(World world, Location blockCorner, Material surfaceMaterial) {
        if (world == null) {
            Material mat = surfaceMaterial != null ? surfaceMaterial : Material.STONE;
            return mat.createBlockData();
        }
        Location corner = normalizeCorner(blockCorner);
        if (corner == null) {
            Material mat = surfaceMaterial != null ? surfaceMaterial : Material.STONE;
            return mat.createBlockData();
        }
        Block block = world.getBlockAt(corner);
        if (!block.getType().isAir()) {
            return block.getBlockData();
        }
        Material mat = surfaceMaterial != null ? surfaceMaterial : Material.STONE;
        return mat.createBlockData();
    }

    private static Sound resolveBreakSound(World world, Location blockCorner, BlockData blockData) {
        if (world == null) return fallbackBreakSound(blockData.getMaterial());
        Location corner = normalizeCorner(blockCorner);
        if (corner == null) return fallbackBreakSound(blockData.getMaterial());
        Block block = world.getBlockAt(corner);
        Material mat = !block.getType().isAir() ? block.getType() : blockData.getMaterial();
        return fallbackBreakSound(mat);
    }

    private static Location normalizeCorner(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return new Location(
                loc.getWorld(),
                Math.floor(loc.getX()),
                Math.floor(loc.getY()),
                Math.floor(loc.getZ()));
    }

    private static Sound fallbackBreakSound(Material mat) {
        if (mat == null) return Sound.BLOCK_STONE_BREAK;
        return switch (mat) {
            case GRASS_BLOCK, SHORT_GRASS, TALL_GRASS, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, MUD ->
                Sound.BLOCK_GRASS_BREAK;
            case SAND, RED_SAND, SUSPICIOUS_SAND -> Sound.BLOCK_SAND_BREAK;
            case GRAVEL -> Sound.BLOCK_GRAVEL_BREAK;
            case SNOW, SNOW_BLOCK, POWDER_SNOW -> Sound.BLOCK_SNOW_BREAK;
            case NETHERRACK -> Sound.BLOCK_NETHERRACK_BREAK;
            case OAK_WOOD, BIRCH_WOOD, SPRUCE_WOOD, JUNGLE_WOOD, ACACIA_WOOD, DARK_OAK_WOOD,
                    MANGROVE_WOOD, CHERRY_WOOD, OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, ACACIA_LOG,
                    DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG ->
                Sound.BLOCK_WOOD_BREAK;
            default -> Sound.BLOCK_STONE_BREAK;
        };
    }

    private static void kickUp(Item ent, ThreadLocalRandom rng) {
        double vx = randomSigned(rng, Cache.gatherKickHorizontalMin, Cache.gatherKickHorizontalMax);
        double vz = randomSigned(rng, Cache.gatherKickHorizontalMin, Cache.gatherKickHorizontalMax);
        double vy = rng.nextDouble(Cache.gatherKickVelocityMin, Cache.gatherKickVelocityMax);
        ent.setVelocity(new Vector(vx, vy, vz));
    }

    private static double randomSigned(ThreadLocalRandom rng, double min, double max) {
        double v = rng.nextDouble(min, max);
        return rng.nextBoolean() ? v : -v;
    }

    private static void startCritTrail(Entity entity, int maxTicks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (entity == null || !entity.isValid() || entity.isDead() || t++ >= maxTicks) {
                    cancel();
                    return;
                }
                Location p = entity.getLocation().add(0, 0.1, 0);
                p.getWorld().spawnParticle(Particle.CRIT, p, 4, 0.05, 0.05, 0.05, 0.0);
            }
        }.runTaskTimer(Gathering.plugin, 0L, 2L);
    }

    private static String entityNameFor(ItemStack item) {
        return "§f" + item.getAmount() + "x " + displayNameOf(item);
    }

    private static String displayNameOf(ItemStack item) {
        if (item == null) return "Item";
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
