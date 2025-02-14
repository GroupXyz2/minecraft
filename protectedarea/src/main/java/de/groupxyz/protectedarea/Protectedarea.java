package de.groupxyz.protectedarea;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Protectedarea extends JavaPlugin {

    private static Protectedarea instance;
    private Map<UUID, Location> firstPoints = new HashMap<>();
    private Map<UUID, Location> secondPoints = new HashMap<>();
    private Map<String, ProtectedRegion> protectedRegions = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("protectedarea-RELOADED by GroupXyz starting...");
        getCommand("protect").setExecutor(new ProtectCommand(this));
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ClickListener(this), this);

        loadProtectedRegions();
    }

    @Override
    public void onDisable() {
        getLogger().info("protectedarea-RELOADED shutting down...");
        saveProtectedRegions();
    }

    public static Protectedarea getInstance() {
        return instance;
    }

    public Map<UUID, Location> getFirstPoints() {
        return firstPoints;
    }

    public Map<UUID, Location> getSecondPoints() {
        return secondPoints;
    }

    public Map<String, ProtectedRegion> getProtectedRegions() {
        return protectedRegions;
    }

    private void loadProtectedRegions() {
        FileConfiguration config = getConfig();
        if (config.contains("protectedRegions")) {
            ConfigurationSection regionsSection = config.getConfigurationSection("protectedRegions");
            getLogger().info("Protected regions loading!");

            for (String key : regionsSection.getKeys(false)) {
                ConfigurationSection regionSection = regionsSection.getConfigurationSection(key);

                String name = regionSection.getString("name");
                Location firstPoint = getLocationFromConfig(regionSection.getConfigurationSection("firstPoint"));
                Location secondPoint = getLocationFromConfig(regionSection.getConfigurationSection("secondPoint"));

                ProtectedRegion protectedRegion = new ProtectedRegion(name, firstPoint, secondPoint);
                protectedRegions.put(name, protectedRegion);
            }
        }
    }

    private Location getLocationFromConfig(ConfigurationSection config) {
        World world = Bukkit.getWorld(config.getString("world"));
        double x = config.getDouble("x");
        double y = config.getDouble("y");
        double z = config.getDouble("z");

        return new Location(world, x, y, z);
    }

    private void saveProtectedRegions() {
        FileConfiguration config = getConfig();
        ConfigurationSection regionsSection = config.createSection("protectedRegions");

        for (Map.Entry<String, ProtectedRegion> entry : protectedRegions.entrySet()) {
            ConfigurationSection regionSection = regionsSection.createSection(entry.getKey());
            ProtectedRegion protectedRegion = entry.getValue();

            regionSection.set("name", protectedRegion.getName());
            saveLocationToConfig(regionSection.createSection("firstPoint"), protectedRegion.getFirstPoint());
            saveLocationToConfig(regionSection.createSection("secondPoint"), protectedRegion.getSecondPoint());
        }

        saveConfig();

        getLogger().info("Protected regions saved!");
    }

    private void saveLocationToConfig(ConfigurationSection config, Location location) {
        config.set("world", location.getWorld().getName());
        config.set("x", location.getX());
        config.set("y", location.getY());
        config.set("z", location.getZ());
    }
}
