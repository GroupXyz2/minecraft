package de.groupxyz.serverselector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.inventory.DoubleChestInventory;

import java.util.*;

public class Serverselector extends JavaPlugin implements Listener {

    private boolean pluginEnabled = true;

    private Map<UUID, Integer> teleportTimers = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("serverselector by GroupXyz starting");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("serverselector shutting down...");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        giveCompass(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("serverselector")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("enable")) {
                    if (!pluginEnabled) {
                        pluginEnabled = true;
                        sender.sendMessage(ChatColor.GREEN + "serverselector enabled.");
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            giveCompass(player);
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "serverselector is already enabled.");
                    }
                } else if (args[0].equalsIgnoreCase("disable")) {
                    if (pluginEnabled) {
                        pluginEnabled = false;
                        sender.sendMessage(ChatColor.GREEN + "serverselector disabled.");
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            removeCompass(player);
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "serverselector is already disabled.");
                    }
                } else if (args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "config reloaded.");
                } else {
                    sender.sendMessage("Usage: /serverselector <enable|disable|reload>");
                }
            } else {
                sender.sendMessage("Usage: /serverselector <enable|disable|reload>");
            }
            return true;
        }
        return false;
    }

    private void giveCompass(Player player) {
        FileConfiguration config = getConfig();

        if (pluginEnabled) {
            if (config.contains("compass_slot")) {
                int compassSlot = config.getInt("compass_slot");

                player.getInventory().setItem(compassSlot, createCompassItem());
            } else {
                player.getInventory().setItem(4, createCompassItem());
            }
        } else if (!pluginEnabled) {
            removeCompass(player);
        }
    }

    private void removeCompass(Player player) {
        player.getInventory().remove(Material.COMPASS);
    }

    private ItemStack createCompassItem() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName("serverswitcher");
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        compass.setItemMeta(meta);
        return compass;
    }

    private void openServerSelectionGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "Server Selection");

        FileConfiguration config = getConfig();
        if (config.contains("servers")) {
            ConfigurationSection serversSection = config.getConfigurationSection("servers");
            Set<String> serverKeys = serversSection.getKeys(false);
            for (String serverKey : serverKeys) {
                ConfigurationSection serverSection = serversSection.getConfigurationSection(serverKey);
                String serverName = serverSection.getString("name");
                String serverDescription = serverSection.getString("description");
                Material serverMaterial = Material.matchMaterial(serverSection.getString("material"));
                int slot = serverSection.getInt("slot", 0);

                if (slot >= 0 && slot < 9) {
                    if (serverMaterial != null) {
                        ItemStack serverItem = createServerItem(serverMaterial, serverName, serverDescription);
                        gui.setItem(slot, serverItem);
                    }
                } else {
                    getLogger().warning("Server slot out of bounds: " + slot);
                }
            }
        }

        player.openInventory(gui);
    }

    private ItemStack createServerItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(description));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
            if (item.getItemMeta().getDisplayName().equals("serverswitcher")) {
                openServerSelectionGUI(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isServerSwitcherCompass(item)) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && isServerSwitcherCompass(clickedItem)) {
                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (isServerSwitcherCompass(item)) {
                event.setCancelled(true);
                break;
            }
        }
    }


    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isServerSwitcherCompass(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean isServerSwitcherCompass(ItemStack item) {
        return item != null
                && item.getType() == Material.COMPASS
                && item.hasItemMeta()
                && "serverswitcher".equals(item.getItemMeta().getDisplayName());
    }


    private void teleportToServer(Player player, String serverName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName.toLowerCase());

            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());

            int taskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Server not online: " + serverName);
                }
                teleportTimers.remove(player.getUniqueId());
            }, 100).getTaskId();

            teleportTimers.put(player.getUniqueId(), taskId);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error, server might be restarting: " + e.getMessage());
        }
    }

}


