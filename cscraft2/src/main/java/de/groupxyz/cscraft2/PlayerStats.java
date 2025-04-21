package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStats implements Listener {
    private final Cscraft2 plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private final Map<UUID, Map<String, Integer>> playerStats = new HashMap<>();

    private final String[] statTypes = {
            "wins", "losses", "kills", "deaths", "bombsPlanted",
            "bombsDefused", "roundsPlayed", "damage"
    };

    public PlayerStats(Cscraft2 plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "playerstats.yml");
        loadStats();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Konnte Statistik-Datei nicht erstellen: " + e.getMessage());
            }
        }

        statsConfig = YamlConfiguration.loadConfiguration(statsFile);

        for (String uuidStr : statsConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Integer> stats = new HashMap<>();

                for (String statType : statTypes) {
                    stats.put(statType, statsConfig.getInt(uuidStr + "." + statType, 0));
                }

                playerStats.put(uuid, stats);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ungültige UUID in stats.yml: " + uuidStr);
            }
        }
    }

    public void saveStats() {
        for (Map.Entry<UUID, Map<String, Integer>> entry : playerStats.entrySet()) {
            String uuidStr = entry.getKey().toString();
            Map<String, Integer> stats = entry.getValue();

            for (String statType : statTypes) {
                statsConfig.set(uuidStr + "." + statType, stats.getOrDefault(statType, 0));
            }
        }

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Fehler beim Speichern der Statistiken: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!playerStats.containsKey(uuid)) {
            Map<String, Integer> defaultStats = new HashMap<>();
            for (String statType : statTypes) {
                defaultStats.put(statType, 0);
            }
            playerStats.put(uuid, defaultStats);
        }
    }

    public void incrementStat(Player player, String statType) {
        if (player == null) {
            Bukkit.getLogger().warning("Player is null in increment stat!");
            return;
        }
        incrementStat(player, statType, 1);
    }

    public void incrementStat(Player player, String statType, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> stats = playerStats.computeIfAbsent(uuid, k -> new HashMap<>());

        int currentValue = stats.getOrDefault(statType, 0);
        stats.put(statType, currentValue + amount);
        saveStats();
        //Bukkit.getLogger().info("Updated stats for " + statType + " from " + currentValue + " to " + amount);
    }

    public int getStat(Player player, String statType) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> stats = playerStats.getOrDefault(uuid, new HashMap<>());
        return stats.getOrDefault(statType, 0);
    }

    public void registerWin(String winningTeam) {
        for (Player player : plugin.getGameManager().getPlayers()) {
            String playerTeam = plugin.getGameManager().getTeamManager().getPlayerTeam(player);
            if (playerTeam != null) {
                if (playerTeam.equals(winningTeam)) {
                    incrementStat(player, "wins");
                } else {
                    incrementStat(player, "losses");
                }
            }
        }
    }

    public void showStats(Player viewer, OfflinePlayer target) {
        boolean isSelf = viewer.equals(target);
        boolean isAdmin = viewer.isOp();

        loadStats();

        if (!isSelf && !isAdmin) {
            viewer.sendMessage(ChatColor.RED + "Du kannst nur deine eigenen Statistiken sehen!");
            return;
        }

        UUID uuid = target.getUniqueId();
        if (!playerStats.containsKey(uuid)) {
            viewer.sendMessage(ChatColor.RED + "Keine Statistiken für " + target.getName() + " gefunden!");
            return;
        }

        Map<String, Integer> stats = playerStats.get(uuid);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        viewer.sendMessage(ChatColor.GOLD + "=== Statistiken: " + target.getName() + " ===");
        viewer.sendMessage(ChatColor.YELLOW + "Siege: " + ChatColor.GREEN + stats.getOrDefault("wins", 0));
        viewer.sendMessage(ChatColor.YELLOW + "Niederlagen: " + ChatColor.RED + stats.getOrDefault("losses", 0));

        int wins = stats.getOrDefault("wins", 0);
        int losses = stats.getOrDefault("losses", 0);
        double wlRatio = losses > 0 ? (double) wins / losses : wins;
        viewer.sendMessage(ChatColor.YELLOW + "W/L-Verhältnis: " + ChatColor.AQUA + df.format(wlRatio));

        viewer.sendMessage(ChatColor.YELLOW + "Kills: " + ChatColor.GREEN + stats.getOrDefault("kills", 0));
        viewer.sendMessage(ChatColor.YELLOW + "Tode: " + ChatColor.RED + stats.getOrDefault("deaths", 0));

        int kills = stats.getOrDefault("kills", 0);
        int deaths = stats.getOrDefault("deaths", 0);
        double kdRatio = deaths > 0 ? (double) kills / deaths : kills;
        viewer.sendMessage(ChatColor.YELLOW + "K/D-Verhältnis: " + ChatColor.AQUA + df.format(kdRatio));

        viewer.sendMessage(ChatColor.YELLOW + "Bomben platziert: " + ChatColor.LIGHT_PURPLE + stats.getOrDefault("bombsPlanted", 0));
        viewer.sendMessage(ChatColor.YELLOW + "Bomben entschärft: " + ChatColor.LIGHT_PURPLE + stats.getOrDefault("bombsDefused", 0));
        viewer.sendMessage(ChatColor.YELLOW + "Gespielte Runden: " + ChatColor.BLUE + stats.getOrDefault("roundsPlayed", 0));
    }

    public void showTopStats(Player player, String statType, int count) {
        if (!isValidStatType(statType)) {
            player.sendMessage(ChatColor.RED + "Ungültiger Statistiktyp! Verfügbare Typen: " + String.join(", ", statTypes));
            return;
        }

        Map<String, Integer> topPlayers = new HashMap<>();

        for (Map.Entry<UUID, Map<String, Integer>> entry : playerStats.entrySet()) {
            UUID uuid = entry.getKey();
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName != null) {
                int value = entry.getValue().getOrDefault(statType, 0);
                topPlayers.put(playerName, value);
            }
        }

        if (topPlayers.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Keine Statistiken verfügbar!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Top " + count + " Spieler - " + statType + " ===");

        topPlayers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .forEach(entry -> {
                    player.sendMessage(ChatColor.YELLOW + entry.getKey() + ": " + ChatColor.GREEN + entry.getValue());
                });
    }

    private boolean isValidStatType(String statType) {
        for (String type : statTypes) {
            if (type.equalsIgnoreCase(statType)) {
                return true;
            }
        }
        return false;
    }

    public void resetStats(Player target) {
        UUID uuid = target.getUniqueId();
        Map<String, Integer> defaultStats = new HashMap<>();
        for (String statType : statTypes) {
            defaultStats.put(statType, 0);
        }
        playerStats.put(uuid, defaultStats);
        saveStats();
    }

    public void debugStats(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerStats.containsKey(uuid)) {
            plugin.getLogger().info("Keine Statistiken für " + player.getName() + " gefunden!");
            return;
        }

        Map<String, Integer> stats = playerStats.get(uuid);
        plugin.getLogger().info("=== Statistiken für " + player.getName() + " ===");
        for (String statType : statTypes) {
            int value = stats.getOrDefault(statType, 0);
            plugin.getLogger().info(statType + ": " + value);
        }
    }

    public void enableAutoSave() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveStats, 6000L, 6000L); // Alle 5 Minuten
        plugin.getLogger().info("Stats auto-save enabled.");
    }
}