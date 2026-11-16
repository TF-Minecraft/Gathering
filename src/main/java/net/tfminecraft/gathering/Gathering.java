package net.tfminecraft.gathering;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.gathering.loader.CategoryLoader;
import net.tfminecraft.gathering.loader.ConfigLoader;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.AdminModeService;
import net.tfminecraft.gathering.manager.CommandManager;
import net.tfminecraft.gathering.manager.GatherCommand;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spawn.NearbySpawnService;
import net.tfminecraft.gathering.spot.SpotGatherHandler;
import net.tfminecraft.gathering.utils.Provinces;

public class Gathering extends JavaPlugin {

    public static Gathering plugin;

    private final ConfigLoader configLoader = new ConfigLoader();
    private final CategoryLoader categoryLoader = new CategoryLoader();
    private final SpotTypeLoader spotTypeLoader = new SpotTypeLoader();

    private final GatheringModeService modeService = new GatheringModeService();
    private final SpotManager spotManager = new SpotManager();
    private final NearbySpawnService spawnService = new NearbySpawnService(spotManager, modeService);
    private final SpotGatherHandler spotGatherHandler = new SpotGatherHandler();
    private final CommandManager commandManager = new CommandManager();
    private final GatherCommand gatherCommand = new GatherCommand();

    @Override
    public void onEnable() {
        plugin = this;

        createFolders();
        createConfigs();
        loadConfigs();
        registerListeners();

        modeService.start();
        spotManager.start(modeService);
        spawnService.start();

        getCommand("gathering").setExecutor(commandManager);
        getCommand("gathering").setTabCompleter(commandManager);
        getCommand("gather").setExecutor(gatherCommand);
        getCommand("gather").setTabCompleter(gatherCommand);

        getLogger().info("Gathering enabled.");
    }

    @Override
    public void onDisable() {
        spawnService.stop();
        modeService.shutdown();
        spotManager.shutdown();
        getLogger().info("Gathering disabled.");
    }

    /** Reloads configs, clears active spots and restarts the timers with the new intervals. */
    public void reloadAll() {
        loadConfigs();
        spotManager.clearSpots();
        modeService.start();
        spotManager.startTasks(modeService);
        spawnService.start();
        getLogger().info("Gathering configs reloaded.");
    }

    public SpotManager getSpotManager() {
        return spotManager;
    }

    public GatheringModeService getModeService() {
        return modeService;
    }

    public NearbySpawnService getSpawnService() {
        return spawnService;
    }

    // ----------------------------------------------------------------------
    //  Config Loading
    // ----------------------------------------------------------------------
    public void loadConfigs() {
        configLoader.load(new File(getDataFolder(), "config.yml"));
        categoryLoader.load(new File(getDataFolder(), "categories.yml"));
        spotTypeLoader.load(new File(getDataFolder(), "spot-types.yml"));
        warnIfProvincesUnavailable();
    }

    private void warnIfProvincesUnavailable() {
        boolean needsProvinces = SpotTypeLoader.all().stream().anyMatch(SpotTypeLoader.SpotTypeDefinition::requiresProvince);
        if (needsProvinces && !Provinces.available()) {
            getLogger().warning("Some spot types are limited to provinces, but SimpleFactions' province map is"
                    + " unavailable; those types will not spawn.");
        }
    }

    // ----------------------------------------------------------------------
    //  Listeners
    // ----------------------------------------------------------------------
    public void registerListeners() {
        getServer().getPluginManager().registerEvents(spotManager, this);
        getServer().getPluginManager().registerEvents(spotGatherHandler, this);
        getServer().getPluginManager().registerEvents(AdminModeService.get(), this);
        getServer().getPluginManager().registerEvents(modeService, this);
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
