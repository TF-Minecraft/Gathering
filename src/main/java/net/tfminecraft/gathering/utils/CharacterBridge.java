package net.tfminecraft.gathering.utils;

import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.tlibs.character.Characters;

public final class CharacterBridge {

    private CharacterBridge() {}

    public static RPCharacter getActiveCharacter(Player player) {
        UUID characterId = getActiveCharacterUuid(player);
        if (characterId == null) return null;
        PlayerData pd = PlayerManager.get(player);
        RPCharacter character = pd == null ? null : pd.getActiveCharacter();
        return character != null && characterId.toString().equalsIgnoreCase(character.getId())
                ? character : null;
    }

    public static UUID getActiveCharacterUuid(Player player) {
        if (player == null) return null;
        return Characters.currentSession(player).map(session -> session.ref().characterId()).orElse(null);
    }
}
