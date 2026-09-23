package net.tfminecraft.gathering.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;

public final class ItemResolver {

    private ItemResolver() {}

    public static ItemStack fromPath(String path, int amount) {
        if (path == null || path.isBlank()) return null;
        try {
            ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            if (item == null || item.getType() == Material.AIR) return null;
            item.setAmount(Math.max(1, Math.min(64, amount)));
            return item;
        } catch (Exception ex) {
            return null;
        }
    }
}
