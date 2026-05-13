package de.groupxyz.groupsskywars;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameManager {
    private final Groupsskywars plugin;
    private final Map<String, GameInstance> gameInstances = new ConcurrentHashMap<>();
    private final Map<String, MultiInstanceGameController> controllers = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToInstance = new ConcurrentHashMap<>();
    private int nextInstanceId = 1;

    public GameManager(Groupsskywars plugin) {
        this.plugin = plugin;
    }

    public GameInstance createGameInstance() {
        String instanceId = "game_" + nextInstanceId++;
        GameInstance instance = new GameInstance(instanceId, plugin);

        MultiInstanceGameController controller = new MultiInstanceGameController(plugin, instance);

        String selectedMap = plugin.getForcedMapName() != null ? plugin.getForcedMapName() : plugin.getRandomWorldName();
        if (selectedMap != null) {
            instance.setSelectedMapName(selectedMap);

            int spawnPointCount = controller.getSpawnPointCountForWorld(selectedMap);
            if (spawnPointCount > 0) {
                instance.setInitialMaxPlayers(spawnPointCount);
                plugin.getLogger().info("Instanz " + instanceId + " erstellt mit Map " + selectedMap + " (" + spawnPointCount + " Spawns)");
            } else {
                int configMaxPlayers = plugin.getMaxPlayers();
                if (configMaxPlayers > 0) {
                    instance.setInitialMaxPlayers(configMaxPlayers);
                }
                plugin.getLogger().warning("Instanz " + instanceId + ": Keine Spawnpunkte für Map " + selectedMap + " gefunden!");
            }
        } else {
            int configMaxPlayers = plugin.getMaxPlayers();
            instance.setInitialMaxPlayers(configMaxPlayers);
            plugin.getLogger().warning("Instanz " + instanceId + ": Keine Map verfügbar!");
        }

        gameInstances.put(instanceId, instance);
        controllers.put(instanceId, controller);

        plugin.getLogger().info("Neue Spielinstanz erstellt: " + instanceId);
        return instance;
    }

    public MultiInstanceGameController getController(String instanceId) {
        return controllers.get(instanceId);
    }

    public MultiInstanceGameController getController(GameInstance instance) {
        if (instance == null) return null;
        return controllers.get(instance.getInstanceId());
    }

    public GameInstance findAvailableInstance() {
        for (GameInstance instance : gameInstances.values()) {
            if (instance.isCountdown() && !instance.isGameStarted()) {
                if (instance.getMaxPlayers() <= 0 || instance.getPlayerCount() < instance.getMaxPlayers()) {
                    return instance;
                }
            }
        }

        for (GameInstance instance : gameInstances.values()) {
            if (!instance.isCountdown() && !instance.isGameStarted()) {
                if (instance.getMaxPlayers() <= 0 || instance.getPlayerCount() < instance.getMaxPlayers()) {
                    return instance;
                }
            }
        }

        return null;
    }

    public GameInstance getPlayerInstance(UUID playerId) {
        String instanceId = playerToInstance.get(playerId);
        if (instanceId != null) {
            return gameInstances.get(instanceId);
        }
        return null;
    }

    public GameInstance getInstanceByWorld(World world) {
        if (world == null) return null;

        for (GameInstance instance : gameInstances.values()) {
            if (instance.getSkywarsWorld() != null &&
                instance.getSkywarsWorld().equals(world)) {
                return instance;
            }
        }
        return null;
    }

    public void assignPlayerToInstance(UUID playerId, GameInstance instance) {
        GameInstance oldInstance = getPlayerInstance(playerId);
        if (oldInstance != null) {
            oldInstance.removePlayer(playerId);
        }

        playerToInstance.put(playerId, instance.getInstanceId());
        instance.addParticipant(playerId);
    }

    public void removePlayer(UUID playerId) {
        GameInstance instance = getPlayerInstance(playerId);
        if (instance != null) {
            instance.removePlayer(playerId);
        }
        playerToInstance.remove(playerId);
    }

    public void removeInstance(String instanceId) {
        GameInstance instance = gameInstances.get(instanceId);
        if (instance != null) {
            for (UUID playerId : new HashSet<>(instance.getParticipants())) {
                playerToInstance.remove(playerId);
            }
            for (UUID playerId : new HashSet<>(instance.getSpectators())) {
                playerToInstance.remove(playerId);
            }

            instance.cleanup();
            gameInstances.remove(instanceId);
            controllers.remove(instanceId);
            plugin.getLogger().info("Spielinstanz entfernt: " + instanceId);
        }
    }

    public Collection<GameInstance> getAllInstances() {
        return new ArrayList<>(gameInstances.values());
    }

    public List<GameInstance> getRunningGames() {
        return gameInstances.values().stream()
            .filter(GameInstance::isGameStarted)
            .collect(Collectors.toList());
    }

    public List<GameInstance> getCountdownGames() {
        return gameInstances.values().stream()
            .filter(GameInstance::isCountdown)
            .collect(Collectors.toList());
    }

    public boolean isPlayerInGame(UUID playerId) {
        GameInstance instance = getPlayerInstance(playerId);
        return instance != null && instance.isGameStarted();
    }

    public int getTotalActivePlayers() {
        return playerToInstance.size();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_instances", gameInstances.size());
        stats.put("running_games", getRunningGames().size());
        stats.put("countdown_games", getCountdownGames().size());
        stats.put("total_players", getTotalActivePlayers());
        return stats;
    }

    public void cleanupEmptyInstances() {
        List<String> toRemove = new ArrayList<>();

        for (GameInstance instance : gameInstances.values()) {
            if (!instance.isGameStarted() && !instance.isCountdown() &&
                instance.getTotalPlayerCount() == 0) {
                toRemove.add(instance.getInstanceId());
            }
            else if (instance.isGameStarted() && instance.getTotalPlayerCount() == 0) {
                plugin.getLogger().info("[gsw] No players online in instance " + instance.getInstanceId() + " - automatically stopping the round...");
                MultiInstanceGameController controller = getController(instance);
                if (controller != null) {
                    controller.endGame(null);
                }
            }
        }

        for (String instanceId : toRemove) {
            removeInstance(instanceId);
        }
    }
}

