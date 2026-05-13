package de.groupxyz.groupsskywars;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameInstance {
    private final String instanceId;
    private final Groupsskywars plugin;
    private World skywarsWorld;
    private List<Location> spawnPoints;
    private boolean gameStarted = false;
    private boolean isCountdown = false;
    private boolean canMoveInGame = true;
    private boolean isEndPhase = false;
    private BukkitTask countdownTask;
    private ZoneManager zoneManager;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, Long> gameStartTimes = new HashMap<>();
    private int maxPlayers;
    private String selectedMapName;

    public GameInstance(String instanceId, Groupsskywars plugin) {
        this.instanceId = instanceId;
        this.plugin = plugin;
    }

    public String getInstanceId() { return instanceId; }
    public World getSkywarsWorld() { return skywarsWorld; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isCountdown() { return isCountdown; }
    public boolean isEndPhase() { return isEndPhase; }
    public Set<UUID> getParticipants() { return new HashSet<>(participants); }
    public Set<UUID> getSpectators() { return new HashSet<>(spectators); }
    public int getMaxPlayers() { return maxPlayers; }
    public ZoneManager getZoneManager() { return zoneManager; }
    public boolean canMoveInGame() { return canMoveInGame; }
    public BukkitTask getCountdownTask() { return countdownTask; }
    public String getSelectedMapName() { return selectedMapName; }

    public void setSkywarsWorld(World world) { this.skywarsWorld = world; }
    public void setSelectedMapName(String mapName) { this.selectedMapName = mapName; }
    public void setSpawnPoints(List<Location> spawnPoints) {
        this.spawnPoints = spawnPoints;
        if (spawnPoints != null && this.maxPlayers < 0) {
            this.maxPlayers = spawnPoints.size();
        }
    }
    public void setInitialMaxPlayers(int max) {
        this.maxPlayers = max;
    }
    public void setGameStarted(boolean started) { this.gameStarted = started; }
    public void setCountdown(boolean countdown) { this.isCountdown = countdown; }
    public void setCanMoveInGame(boolean canMove) { this.canMoveInGame = canMove; }
    public void setEndPhase(boolean endPhase) { this.isEndPhase = endPhase; }
    public void setCountdownTask(BukkitTask task) { this.countdownTask = task; }
    public void setZoneManager(ZoneManager manager) { this.zoneManager = manager; }

    public void addParticipant(UUID playerId) {
        participants.add(playerId);
        spectators.remove(playerId);
    }

    public void addSpectator(UUID playerId) {
        spectators.add(playerId);
        participants.remove(playerId);
    }

    public void removePlayer(UUID playerId) {
        participants.remove(playerId);
        spectators.remove(playerId);
        gameStartTimes.remove(playerId);
    }

    public boolean hasPlayer(UUID playerId) {
        return participants.contains(playerId) || spectators.contains(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean isSpectator(UUID playerId) {
        return spectators.contains(playerId);
    }

    public int getPlayerCount() {
        return participants.size();
    }

    public int getTotalPlayerCount() {
        return participants.size() + spectators.size();
    }

    public List<Player> getOnlinePlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public List<Player> getAllOnlinePlayers() {
        List<Player> players = new ArrayList<>();
        Set<UUID> allPlayers = new HashSet<>(participants);
        allPlayers.addAll(spectators);
        for (UUID uuid : allPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public void trackPlayTime(Player player) {
        if (gameStartTimes.containsKey(player.getUniqueId())) {
            long playTime = (System.currentTimeMillis() - gameStartTimes.get(player.getUniqueId())) / 1000;
            plugin.getStatsManager().incrementStat(player.getUniqueId(), "playTime", (int) playTime);
            gameStartTimes.remove(player.getUniqueId());
        }
    }

    public void setGameStartTime(UUID playerId) {
        gameStartTimes.put(playerId, System.currentTimeMillis());
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        isCountdown = false;
    }

    public void cleanup() {
        if (zoneManager != null) {
            zoneManager.stop();
            zoneManager = null;
        }

        cancelCountdown();

        participants.clear();
        spectators.clear();
        gameStartTimes.clear();

        gameStarted = false;
        isEndPhase = false;
    }
}

