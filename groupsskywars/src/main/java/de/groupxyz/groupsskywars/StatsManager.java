package de.groupxyz.groupsskywars;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {
    private final File statsFile = new File("stats.json");
    private final Gson gson = new Gson();
    private Map<String, PlayerStats> stats;

    public StatsManager() {
        loadStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            stats = new HashMap<>();
            saveStats();
        } else {
            try (Reader reader = new FileReader(statsFile)) {
                Type type = new TypeToken<Map<String, PlayerStats>>() {}.getType();
                stats = gson.fromJson(reader, type);
            } catch (IOException e) {
                e.printStackTrace();
                stats = new HashMap<>();
            }
        }
    }

    private void saveStats() {
        try (Writer writer = new FileWriter(statsFile)) {
            gson.toJson(stats, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void incrementStat(UUID playerUUID, String stat) {
        incrementStat(playerUUID, stat, 1);
    }

    public void incrementStat(UUID playerUUID, String stat, int amount) {
        String uuid = playerUUID.toString();
        stats.putIfAbsent(uuid, new PlayerStats());
        PlayerStats playerStats = stats.get(uuid);

        switch (stat) {
            case "wins" -> playerStats.wins += amount;
            case "deaths" -> playerStats.deaths += amount;
            case "participations" -> playerStats.participations += amount;
            case "kills" -> playerStats.kills += amount;
            case "assists" -> playerStats.assists += amount;
            case "damageDealt" -> playerStats.damageDealt += amount;
            case "damageTaken" -> playerStats.damageTaken += amount;
            case "blocksPlaced" -> playerStats.blocksPlaced += amount;
            case "blocksBroken" -> playerStats.blocksBroken += amount;
            case "chestsOpened" -> playerStats.chestsOpened += amount;
            case "arrowsShot" -> playerStats.arrowsShot += amount;
            case "arrowsHit" -> playerStats.arrowsHit += amount;
            case "playTime" -> playerStats.playTime += amount;
        }

        saveStats();
    }

    public PlayerStats getStats(UUID playerUUID) {
        return stats.getOrDefault(playerUUID.toString(), new PlayerStats());
    }

    public Map<UUID, PlayerStats> getAllStats() {
        Map<UUID, PlayerStats> result = new HashMap<>();
        for (Map.Entry<String, PlayerStats> entry : stats.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                result.put(uuid, entry.getValue());
            } catch (IllegalArgumentException e) {
            }
        }
        return result;
    }

    public static class PlayerStats {
        public int wins = 0;
        public int deaths = 0;
        public int participations = 0;
        public int kills = 0;
        public int assists = 0;
        public int damageDealt = 0;
        public int damageTaken = 0;
        public int blocksPlaced = 0;
        public int blocksBroken = 0;
        public int chestsOpened = 0;
        public int arrowsShot = 0;
        public int arrowsHit = 0;
        public int playTime = 0; // in seconds

        // Berechnete Stats
        public double getKDRatio() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }

        public double getWinRate() {
            return participations == 0 ? 0 : (double) wins / participations * 100;
        }

        public double getArrowAccuracy() {
            return arrowsShot == 0 ? 0 : (double) arrowsHit / arrowsShot * 100;
        }

        public int getScore() {
            return (wins * 10) + (kills * 3) + (assists * 1) - (deaths * 2);
        }

        public String getPlayTimeFormatted() {
            int hours = playTime / 3600;
            int minutes = (playTime % 3600) / 60;
            int seconds = playTime % 60;
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
    }
}

