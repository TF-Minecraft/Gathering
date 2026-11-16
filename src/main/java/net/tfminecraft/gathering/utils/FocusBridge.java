package net.tfminecraft.gathering.utils;

import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;

/** Mental Points (Focus) live in RPCharacters and belong to the player's active character. */
public final class FocusBridge {

    private FocusBridge() {}

    public static int points(Player player) {
        FocusService focus = RPCharacters.getFocusService();
        return focus == null ? 0 : focus.getPoints(player);
    }

    public static int max() {
        FocusService focus = RPCharacters.getFocusService();
        return focus == null ? 0 : focus.getMax();
    }

    /** Spends {@code amount} points; a non-positive amount is free and always succeeds. */
    public static boolean trySpend(Player player, int amount) {
        if (amount <= 0) return true;
        FocusService focus = RPCharacters.getFocusService();
        return focus != null && focus.trySpend(player, amount);
    }
}
