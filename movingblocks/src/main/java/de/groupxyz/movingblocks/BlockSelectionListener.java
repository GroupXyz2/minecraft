package de.groupxyz.movingblocks;

import de.groupxyz.movingblocks.AnimationManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockSelectionListener implements Listener {
    private final AnimationManager animationManager;
    private final Map<UUID, Location> firstPoint;

    public BlockSelectionListener(AnimationManager animationManager) {
        this.animationManager = animationManager;
        this.firstPoint = new HashMap<>();
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        ItemStack item = event.getItem();

        if (item == null || !item.getType().equals(Material.STICK)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() ||
                !meta.getDisplayName().equals("§6Block Selection Stick")) {
            return;
        }

        UUID playerUUID = event.getPlayer().getUniqueId();

        if (event.getPlayer().isSneaking()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Location clickedLocation = event.getClickedBlock().getLocation();

                if (!firstPoint.containsKey(playerUUID)) {
                    firstPoint.put(playerUUID, clickedLocation);
                    event.getPlayer().sendMessage("§aFirst Point set: " + formatLocation(clickedLocation));
                } else {
                    Location first = firstPoint.remove(playerUUID);
                    Location second = clickedLocation;

                    animationManager.selectArea(event.getPlayer(), first, second);
                }
                event.setCancelled(true);
            }
        } else {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                event.setCancelled(true);
                animationManager.toggleBlockSelection(event.getPlayer(), event.getClickedBlock());
            }

            if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                animationManager.createFrame(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location blockLoc = event.getBlock().getLocation();

        if (animationManager.isBlockProtected(blockLoc)) {
            event.setCancelled(true);

            UUID playerUUID = event.getPlayer().getUniqueId();
            String key = "protection_message:" + playerUUID;
            
            if (!event.getPlayer().hasMetadata(key)) {
                event.getPlayer().sendMessage("§c§lThis block is part of a protected animation and cannot be broken!");

                org.bukkit.metadata.FixedMetadataValue metaValue = new org.bukkit.metadata.FixedMetadataValue(
                        animationManager.getPlugin(), true);
                event.getPlayer().setMetadata(key, metaValue);

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (event.getPlayer().isOnline()) {
                            event.getPlayer().removeMetadata(key, animationManager.getPlugin());
                        }
                    }
                }.runTaskLater(animationManager.getPlugin(), 40);
            }
        }
    }

    private String formatLocation(Location loc) {
        return "§e[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
    }
}
