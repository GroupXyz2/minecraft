package de.groupxyz.protectedarea;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class ProtectCommand implements CommandExecutor {

    private final Protectedarea plugin;

    public ProtectCommand(Protectedarea plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command may only be used by players.");
            return true;
        }

        if (!(label.equalsIgnoreCase("protect") || label.equalsIgnoreCase("p"))) {
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("p1")) {
            plugin.getFirstPoints().put(player.getUniqueId(), player.getLocation());
            player.sendMessage(ChatColor.AQUA + "First Corner Point saved. Please select the second Corner Point with the selector or /protect p2.");
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("p2")) {
            if (!plugin.getFirstPoints().containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You have to select the first Corner Point with /protect p1 first.");
                return true;
            }

            plugin.getSecondPoints().put(player.getUniqueId(), player.getLocation());
            player.sendMessage(ChatColor.AQUA + "Second Corner Point saved. Use /protect <apply [regionname]|cancel> to continue.");
            return true;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("apply")) {
            if (!plugin.getFirstPoints().containsKey(player.getUniqueId()) || !plugin.getSecondPoints().containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You have to select both Corner Points first (/protect p1 and /protect p2).");
                return true;
            }
            Location firstPoint = plugin.getFirstPoints().get(player.getUniqueId());
            Location secondPoint = plugin.getSecondPoints().get(player.getUniqueId());

            if (!firstPoint.getWorld().equals(secondPoint.getWorld())) {
                player.sendMessage(ChatColor.RED + "Both Corners have to be in the same World.");
                return true;
            }

            String regionName = "region_" + args[1];
            if (plugin.getProtectedRegions().containsKey(regionName)) {
                player.sendMessage(ChatColor.RED + "A region with the specified name already exists. Please choose a different name.");
                return true;
            }

            ProtectedRegion protectedRegion = new ProtectedRegion(regionName, firstPoint, secondPoint);
            plugin.getProtectedRegions().put(regionName, protectedRegion);

            player.sendMessage(ChatColor.AQUA + "Region saved as " + regionName);
            plugin.getFirstPoints().remove(player.getUniqueId());
            plugin.getSecondPoints().remove(player.getUniqueId());
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (plugin.getFirstPoints().containsKey(player.getUniqueId())) {
                plugin.getFirstPoints().remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Region selection canceled.");
            } else if (plugin.getSecondPoints().containsKey(player.getUniqueId())) {
                plugin.getSecondPoints().remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Second Corner Point selection canceled.");
            } else {
                player.sendMessage(ChatColor.RED + "You have to start a region selection first (/protect p1).");
            }
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            for (Map.Entry<String, ProtectedRegion> entry : plugin.getProtectedRegions().entrySet()) {
                ProtectedRegion region = entry.getValue();
                player.sendMessage(ChatColor.AQUA + "Region:");
                player.sendMessage(ChatColor.GRAY + "Region Name: " + region.getName());
                player.sendMessage(ChatColor.GRAY + "First Corner: X=" + region.getFirstPoint().getX() + ", Y=" + region.getFirstPoint().getY() + ", Z=" + region.getFirstPoint().getZ());
                player.sendMessage(ChatColor.GRAY + "Second Corner: X=" + region.getSecondPoint().getX() + ", Y=" + region.getSecondPoint().getY() + ", Z=" + region.getSecondPoint().getZ());
                player.sendMessage(ChatColor.AQUA+ "----");
            }
            return true;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String regionName = args[1];
            if (plugin.getProtectedRegions().containsKey(regionName)) {
                plugin.getProtectedRegions().remove(regionName);
                player.sendMessage(ChatColor.AQUA + "Protected region '" + regionName + "' removed.");
            } else {
                player.sendMessage(ChatColor.RED + "The specified region " + regionName + " does not exist.");
            }
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            player.sendMessage(ChatColor.AQUA + "Configuration reloaded.");
            return true;
        } else if (args.length == 0) {
            ItemStack blazeRod = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = blazeRod.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "areaprotect");
            blazeRod.setItemMeta(meta);

            player.getInventory().addItem(blazeRod);
            player.sendMessage(ChatColor.AQUA + "You received an area selector, rightclick both cornerpoints (or run /protect p1).");
            return true;
        }
        return false;
    }
}

