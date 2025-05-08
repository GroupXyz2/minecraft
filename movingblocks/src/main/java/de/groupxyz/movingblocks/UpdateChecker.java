package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private final Plugin plugin;
    private final String currentVersion;
    private final String githubBaseUrl;
    private final String githubDownloadUrl;
    private final Pattern versionPattern;
    
    private String latestVersion = null;
    private String latestJarName = null;
    private boolean updateAvailable = false;
    private boolean enabled;
    private boolean notifyAdmins;
    private boolean autoUpdate;
    private boolean autoRestart;
    private File downloadedUpdateFile = null;
    
    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.githubBaseUrl = "https://github.com/GroupXyz2/minecraft/blob/main/movingblocks/";
        this.githubDownloadUrl = "https://github.com/GroupXyz2/minecraft/raw/main/movingblocks/";
        this.versionPattern = Pattern.compile("movingblocks-(\\d+\\.\\d+(?:-\\w+)?)\\.jar");
        
        this.enabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        this.notifyAdmins = plugin.getConfig().getBoolean("update-checker.notify-admins", true);
        this.autoUpdate = plugin.getConfig().getBoolean("update-checker.auto-update", false);
        this.autoRestart = plugin.getConfig().getBoolean("update-checker.auto-restart", false);
        
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        
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
                    latestJarName = getLatestJarName();
                    if (latestJarName != null) {
                        Matcher matcher = versionPattern.matcher(latestJarName);
                        if (matcher.find()) {
                            latestVersion = matcher.group(1);
                            
                            if (isNewerVersion(latestVersion, currentVersion)) {
                                updateAvailable = true;
                                plugin.getLogger().info("A new update is available: " + latestVersion + 
                                        " (Current version: " + currentVersion + ")");
                                plugin.getLogger().info("Download at: " + githubBaseUrl + latestJarName);
                                
                                if (autoUpdate) {
                                    downloadAndInstallUpdate();
                                }
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
            
            if (autoUpdate) {
                if (downloadedUpdateFile != null) {
                    player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                            "The update has been downloaded and will be installed after server restart.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                            "The update will be automatically downloaded and installed.");
                }
                
                if (autoRestart) {
                    player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                            "Server will restart automatically to apply the update.");
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                        "Download at: " + ChatColor.WHITE + githubBaseUrl + "movingblocks-" + latestVersion + ".jar");
                player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                        "You can enable auto-updates in the config.yml");
            }
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
    
    private void downloadAndInstallUpdate() {
        if (latestJarName == null || latestVersion == null) {
            plugin.getLogger().warning("Cannot download update: Version information is missing");
            return;
        }

        plugin.getLogger().info("Starting download of update to version " + latestVersion);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    File tempDir = new File(plugin.getDataFolder(), "temp");
                    if (!tempDir.exists()) {
                        tempDir.mkdirs();
                    }
                    
                    File downloadTarget = new File(tempDir, latestJarName);
                    
                    downloadFile(githubDownloadUrl + latestJarName, downloadTarget);
                    downloadedUpdateFile = downloadTarget;
                    plugin.getLogger().info("Downloaded update to: " + downloadTarget.getAbsolutePath());
                    
                    prepareInstallation(downloadTarget);
                    
                    if (notifyAdmins) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("movingblocks.admin")) {
                                player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                                        "Update to version " + latestVersion + " has been downloaded.");
                                
                                if (autoRestart) {
                                    player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                                            "Server will restart in 60 seconds to apply the update.");
                                } else {
                                    player.sendMessage(ChatColor.GREEN + "[MovingBlocks] " + ChatColor.YELLOW + 
                                            "Update will be applied on next server restart.");
                                }
                            }
                        }
                    }
                    
                    if (autoRestart) {
                        scheduleRestart();
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to download update: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    private void prepareInstallation(File updateFile) {
        try {
            File pluginFile = new File(UpdateChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backupFile = new File(plugin.getDataFolder(), "backups/movingblocks-" + currentVersion + "-backup-" + timestamp + ".jar");
            Files.copy(pluginFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backup of current version created at: " + backupFile.getAbsolutePath());
            
            createShutdownHook(updateFile, pluginFile, backupFile);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to prepare update installation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createShutdownHook(final File updateFile, final File pluginFile, final File backupFile) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("[MovingBlocks] Installing update from " + updateFile.getAbsolutePath() + " to " + pluginFile.getAbsolutePath());
                
                Files.copy(updateFile.toPath(), pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                updateFile.delete();
                
                System.out.println("[MovingBlocks] Update installed successfully!");
                System.out.println("[MovingBlocks] Backup of previous version: " + backupFile.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[MovingBlocks] Failed to install update: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }
    
    private void downloadFile(String downloadUrl, File target) throws IOException {
        plugin.getLogger().info("Downloading from URL: " + downloadUrl);
        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        
        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;
            int contentLength = connection.getContentLength();
            
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                if (contentLength > 0 && totalBytesRead % 10240 == 0) {
                    int progress = (int) (totalBytesRead * 100 / contentLength);
                    plugin.getLogger().info("Download progress: " + progress + "% (" + totalBytesRead + " / " + contentLength + " bytes)");
                }
            }
        }
    }
    
    private void scheduleRestart() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "[Server] " + ChatColor.RED + 
                        "Server will restart in 60 seconds to apply plugin updates!");
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "[Server] " + ChatColor.RED + 
                                "Server is restarting now to apply updates!");
                        
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Bukkit.shutdown();
                            }
                        }.runTaskLater(plugin, 5 * 20);
                    }
                }.runTaskLater(plugin, 60 * 20);
            }
        }.runTask(plugin);
    }
    
    public void manualUpdate() {
        if (updateAvailable && latestVersion != null && latestJarName != null) {
            downloadAndInstallUpdate();
        } else {
            plugin.getLogger().info("No update available or update check has not completed yet.");
        }
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
    
    public boolean isAutoUpdate() {
        return autoUpdate;
    }
    
    public boolean isAutoRestart() {
        return autoRestart;
    }
}