package de.groupxyz.serverstatistics;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import java.lang.management.ManagementFactory;
import java.util.Collections;

public class Serverstatistics extends JavaPlugin implements Listener {
    private int maxOnlinePlayers = 0;
    private BukkitRunnable task;

    @Override
    public void onEnable() {
        getLogger().info("ServerStatistics by GroupXyz loading...");
        getServer().getPluginManager().registerEvents(this, this);
        loadMaxOnlinePlayers();
    }

    @Override
    public void onDisable() {
        getLogger().info("ServerStatistics shutting down!");
        saveMaxOnlinePlayers();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("statistics")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player!");
                return true;
            }

            Player player = (Player) sender;
            openStatsGUI(player);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("Server Stats")) {
            if (task != null) {
                task.cancel();
            }
        }
    }

    public void openStatsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(player, 45, "Server Stats");

        task = new BukkitRunnable() {
            @Override
            public void run() {
                double ramUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                double maxRam = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                double cpuUsage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
                int onlinePlayers = Bukkit.getOnlinePlayers().size();
                int chunksLoaded = Bukkit.getWorlds().stream().mapToInt(world -> world.getLoadedChunks().length).sum();
                long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
                double tps = Bukkit.getServer().getTPS()[0];
                int entities = Bukkit.getWorlds().stream().mapToInt(world -> world.getEntities().size()).sum();
                int maxOnlinePlayersEver = maxOnlinePlayers;

                if (onlinePlayers > maxOnlinePlayers) {
                    maxOnlinePlayers = onlinePlayers;
                }

                gui.clear();
                gui.setItem(10, createItem(Material.GOLD_INGOT, "RAM Usage", String.format("%.2f MB", ramUsage)));
                gui.setItem(12, createItem(Material.IRON_INGOT, "Max RAM", String.format("%.2f MB", maxRam)));
                gui.setItem(14, createItem(Material.REDSTONE, "CPU Usage", String.format("%.2f%%", cpuUsage)));
                gui.setItem(16, createItem(Material.EMERALD, "Online Players", String.valueOf(onlinePlayers)));
                gui.setItem(20, createItem(Material.GRASS_BLOCK, "Loaded Chunks", String.valueOf(chunksLoaded)));
                gui.setItem(22, createItem(Material.COMPASS, "Uptime", formatUptime(uptimeSeconds)));
                gui.setItem(24, createItem(Material.CLOCK, "TPS", String.format("%.2f", tps)));
                gui.setItem(30, createItem(Material.DIAMOND, "Entities", String.valueOf(entities)));
                gui.setItem(32, createItem(Material.DIAMOND_BLOCK, "Max Online Players (Ever)", String.valueOf(maxOnlinePlayersEver)));

                player.openInventory(gui);
            }
        };
        task.runTaskTimer(this, 0, 20 * 5);
    }

    private ItemStack createItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(Collections.singletonList(ChatColor.GRAY + value));
        item.setItemMeta(meta);
        return item;
    }

    private String formatUptime(long uptimeSeconds) {
        long days = uptimeSeconds / 86400;
        uptimeSeconds %= 86400;
        long hours = uptimeSeconds / 3600;
        uptimeSeconds %= 3600;
        long minutes = uptimeSeconds / 60;
        long seconds = uptimeSeconds % 60;
        return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds);
    }

    private void saveMaxOnlinePlayers() {
        getConfig().set("maxOnlinePlayers", maxOnlinePlayers);
        saveConfig();
    }

    private void loadMaxOnlinePlayers() {
        maxOnlinePlayers = getConfig().getInt("maxOnlinePlayers", 0);
    }
}

