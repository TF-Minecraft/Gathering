package net.tfminecraft.gathering;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.gathering.loader.CategoryLoader;
import net.tfminecraft.gathering.loader.ConfigLoader;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.AdminModeService;
import net.tfminecraft.gathering.manager.CommandManager;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.spot.SpotGatherHandler;

public class Gathering extends JavaPlugin {

    public static Gathering plugin;

    private final ConfigLoader configLoader = new ConfigLoader();
    private final CategoryLoader categoryLoader = new CategoryLoader();
    private final SpotTypeLoader spotTypeLoader = new SpotTypeLoader();

    private final SpotManager spotManager = new SpotManager();
    private final SpotGatherHandler spotGatherHandler = new SpotGatherHandler();
    private final CommandManager commandManager = new CommandManager();

    @Override
    public void onEnable() {
        plugin = this;

        createFolders();
        createConfigs();
        loadConfigs();
        registerListeners();

        spotManager.start();

        getCommand("gathering").setExecutor(commandManager);
        getCommand("gathering").setTabCompleter(commandManager);

        getLogger().info("Gathering enabled.");
    }

    @Override
    public void onDisable() {
        if (spotManager != null) {
            spotManager.shutdown();
        }
        getLogger().info("Gathering disabled.");
    }

    public void reloadAll() {
        loadConfigs();
        getLogger().info("Gathering configs reloaded.");
    }

    public SpotManager getSpotManager() {
        return spotManager;
    }

    // ----------------------------------------------------------------------
    //  Config Loading
    // ----------------------------------------------------------------------
    public void loadConfigs() {
        configLoader.load(new File(getDataFolder(), "config.yml"));
        categoryLoader.load(new File(getDataFolder(), "categories.yml"));
        spotTypeLoader.load(new File(getDataFolder(), "spot-types.yml"));
    }

    // ----------------------------------------------------------------------
    //  Listeners
    // ----------------------------------------------------------------------
    public void registerListeners() {
        getServer().getPluginManager().registerEvents(spotManager, this);
        getServer().getPluginManager().registerEvents(spotGatherHandler, this);
        getServer().getPluginManager().registerEvents(AdminModeService.get(), this);
    }

    // ----------------------------------------------------------------------
    //  Folders
    // ----------------------------------------------------------------------
    public void createFolders() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        File data = new File(getDataFolder(), "Data");
        if (!data.exists()) {
            data.mkdir();
        }
    }

    // ----------------------------------------------------------------------
    //  Config Generation
    // ----------------------------------------------------------------------
    public void createConfigs() {
        String[] files = {
                "config.yml",
                "categories.yml",
                "spot-types.yml"
        };
        for (String s : files) {
            File file = new File(getDataFolder(), s);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                saveResource(s, false);
            }
        }
    }
}
