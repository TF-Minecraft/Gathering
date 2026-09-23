package net.tfminecraft.gathering.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.experience.Profession;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;

public final class ProfessionBridge {

    private ProfessionBridge() {}

    public static double professionBonus(Player player, SpotTypeLoader.SpotTypeDefinition spotType) {
        if (!Cache.professionEnabled) return 0.0;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) return 0.0;
        if (player == null) return 0.0;

        String professionId = spotType != null && spotType.professionId != null
                ? spotType.professionId
                : Cache.professionId;
        if (professionId == null || professionId.isBlank()) return 0.0;

        try {
            Profession profession = MMOCore.plugin.professionManager.get(professionId);
            if (profession == null) return 0.0;
            int level = net.Indyuce.mmocore.api.player.PlayerData.get(player)
                    .getCollectionSkills().getLevel(profession);
            return Math.max(0, level) * Cache.professionLevelWeight;
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
