package net.tfminecraft.gathering.manager;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.mode.GatheringModeService;

/** {@code /gather [on|off]}: toggles gathering mode for the sender. */
public final class GatherCommand implements CommandExecutor, TabCompleter {

    private static final List<String> OPTIONS = List.of("on", "off");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        GatheringModeService mode = Gathering.plugin.getModeService();
        if (args.length == 0) {
            mode.toggle(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                if (mode.enter(player) == GatheringModeService.EnterResult.ALREADY_ACTIVE) {
                    player.sendMessage("§7You are already searching.");
                }
            }
            case "off" -> {
                if (!mode.leave(player)) player.sendMessage("§7You are not searching.");
            }
            default -> player.sendMessage("§cUsage: /" + label + " [on|off]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return OPTIONS.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
