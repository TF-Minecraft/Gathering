package net.tfminecraft.gathering.cache;

import java.util.HashMap;
import java.util.Map;

import net.tfminecraft.gathering.loader.ConfigLoader;

public final class Cache {

    private Cache() {}

    public static int spawnIntervalMinutes = 10;
    public static int chunkCooldownMinutes = 4320;
    public static int spawnAttemptsPerTick = 3;
    public static int probeColumnAttempts = 12;

    public static Map<String, ConfigLoader.WorldBounds> worldBounds = new HashMap<>();

    public static boolean passiveDiscoveryEnabled = true;
    public static int passiveIntervalSeconds = 12;
    public static double passiveRadius = 6.0;
    public static double passiveBaseChance = 0.06;
    public static String passiveMessage = "&7*You notice something glinting nearby...*";

    public static double wisdomWeight = 0.02;
    public static double intelligenceWeight = 0.015;

    public static boolean professionEnabled = true;
    public static String professionId = "herbalism";
    public static double professionLevelWeight = 0.01;

    public static int particleIntervalTicks = 10;
    public static double particleRingRadius = 0.45;

    public static boolean gatherBurstParticles = false;
    public static double gatherKickVelocityMin = 0.42;
    public static double gatherKickVelocityMax = 0.78;
    public static double gatherKickHorizontalMin = 0.015;
    public static double gatherKickHorizontalMax = 0.045;
}
