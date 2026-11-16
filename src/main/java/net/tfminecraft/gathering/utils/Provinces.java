package net.tfminecraft.gathering.utils;

import org.bukkit.Bukkit;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.map.ProvinceGrid;

/**
 * Province lookups through SimpleFactions, which is a soft dependency. SimpleFactions classes are only
 * touched once the plugin is known to be enabled, so Gathering still loads without it.
 */
public final class Provinces {

    /** Returned when no province can be determined (SimpleFactions missing, grid not loaded, ...). */
    public static final int UNKNOWN = -2;

    private Provinces() {}

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("SimpleFactions") && grid() != null;
    }

    public static int at(int blockX, int blockZ) {
        if (!Bukkit.getPluginManager().isPluginEnabled("SimpleFactions")) return UNKNOWN;
        ProvinceGrid grid = grid();
        return grid == null ? UNKNOWN : grid.getAt(blockX, blockZ);
    }

    private static ProvinceGrid grid() {
        SimpleFactions sf = SimpleFactions.getInstance();
        return sf == null ? null : sf.getProvinceGrid();
    }
}
