package net.tfminecraft.gathering.manager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AdminModeService implements Listener {

    private static final AdminModeService INSTANCE = new AdminModeService();
    private final Set<UUID> enabled = new HashSet<>();

    private AdminModeService() {}

    public static AdminModeService get() {
        return INSTANCE;
    }

    public boolean isEnabled(Player player) {
        return player != null && enabled.contains(player.getUniqueId());
    }

    public boolean enable(Player player) {
        if (player == null) return false;
        return enabled.add(player.getUniqueId());
    }

    public boolean disable(Player player) {
        if (player == null) return false;
        return enabled.remove(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (player == null) return false;
        UUID id = player.getUniqueId();
        if (enabled.contains(id)) {
            enabled.remove(id);
            return false;
        }
        enabled.add(id);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enabled.remove(event.getPlayer().getUniqueId());
    }
}
