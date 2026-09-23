package net.tfminecraft.gathering.loader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.gathering.loot.DropCategory;

public class CategoryLoader {

    private static final HashMap<String, DropCategory> oList = new HashMap<>();

    public static void clear() {
        oList.clear();
    }

    public static HashMap<String, DropCategory> get() {
        return oList;
    }

    public static DropCategory getByString(String id) {
        return oList.get(id);
    }

    public void load(File configFile) {
        clear();
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        for (String key : config.getKeys(false)) {
            List<String> drops = config.getStringList(key + ".drops");
            if (drops == null || drops.isEmpty()) {
                drops = config.getStringList(key);
            }
            DropCategory category = new DropCategory(key, drops);
            oList.put(key, category);
        }
    }
}
