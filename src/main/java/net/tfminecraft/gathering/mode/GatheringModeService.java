package net.tfminecraft.gathering.mode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.utils.CharacterBridge;
import net.tfminecraft.gathering.utils.FocusBridge;

/**
 * Who is searching for gathering spots. Being in the mode costs Mental Points: one charge on entry and
 * one every {@link Cache#drainIntervalSeconds}. The mode ends only when the player turns it off or a
 * charge fails; quitting and plugin shutdown clear it without a refund, while a config reload keeps it.
 * While it is on, a boss bar
 * shows the player's Mental Points (an action bar would be overwritten by the MMOCore HUD).
 */
public final class GatheringModeService implements Listener {

    public enum EnterResult {
        ENTERED,
        ALREADY_ACTIVE,
        NO_CHARACTER,
        NO_FOCUS
    }

    /** Players in the mode, mapped to seconds since their last charge. */
    private final Map<UUID, Integer> active = new LinkedHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;

    public void start() {
        stopTask();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Gathering.plugin, 20L, 20L);
    }

    public void shutdown() {
        stopTask();
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id);
        }
    }

    /** Removes the player from the mode and hides their status bar if they are online. */
    private void end(UUID id) {
        active.remove(id);
        BossBar bar = bars.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (bar != null && player != null) player.hideBossBar(bar);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isActive(Player player) {
        return player != null && active.containsKey(player.getUniqueId());
    }

    public int activeCount() {
        return active.size();
    }

    /** Online players currently in the mode. */
    public List<Player> activePlayers() {
        List<Player> players = new ArrayList<>(active.size());
        for (UUID id : active.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) players.add(player);
        }
        return players;
    }

    public EnterResult enter(Player player) {
        if (isActive(player)) return EnterResult.ALREADY_ACTIVE;
        if (CharacterBridge.getActiveCharacterUuid(player) == null) {
            send(player, Cache.messageNoCharacter);
            return EnterResult.NO_CHARACTER;
        }
        if (!FocusBridge.trySpend(player, Cache.drainAmount)) {
            send(player, Cache.messageNoFocus);
            return EnterResult.NO_FOCUS;
        }
        active.put(player.getUniqueId(), 0);
        send(player, Cache.messageEnter);
        showStatus(player);
        return EnterResult.ENTERED;
    }

    public boolean leave(Player player) {
        if (!isActive(player)) return false;
        end(player.getUniqueId());
        send(player, Cache.messageLeave);
        return true;
    }

    /** Returns true if the player is in the mode afterwards. */
    public boolean toggle(Player player) {
        if (isActive(player)) {
            leave(player);
            return false;
        }
        return enter(player) == EnterResult.ENTERED;
    }

    public void tick() {
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(active.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                end(entry.getKey());
                continue;
            }
            int seconds = entry.getValue() + 1;
            if (seconds >= Cache.drainIntervalSeconds) {
                if (!FocusBridge.trySpend(player, Cache.drainAmount)) {
                    end(entry.getKey());
                    send(player, Cache.messageOutOfFocus);
                    continue;
                }
                seconds = 0;
            }
            active.put(entry.getKey(), seconds);
            if (seconds % 2 == 0) showStatus(player);
        }
    }

    private void showStatus(Player player) {
        if (Cache.statusBarFormat == null || Cache.statusBarFormat.isBlank()) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) player.hideBossBar(bar);
            return;
        }
        int points = FocusBridge.points(player);
        int max = FocusBridge.max();
        float progress = max <= 0 ? 0f : Math.min(1f, Math.max(0f, points / (float) max));
        var name = LegacyComponentSerializer.legacySection()
                .deserialize(colorize(Cache.statusBarFormat.replace("{mp}", String.valueOf(points))));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
        } else {
            bar.name(name);
            bar.progress(progress);
        }
        player.showBossBar(bar);
    }

    private static void send(Player player, String message) {
        if (message == null || message.isBlank()) return;
        player.sendMessage(colorize(message));
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        active.remove(id);
        bars.remove(id);
    }
}
