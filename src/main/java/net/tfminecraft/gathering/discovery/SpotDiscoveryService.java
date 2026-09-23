package net.tfminecraft.gathering.discovery;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.RPCharacters.Objects.Attributes.AttributeModifier;
import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.gathering.cache.Cache;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.manager.SpotVisualManager;
import net.tfminecraft.gathering.spot.GatheringSpot;
import net.tfminecraft.gathering.utils.CharacterBridge;
import net.tfminecraft.gathering.utils.ProfessionBridge;

public final class SpotDiscoveryService {

    private SpotDiscoveryService() {}

    public static void tickPassive(SpotManager manager) {
        if (!Cache.passiveDiscoveryEnabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            RPCharacter character = CharacterBridge.getActiveCharacter(player);
            if (character == null) continue;

            List<GatheringSpot> nearby = manager.getSpotsNear(player.getLocation(), Cache.passiveRadius);
            for (GatheringSpot spot : nearby) {
                tryPassiveDiscovery(player, character, spot, manager);
            }
        }
    }

    public static boolean tryPassiveDiscovery(Player player, RPCharacter character, GatheringSpot spot,
            SpotManager manager) {
        if (player == null || character == null || spot == null) return false;

        UUID charUuid = CharacterBridge.getActiveCharacterUuid(player);
        if (charUuid == null || spot.isDiscoveredBy(charUuid)) return false;

        SpotTypeLoader.SpotTypeDefinition type = SpotTypeLoader.getByString(spot.getSpotTypeId());
        double chance = computeChance(character, player, type);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return false;

        spot.markDiscovered(charUuid);
        manager.markDirty();

        if (Cache.passiveMessage != null && !Cache.passiveMessage.isBlank()) {
            player.sendMessage(StringFormatter.formatHex(Cache.passiveMessage.replace('&', '\u00A7')));
        }

        SpotVisualManager.get().refreshPlayer(player, manager);
        return true;
    }

    public static double computeChance(RPCharacter character, Player player,
            SpotTypeLoader.SpotTypeDefinition spotType) {
        int wisdom = character.getAttributeData().getAmount(new AttributeModifier("wisdom", 0));
        int intelligence = character.getAttributeData().getAmount(new AttributeModifier("intelligence", 0));
        double score = Cache.passiveBaseChance
                + (wisdom * Cache.wisdomWeight)
                + (intelligence * Cache.intelligenceWeight)
                + ProfessionBridge.professionBonus(player, spotType);
        return Math.max(0.0, Math.min(1.0, score));
    }
}
