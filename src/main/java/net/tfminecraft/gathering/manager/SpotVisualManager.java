package net.tfminecraft.gathering.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.mode.GatheringModeService;
import net.tfminecraft.gathering.spot.GatheringSpot;
import net.tfminecraft.gathering.spot.SpotParticles;

/** Shows spot particle rings to players in gathering mode and to staff in admin mode. */
public final class SpotVisualManager {

    private static final SpotVisualManager INSTANCE = new SpotVisualManager();

    private SpotVisualManager() {}

    public static SpotVisualManager get() {
        return INSTANCE;
    }

    public void tickAllPlayers(SpotManager manager, GatheringModeService mode) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player, manager, mode);
        }
    }

    private void tickPlayer(Player player, SpotManager manager, GatheringModeService mode) {
        boolean adminMode = AdminModeService.get().isEnabled(player);
        if (!adminMode && !mode.isActive(player)) return;

        for (GatheringSpot spot : manager.getSpotsNear(player.getLocation(), Cache.spotKeepaliveRadius)) {
            SpotTypeLoader.SpotTypeDefinition type = SpotTypeLoader.getByString(spot.getSpotTypeId());
            SpotParticles.tickRing(player, spot, type, adminMode);
        }
    }
}
