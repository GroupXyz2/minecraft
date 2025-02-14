package de.groupxyz.protectedarea;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ClickListener implements Listener {

    private final Protectedarea plugin;

    public ClickListener(Protectedarea plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.BLAZE_ROD) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.AQUA + "areaprotect")) {
                if (player.isOp()) {
                    Location clickedBlockLocation = event.getClickedBlock().getLocation();

                    if (!plugin.getFirstPoints().containsKey(player.getUniqueId())) {
                        plugin.getFirstPoints().put(player.getUniqueId(), clickedBlockLocation);
                        player.sendMessage(ChatColor.AQUA + "First Corner Point saved. Please select the second Corner Point.");
                    } else if (!plugin.getSecondPoints().containsKey(player.getUniqueId())) {
                        plugin.getSecondPoints().put(player.getUniqueId(), clickedBlockLocation);
                        player.sendMessage(ChatColor.AQUA + "Second Corner Point saved. Use /protect <apply [regionname]|cancel> to continue.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You have already selected both Corner Points.");
                    }
                    event.setCancelled(true);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this.");
                }
            }
        }
    }

}

