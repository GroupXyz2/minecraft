package de.groupxyz.movingblocks;

import de.groupxyz.movingblocks.AnimationManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BlockSelectionListener implements Listener {
    private final AnimationManager animationManager;

    public BlockSelectionListener(AnimationManager animationManager) {
        this.animationManager = animationManager;
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
