package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private final Plugin plugin;
    private final String currentVersion;
    private final String githubBaseUrl;
    private final Pattern versionPattern;
    
    private String latestVersion = null;
    private boolean updateAvailable = false;
    private boolean enabled;
    private boolean notifyAdmins;
    
    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.githubBaseUrl = "https://github.com/GroupXyz2/minecraft/blob/main/movingblocks/";
        this.versionPattern = Pattern.compile("movingblocks-(\\d+\\.\\d+(?:-\\w+)?)\\.jar");
        
        this.enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        this.notifyAdmins = plugin.getConfig().getBoolean("update-checker.notify-admins", true);
        
        if (this.enabled) {
            checkForUpdates();
        }
    }
    
    public void checkForUpdates() {
        if (!enabled) {
            return;
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String latestJarName = getLatestJarName();
                    if (latestJarName != null) {
                        Matcher matcher = versionPattern.matcher(latestJarName);
                        if (matcher.find()) {
                            latestVersion = matcher.group(1);
                            
                            if (isNewerVersion(latestVersion, currentVersion)) {
                                updateAvailable = true;
                                plugin.getLogger().info("A new update is available: " + latestVersion + 
                                        " (Current version: " + currentVersion + ")");
                                plugin.getLogger().info("Download at: " + githubBaseUrl + latestJarName);
                            } else {
                                plugin.getLogger().info("Running the latest version (" + currentVersion + ")");
                            }
                        }
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    public void notifyPlayer(Player player) {
        if (!enabled || !notifyAdmins) {
            return;
        }
        
        if (player.hasPermission("movingblocks.admin") && updateAvailable && latestVersion != null) {
            player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                    "A new version is available: " + ChatColor.WHITE + latestVersion + 
                    ChatColor.YELLOW + " (Current: " + ChatColor.WHITE + currentVersion + ChatColor.YELLOW + ")");
            player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                    "Download at: " + ChatColor.WHITE + githubBaseUrl + "movingblocks-" + latestVersion + ".jar");
        }
    }
    
    public void scheduleUpdateChecks(int initialDelayHours, int checkIntervalHours) {
        if (!enabled) {
            return;
        }
        
        long initialDelay = initialDelayHours * 60L * 60L * 20L;
        long checkInterval = checkIntervalHours * 60L * 60L * 20L;
        
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        }.runTaskTimerAsynchronously(plugin, initialDelay, checkInterval);
    }
    
    private String getLatestJarName() throws IOException {
        URL url = new URL("https://github.com/GroupXyz2/minecraft/tree/main/movingblocks");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        String html = response.toString();
        Matcher matcher = versionPattern.matcher(html);
        String latestJarName = null;
        String latestVersionFound = null;
        
        while (matcher.find()) {
            String jarName = matcher.group(0);
            String jarVersion = matcher.group(1);
            
            if (latestVersionFound == null || isNewerVersion(jarVersion, latestVersionFound)) {
                latestVersionFound = jarVersion;
                latestJarName = jarName;
            }
        }
        
        return latestJarName;
    }
    
    private boolean isNewerVersion(String newVersion, String oldVersion) {
        String[] newParts = newVersion.split("\\.|\\-");
        String[] oldParts = oldVersion.split("\\.|\\-");
        
        for (int i = 0; i < Math.min(newParts.length, oldParts.length); i++) {
            try {
                int newVal = Integer.parseInt(newParts[i]);
                int oldVal = Integer.parseInt(oldParts[i]);
                
                if (newVal > oldVal) {
                    return true;
                } else if (newVal < oldVal) {
                    return false;
                }
            } catch (NumberFormatException e) {
                int comparison = newParts[i].compareTo(oldParts[i]);
                if (comparison != 0) {
                    return comparison > 0;
                }
            }
        }
        
        return newParts.length > oldParts.length;
    }
    
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isNotifyAdmins() {
        return notifyAdmins;
    }
}