package net.tfminecraft.gathering.spot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.gathering.Gathering;
import net.tfminecraft.gathering.loader.CategoryLoader;
import net.tfminecraft.gathering.loader.SpotTypeLoader;
import net.tfminecraft.gathering.loot.DropCategory;
import net.tfminecraft.gathering.manager.AdminModeService;
import net.tfminecraft.gathering.manager.ChunkKey;
import net.tfminecraft.gathering.manager.SpotManager;
import net.tfminecraft.gathering.utils.CharacterBridge;
import net.tfminecraft.gathering.utils.GatherFx;
import net.tfminecraft.gathering.utils.ItemResolver;

public final class SpotGatherHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        SpotManager manager = Gathering.plugin.getSpotManager();
        GatheringSpot spot = manager.getAtBlock(event.getClickedBlock().getLocation());
        if (spot == null) return;

        var player = event.getPlayer();
        boolean adminMode = AdminModeService.get().isEnabled(player);
        if (!adminMode) {
            UUID charUuid = CharacterBridge.getActiveCharacterUuid(player);
            if (charUuid == null) return;
            if (!spot.isDiscoveredBy(charUuid)) return;
        }

        event.setCancelled(true);

        List<ItemStack> rewards = rollRewards(spot);
        if (rewards == null || rewards.isEmpty()) {
            event.getPlayer().sendMessage("§cNothing to gather here.");
            return;
        }

        Location effectLoc = spot.getGatherEffectLocation();
        Location blockCorner = spot.getBlockCorner();
        Material surface = spot.getSpawnBlockMaterial();
        if (effectLoc != null && blockCorner != null) {
            GatherFx.playGather(player, effectLoc, blockCorner, surface, rewards);
        } else {
            GatherFx.dropAtPlayer(player, rewards);
        }

        ChunkKey key = new ChunkKey(spot.getWorldName(), spot.getChunkX(), spot.getChunkZ());
        manager.remove(spot);
        manager.setChunkCooldown(key);
    }

    private List<ItemStack> rollRewards(GatheringSpot spot) {
        SpotTypeLoader.SpotTypeDefinition type = SpotTypeLoader.getByString(spot.getSpotTypeId());
        if (type == null || type.categories.isEmpty()) return null;

        SpotTypeLoader.CategoryWeight categoryWeight = pickCategory(type);
        if (categoryWeight == null) return null;

        DropCategory category = CategoryLoader.getByString(categoryWeight.id);
        if (category == null || category.isEmpty()) return null;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int dropCount = categoryWeight.rollDropCount(rng);
        List<DropCategory.Drop> drops = category.rollMany(dropCount, rng);

        List<ItemStack> items = new ArrayList<>(drops.size());
        for (DropCategory.Drop drop : drops) {
            ItemStack stack = ItemResolver.fromPath(drop.type, drop.amount);
            if (stack != null) items.add(stack);
        }
        return items.isEmpty() ? null : items;
    }

    private SpotTypeLoader.CategoryWeight pickCategory(SpotTypeLoader.SpotTypeDefinition type) {
        double total = 0.0;
        for (SpotTypeLoader.CategoryWeight cw : type.categories) {
            total += cw.weight;
        }
        if (total <= 0.0) return null;

        double r = ThreadLocalRandom.current().nextDouble() * total;
        double acc = 0.0;
        for (SpotTypeLoader.CategoryWeight cw : type.categories) {
            acc += cw.weight;
            if (r <= acc) return cw;
        }
        return type.categories.get(type.categories.size() - 1);
    }
}
