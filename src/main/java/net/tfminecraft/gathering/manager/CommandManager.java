package net.tfminecraft.gathering.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.spawn.ChunkLoadHelper;
import net.tfminecraft.gathering.spawn.ChunkProbe;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "clearcache", "status", "forcespawn", "adminmode");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gathering.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/gathering <reload|clearcache|status|forcespawn|adminmode>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "clearcache" -> handleClearCache(sender, args);
            case "status" -> handleStatus(sender);
            case "forcespawn" -> handleForceSpawn(sender, args);
            case "adminmode" -> handleAdminMode(sender, args);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        Gathering.plugin.reloadAll();
        sender.sendMessage("§aGathering configs reloaded.");
    }

    private void handleClearCache(CommandSender sender, String[] args) {
        SpotManager manager = Gathering.plugin.getSpotManager();
        if (args.length >= 2) {
            manager.clearExcludedChunks(args[1]);
            sender.sendMessage("§aCleared excluded chunk cache for world §f" + args[1] + "§a.");
        } else {
            manager.clearExcludedChunks(null);
            sender.sendMessage("§aCleared all excluded chunk caches.");
        }
    }

    private void handleStatus(CommandSender sender) {
        SpotManager manager = Gathering.plugin.getSpotManager();
        sender.sendMessage("§6Gathering status:");
        sender.sendMessage("§7Active spots: §f" + manager.getActiveSpotCount());
        sender.sendMessage("§7Excluded chunks: §f" + manager.getExcludedChunkCount());
        sender.sendMessage("§7Chunks on cooldown: §f" + manager.getCooldownChunkCount());
    }

    private void handleForceSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /gathering forcespawn <spotType> [chunkX chunkZ]");
            return;
        }

        SpotTypeLoader.SpotTypeDefinition type = SpotTypeLoader.getByString(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown spot type: " + args[1]);
            return;
        }

        World world = player.getWorld();
        int chunkX = args.length >= 4 ? parseInt(args[2], player.getLocation().getBlockX() >> 4)
                : player.getLocation().getBlockX() >> 4;
        int chunkZ = args.length >= 4 ? parseInt(args[3], player.getLocation().getBlockZ() >> 4)
                : player.getLocation().getBlockZ() >> 4;

        SpotManager manager = Gathering.plugin.getSpotManager();
        ChunkKey key = manager.chunkKey(world.getName(), chunkX, chunkZ);
        if (manager.hasActiveSpotInChunk(key)) {
            sender.sendMessage("§cChunk already has an active spot.");
            return;
        }

        ChunkProbe.ProbeOutcome outcome = ChunkLoadHelper.withLoadedChunk(world, chunkX, chunkZ,
                chunk -> ChunkProbe.probe(manager, chunk, type));

        if (outcome == ChunkProbe.ProbeOutcome.SPAWNED) {
            sender.sendMessage("§aSpawned §f" + type.id + " §aat chunk §f" + chunkX + ", " + chunkZ);
        } else {
            sender.sendMessage("§cNo valid surface in chunk - marked excluded.");
        }
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

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
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
