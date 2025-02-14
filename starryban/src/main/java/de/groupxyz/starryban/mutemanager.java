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

public class mutemanager {

    private final File dataFile;
    private final FileConfiguration dataConfig;
    private final Map<UUID, Long> mutedPlayers = new HashMap<>();

    public mutemanager(JavaPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), "muted_players.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        loadMutedPlayers();
    }

    public void mutePlayer(OfflinePlayer player, long muteDurationMilliseconds) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        dataConfig.set(playerId.toString(), System.currentTimeMillis() + muteDurationMilliseconds);
        mutedPlayers.put(playerId, System.currentTimeMillis() + muteDurationMilliseconds);
        saveData();
    }

    public void unmutePlayer(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        dataConfig.set(playerId.toString(), null);
        saveData();
    }

    public long getUnmuteTime(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        Long unmuteTime = mutedPlayers.get(playerId);
        return unmuteTime != null ? unmuteTime : 0;
    }

    public boolean isMuted(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();
        return dataConfig.contains(playerId.toString());
    }

    public Set<String> getMutedPlayerIds() {
        return dataConfig.getKeys(false);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMutedPlayers() {
        for (String playerId : dataConfig.getKeys(false)) {
            Long unmuteTime = dataConfig.getLong(playerId);
            UUID uuid = UUID.fromString(playerId);
            mutedPlayers.put(uuid, unmuteTime);
        }
    }
}
