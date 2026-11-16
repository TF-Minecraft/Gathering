package net.tfminecraft.gathering.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.spot.GatheringSpot;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "clearcache", "status", "forcespawn", "adminmode", "freshness");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gathering.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/gathering <reload|clearcache|status|forcespawn|adminmode|freshness>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "clearcache" -> handleClearCache(sender, args);
            case "status" -> handleStatus(sender);
            case "forcespawn" -> handleForceSpawn(sender, args);
            case "adminmode" -> handleAdminMode(sender, args);
            case "freshness" -> handleFreshness(sender);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        Gathering.plugin.reloadAll();
        sender.sendMessage("§aGathering configs reloaded; active spots cleared.");
    }

    private void handleClearCache(CommandSender sender, String[] args) {
        SpotManager manager = Gathering.plugin.getSpotManager();
        if (args.length >= 2) {
            manager.clearFreshness(args[1]);
            sender.sendMessage("§aReset chunk depletion for world §f" + args[1] + "§a.");
        } else {
            manager.clearFreshness(null);
            sender.sendMessage("§aReset chunk depletion for all worlds.");
        }
    }

    private void handleStatus(CommandSender sender) {
        SpotManager manager = Gathering.plugin.getSpotManager();
        long now = System.currentTimeMillis();
        sender.sendMessage("§6Gathering status:");
        sender.sendMessage("§7Active spots: §f" + manager.getActiveSpotCount());
        sender.sendMessage("§7Players gathering: §f" + Gathering.plugin.getModeService().activeCount());
        sender.sendMessage("§7Tracked chunks: §f" + manager.getTrackedChunkCount()
                + " §7(below 50% freshness: §f" + manager.countChunksBelow(0.5, now) + "§7)");
    }

    private void handleForceSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        SpotTypeLoader.SpotTypeDefinition type = null;
        if (args.length >= 2) {
            type = SpotTypeLoader.getByString(args[1]);
            if (type == null) {
                sender.sendMessage("§cUnknown spot type: " + args[1]);
                return;
            }
        }

        GatheringSpot spot = Gathering.plugin.getSpawnService().forceSpawn(player, type);
        if (spot == null) {
            sender.sendMessage("§cNo valid surface found nearby.");
        } else {
            sender.sendMessage("§aSpawned §f" + spot.getSpotTypeId() + " §aat §f" + spot.getBlockX() + ", "
                    + spot.getBlockY() + ", " + spot.getBlockZ());
        }
    }

    private void handleFreshness(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        SpotManager manager = Gathering.plugin.getSpotManager();
        Location loc = player.getLocation();
        ChunkKey key = ChunkKey.of(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
        long now = System.currentTimeMillis();
        sender.sendMessage(String.format(Locale.ROOT, "§7Chunk §f%s§7: depletion §f%.2f§7, freshness §f%.0f%%",
                key, manager.depletion(key, now), manager.freshness(key, now) * 100));
    }

    private void handleAdminMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        AdminModeService adminMode = AdminModeService.get();
        if (args.length < 2) {
            boolean on = adminMode.toggle(player);
            sender.sendMessage(on ? "§aGathering admin mode §lenabled§a." : "§7Gathering admin mode §cdisabled§7.");
            return;
        }

        String mode = args[1].toLowerCase();
        switch (mode) {
            case "on", "enable", "true" -> {
                adminMode.enable(player);
                sender.sendMessage("§aGathering admin mode §lenabled§a.");
            }
            case "off", "disable", "false" -> {
                adminMode.disable(player);
                sender.sendMessage("§7Gathering admin mode §cdisabled§7.");
            }
            default -> sender.sendMessage("§cUsage: /gathering adminmode <on|off>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gathering.admin")) return List.of();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "adminmode".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("on", "off"), args[1]);
        }
        if (args.length == 2 && "forcespawn".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(SpotTypeLoader.get().keySet()), args[1]);
        }
        if (args.length == 2 && "clearcache".equalsIgnoreCase(args[0])) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
