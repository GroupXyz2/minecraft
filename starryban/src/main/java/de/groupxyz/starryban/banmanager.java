package de.groupxyz.starryban;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class banmanager {

    private final File dataFile;
    private final FileConfiguration dataConfig;
    public final Map<UUID, Long> bannedPlayers = new HashMap<>();

    public banmanager(JavaPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), "banned_players.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        loadBannedPlayers();
    }

    public void banPlayer(OfflinePlayer player, Long banDurationMilliseconds) {
        UUID playerId = player.getUniqueId();
        long banEndTime = banDurationMilliseconds == null ? Long.MAX_VALUE : System.currentTimeMillis() + banDurationMilliseconds;
        dataConfig.set(playerId.toString(), banEndTime);
        bannedPlayers.put(playerId, banEndTime);
        saveData();
    }

    public void unbanPlayer(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        dataConfig.set(playerId.toString(), null);
        bannedPlayers.remove(playerId);
        saveData();
    }

    public long getUnbanTime(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        Long unbanTime = bannedPlayers.get(playerId);
        return unbanTime != null ? unbanTime : 0;
    }

    public boolean isBanned(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        return bannedPlayers.containsKey(playerId) && dataConfig.contains(playerId.toString());
    }

    public Set<String> getBannedPlayerIds() {
        return dataConfig.getKeys(false);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadBannedPlayers() {
        for (String playerId : dataConfig.getKeys(false)) {
            Long unbanTime = dataConfig.getLong(playerId);
            UUID uuid = UUID.fromString(playerId);
            bannedPlayers.put(uuid, unbanTime);
        }
    }
}

