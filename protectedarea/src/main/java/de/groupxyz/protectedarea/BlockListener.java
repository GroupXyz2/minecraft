package de.groupxyz.protectedarea;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockListener implements Listener {

    private final Protectedarea plugin;
    private final Map<UUID, Long> lastMessageTime;

    public BlockListener(Protectedarea plugin) {
        this.plugin = plugin;
        this.lastMessageTime = new HashMap<>();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();

        if (!canModifyBlock(player, blockLocation)) {
            event.setCancelled(true);
            sendErrorMessage(player, "Sorry, but you cannot place blocks here.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();

        if (!canModifyBlock(player, blockLocation)) {
            event.setCancelled(true);
            sendErrorMessage(player, "Sorry, but you cannot break blocks here.");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        Location location = entity.getLocation();
        if (!canModifyTerrain(entity, location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager.getType() == EntityType.PLAYER) {
            Location location = damager.getLocation();
            if (!canModifyTerrain(damager, location)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block blockLocation : event.blockList()) {
            if (!canModifyTerrain(event.getEntity(), blockLocation.getLocation())) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN) {
            Location location = event.getBlock().getLocation();
            if (!canModifyTerrain(event.getEntity(), location)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean canModifyBlock(Player player, Location location) {
        for (ProtectedRegion protectedRegion : plugin.getProtectedRegions().values()) {
            if (isInsideRegion(location, protectedRegion)) {
                if (!player.isOp()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canModifyTerrain(Entity entity, Location location) {
        for (ProtectedRegion protectedRegion : plugin.getProtectedRegions().values()) {
            if (entity.getType().equals(EntityType.PLAYER)) {
                Player player = (Player) entity;
                if (player.isOp()) {
                    return true;
                }
            }

            if (isInsideRegion(location, protectedRegion)) {
                return false;
            }
        }
        return true;
    }

    private boolean isInsideRegion(Location location, ProtectedRegion protectedRegion) {
        double minX = Math.min(protectedRegion.getFirstPoint().getX(), protectedRegion.getSecondPoint().getX());
        double minY = Math.min(protectedRegion.getFirstPoint().getY(), protectedRegion.getSecondPoint().getY());
        double minZ = Math.min(protectedRegion.getFirstPoint().getZ(), protectedRegion.getSecondPoint().getZ());

        double maxX = Math.max(protectedRegion.getFirstPoint().getX(), protectedRegion.getSecondPoint().getX());
        double maxY = Math.max(protectedRegion.getFirstPoint().getY(), protectedRegion.getSecondPoint().getY());
        double maxZ = Math.max(protectedRegion.getFirstPoint().getZ(), protectedRegion.getSecondPoint().getZ());

        return location.getX() >= minX && location.getX() <= maxX &&
                location.getY() >= minY && location.getY() <= maxY &&
                location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    private void sendErrorMessage(Player player, String message) {
        long currentTime = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        if (lastMessageTime.containsKey(playerId)) {
            long lastTime = lastMessageTime.get(playerId);
            if (currentTime - lastTime < 10000) {
                return;
            }
        }

        lastMessageTime.put(playerId, currentTime);

        player.sendMessage(ChatColor.DARK_RED + message);
    }
}

