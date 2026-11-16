package net.tfminecraft.gathering.mode;

import java.util.ArrayList;
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

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.utils.CharacterBridge;
import net.tfminecraft.gathering.utils.FocusBridge;

/**
 * Who is searching for gathering spots. Being in the mode costs Mental Points: one charge on entry and
 * one every {@link Cache#drainIntervalSeconds}. The mode ends only when the player turns it off or a
 * charge fails; quitting, reloads and shutdowns clear it without a refund.
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
        active.clear();
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
        if (player == null || active.remove(player.getUniqueId()) == null) return false;
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
                active.remove(entry.getKey());
                continue;
            }
            int seconds = entry.getValue() + 1;
            if (seconds >= Cache.drainIntervalSeconds) {
                if (!FocusBridge.trySpend(player, Cache.drainAmount)) {
                    active.remove(entry.getKey());
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
        if (Cache.actionbarFormat == null || Cache.actionbarFormat.isBlank()) return;
        String text = Cache.actionbarFormat.replace("{mp}", String.valueOf(FocusBridge.points(player)));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(colorize(text)));
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
        active.remove(event.getPlayer().getUniqueId());
    }
}
