package net.tfminecraft.gathering.utils;

import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.RPCharacters.Managers.PlayerManager;
import net.tfminecraft.RPCharacters.Objects.PlayerData;
import net.tfminecraft.RPCharacters.Objects.RPCharacter;

public final class CharacterBridge {

    private CharacterBridge() {}

    public static RPCharacter getActiveCharacter(Player player) {
        if (player == null) return null;
        PlayerData pd = PlayerManager.get(player);
        if (pd == null || !pd.hasActiveCharacter()) return null;
        return pd.getActiveCharacter();
    }

    public static UUID getActiveCharacterUuid(Player player) {
        RPCharacter character = getActiveCharacter(player);
        if (character == null || character.getId() == null) return null;
        try {
            return UUID.fromString(character.getId());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
