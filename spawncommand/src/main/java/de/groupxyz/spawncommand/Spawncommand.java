package de.groupxyz.spawncommand;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

public class Spawncommand extends JavaPlugin {

    private Location spawnLocation;
    private BukkitTask teleportTask;
    private int teleportTaskID;

    @Override
    public void onEnable() {
        getLogger().info("spawncommand by GroupXyz starting, loading configuration");
        loadSpawnLocation();
    }

    @Override
    public void onDisable() {
        getLogger().info("spawncommand is being disabled");
        saveSpawnLocation();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("spawn") && sender instanceof Player) {
            Player player = (Player) sender;

            Location initialLocation = player.getLocation();
            double initialHealth = player.getHealth();

            player.sendMessage(ChatColor.YELLOW + "Teleporting in 10 seconds");

            teleportTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (player.getHealth() != initialHealth || !player.getLocation().getBlock().equals(initialLocation.getBlock())) {
                    player.sendMessage(ChatColor.YELLOW + "Teleportation cancelled due to damage or movement.");
                    teleportTask.cancel();
                }
            }, 20L, 20L);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!teleportTask.isCancelled()) {
                    player.sendMessage(ChatColor.YELLOW + "Teleported to spawn");
                    player.teleport(spawnLocation);
                }
            }, 200L);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("serverspawn") && sender instanceof Player) {
            Player player = (Player) sender;
            spawnLocation = player.getLocation();
            player.sendMessage(ChatColor.YELLOW + "Spawn position saved successfully");
            saveSpawnLocation();
            return true;
        }
        return false;
    }


    private void saveSpawnLocation() {
        if (spawnLocation != null) {
            if (getServer().getWorld(spawnLocation.getWorld().getName()) != null) {
                File configFile = new File(getDataFolder(), "config.yml");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                config.set("spawnLocation", spawnLocation.serialize());
                try {
                    config.save(configFile);
                    getLogger().info("Configuration saved, plugin disabled");
                } catch (IOException e) {
                    getLogger().warning("Error while saving configuration, see stacktrace below");
                    e.printStackTrace();
                }
            } else {
                getLogger().warning("Spawn position not saved. The world does not exist.");
            }
        }
    }

    private void loadSpawnLocation() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            if (config.contains("spawnLocation")) {
                spawnLocation = Location.deserialize(config.getConfigurationSection("spawnLocation").getValues(true));
                getLogger().info("Configuration loaded successfully");
            }
        }
    }
}



