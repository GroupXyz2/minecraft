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
        String uuid = playerUUID.toString();
        stats.putIfAbsent(uuid, new PlayerStats());
        PlayerStats playerStats = stats.get(uuid);

        switch (stat) {
            case "wins" -> playerStats.wins++;
            case "deaths" -> playerStats.deaths++;
            case "participations" -> playerStats.participations++;
        }

        saveStats();
    }

    public PlayerStats getStats(UUID playerUUID) {
        return stats.getOrDefault(playerUUID.toString(), new PlayerStats());
    }

    public static class PlayerStats {
        public int wins;
        public int deaths;
        public int participations;
    }
}

