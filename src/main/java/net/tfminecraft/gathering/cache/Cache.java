package net.tfminecraft.gathering.cache;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.gathering.loader.ConfigLoader;

public final class Cache {

    private Cache() {}

    public static Map<String, ConfigLoader.WorldBounds> worldBounds = new HashMap<>();

    // Gathering mode
    public static int drainAmount = 1;
    public static int drainIntervalSeconds = 90;
    public static String actionbarFormat = "&7Gathering &8· &e{mp} MP";
    public static String messageEnter = "&7You begin searching your surroundings carefully.";
    public static String messageLeave = "&7You stop searching.";
    public static String messageOutOfFocus = "&7Your focus fades; you stop searching.";
    public static String messageNoFocus = "&cYou are too unfocused to search.";
    public static String messageNoCharacter = "&cYou need an active character to gather.";

    // Spawning around gatherers
    public static int spawnIntervalSeconds = 20;
    public static double spawnMinRadius = 8.0;
    public static double spawnMaxRadius = 32.0;
    public static int spawnMaxVertical = 16;
    public static double spawnMinSpacing = 6.0;
    public static int spawnLocationAttempts = 8;
    public static int capBase = 3;
    public static int capPerExtraGatherer = 1;
    public static int capMax = 6;
    public static double spawnBaseChance = 0.30;
    public static double spawnMaxChance = 0.85;
    public static double wisdomWeight = 0.010;
    public static double intelligenceWeight = 0.0075;

    // Spot lifetime
    public static int spotLifetimeMinutes = 15;
    public static double spotKeepaliveRadius = 48.0;
    public static int spotOrphanGraceSeconds = 60;

    // Chunk freshness
    public static double freshnessPerHarvest = 1.0;
    public static double freshnessPerSpawn = 0.0;
    public static double freshnessHalfLifeHours = 24.0;
    public static double freshnessStrength = 1.0;

    public static int particleIntervalTicks = 10;
    public static double particleRingRadius = 0.45;

    public static boolean gatherBurstParticles = false;
    public static double gatherKickVelocityMin = 0.42;
    public static double gatherKickVelocityMax = 0.78;
    public static double gatherKickHorizontalMin = 0.015;
    public static double gatherKickHorizontalMax = 0.045;
}
