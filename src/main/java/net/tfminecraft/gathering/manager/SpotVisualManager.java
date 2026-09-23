package net.tfminecraft.gathering.manager;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.spot.GatheringSpot;
import net.tfminecraft.gathering.spot.SpotParticles;
import net.tfminecraft.gathering.utils.CharacterBridge;

public final class SpotVisualManager {

    private static final SpotVisualManager INSTANCE = new SpotVisualManager();

    private SpotVisualManager() {}

    public static SpotVisualManager get() {
        return INSTANCE;
    }

    public void tickAllPlayers(SpotManager manager) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player, manager);
        }
    }

    public void refreshPlayer(Player player, SpotManager manager) {
        tickPlayer(player, manager);
    }

    private void tickPlayer(Player player, SpotManager manager) {
        if (player == null || !player.isOnline()) return;

        boolean adminMode = AdminModeService.get().isEnabled(player);
        UUID charUuid = adminMode ? null : CharacterBridge.getActiveCharacterUuid(player);
        if (!adminMode && charUuid == null) return;

        List<GatheringSpot> nearby = manager.getSpotsNear(player.getLocation(),
                Math.max(Cache.passiveRadius, 32.0));
        for (GatheringSpot spot : nearby) {
            if (!adminMode && !spot.isDiscoveredBy(charUuid)) continue;
            SpotTypeLoader.SpotTypeDefinition type = SpotTypeLoader.getByString(spot.getSpotTypeId());
            SpotParticles.tickRing(player, spot, type, adminMode);
        }
    }
}
