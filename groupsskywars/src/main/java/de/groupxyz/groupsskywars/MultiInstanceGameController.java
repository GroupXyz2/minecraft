package de.groupxyz.groupsskywars;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class MultiInstanceGameController {
    private final Groupsskywars plugin;
    private final GameInstance instance;

    public MultiInstanceGameController(Groupsskywars plugin, GameInstance instance) {
        this.plugin = plugin;
        this.instance = instance;
    }

    public boolean loadWorld(String worldName) {
        File worldsFolder = plugin.getWorldsFolder();
        File sourceWorld = new File(worldsFolder, worldName);

        if (!sourceWorld.exists()) {
            plugin.getLogger().severe("World " + worldName + " not found!");
            return false;
        }

        String uniqueWorldName = worldName + "_" + instance.getInstanceId();
        File targetWorld = new File(Bukkit.getWorldContainer(), uniqueWorldName);

        try {
            copyWorld(sourceWorld, targetWorld);

            WorldCreator creator = new WorldCreator(uniqueWorldName);
            World world = Bukkit.createWorld(creator);

            if (world != null) {
                instance.setSkywarsWorld(world);
                world.setDifficulty(Difficulty.NORMAL);
                world.setPVP(true);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(6000);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);

                List<Location> spawnPoints = loadSpawnPointsForWorld(world, worldName);
                instance.setSpawnPoints(spawnPoints);

                plugin.loadLuckyBlocksForInstance(instance, worldName);

                plugin.getLogger().info("[" + instance.getInstanceId() + "] World loaded with " + spawnPoints.size() + " spawn points");
                return true;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[" + instance.getInstanceId() + "] Error loading world: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public int getSpawnPointCountForWorld(String configWorldName) {
        List<?> spawnPointList = plugin.getConfig().getList("spawnPoints");
        if (spawnPointList == null) return 0;

        return (int) spawnPointList.stream()
            .filter(o -> {
                if (o instanceof LinkedHashMap) {
                    LinkedHashMap<?, ?> map = (LinkedHashMap<?, ?>) o;
                    String configWorld = (String) map.get("world");
                    return configWorld != null && configWorld.equals(configWorldName);
                }
                return false;
            })
            .count();
    }

    private List<Location> loadSpawnPointsForWorld(World world, String configWorldName) {
        List<?> spawnPointList = new ArrayList<>(plugin.getConfig().getList("spawnPoints"));

        return spawnPointList.stream()
            .map(o -> {
                if (o instanceof LinkedHashMap) {
                    LinkedHashMap<?, ?> map = (LinkedHashMap<?, ?>) o;
                    String configWorld = (String) map.get("world");

                    if (configWorld.equals(configWorldName)) {
                        double x = ((Number) map.get("x")).doubleValue();
                        double y = ((Number) map.get("y")).doubleValue();
                        double z = ((Number) map.get("z")).doubleValue();
                        float pitch = ((Number) map.get("pitch")).floatValue();
                        float yaw = ((Number) map.get("yaw")).floatValue();

                        return new Location(world, x, y, z, yaw, pitch);
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    public void startCountdown() {
        if (instance.isCountdown()) {
            return;
        }

        instance.setCountdown(true);

        BukkitRunnable task = new BukkitRunnable() {
            int countdown = calculateCountdownTime();
            int lastPlayerCount = instance.getPlayerCount();
            boolean alreadyAdjusted = false;

            @Override
            public void run() {
                int currentPlayers = instance.getPlayerCount();

                if (instance.getMaxPlayers() > 0 && currentPlayers >= instance.getMaxPlayers() && countdown > 3) {
                    countdown = 3;
                    broadcastToInstance(ChatColor.GREEN + "Maximale Spieleranzahl erreicht! Schnellstart in " + countdown + " Sekunden!");
                }

                if (!alreadyAdjusted && currentPlayers > lastPlayerCount && countdown > 10) {
                    int expectedCountdown = calculateCountdownTime();
                    if (expectedCountdown > countdown) {
                        countdown = expectedCountdown;
                        broadcastToInstance(ChatColor.YELLOW + "Neue Spieler sind beigetreten! Countdown angepasst: " + countdown + " Sekunden");
                        alreadyAdjusted = true;
                        lastPlayerCount = currentPlayers;
                    }
                }

                if (countdown == 60 || countdown == 30 || countdown == 20) {
                    for (Player player : instance.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                } else if (countdown == 10) {
                    for (Player player : instance.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    }
                } else if (countdown <= 5 && countdown > 0) {
                    for (Player player : instance.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    }
                }

                if (countdown <= 0) {
                    teleportPlayersToGame();
                    cancel();
                    instance.setCountdown(false);
                } else if (countdown % 10 == 0 || countdown <= 5) {
                    broadcastToInstance(ChatColor.DARK_PURPLE + "[" + instance.getInstanceId() + "] Die Runde startet in " + countdown + " Sekunden...");
                }
                countdown--;
            }
        };

        instance.setCountdownTask(task.runTaskTimer(plugin, 0L, 20L));
    }

    private int calculateCountdownTime() {
        int currentPlayers = instance.getPlayerCount();

        if (instance.getMaxPlayers() > 0 && currentPlayers >= instance.getMaxPlayers()) {
            return 3;
        }

        int additionalPlayers = Math.max(0, currentPlayers - plugin.getMinPlayers());
        int calculatedTime = 10 + (additionalPlayers * 10);

        return Math.min(calculatedTime, 60);
    }

    public void cancelCountdown() {
        if (!instance.isCountdown()) {
            return;
        }

        BukkitTask task = instance.getCountdownTask();
        if (task != null) {
            task.cancel();
        }

        instance.setCountdown(false);
        instance.setCountdownTask(null);
    }

    private void teleportPlayersToGame() {
        List<Location> spawnPoints = getSpawnPointsForInstance();

        if (spawnPoints == null || spawnPoints.isEmpty()) {
            plugin.getLogger().severe("[" + instance.getInstanceId() + "] No spawn points available!");
            return;
        }

        instance.setGameStarted(true);
        instance.setCountdown(false);

        List<Player> players = instance.getOnlinePlayers();

        for (int i = 0; i < players.size() && i < spawnPoints.size(); i++) {
            Player player = players.get(i);
            Location spawnLocation = spawnPoints.get(i);

            if (spawnLocation.getWorld() != null) {
                player.teleport(spawnLocation);
                player.setGameMode(GameMode.ADVENTURE);
                instance.setCanMoveInGame(false);
                player.getInventory().clear();
                player.setSaturation(20.0f);
                player.setFoodLevel(20);
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1.0f, 1.0f);
                instance.setGameStartTime(player.getUniqueId());
            }
        }

        for (int i = spawnPoints.size(); i < players.size(); i++) {
            Player player = players.get(i);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(spawnPoints.get(0));
            player.sendMessage(ChatColor.GOLD + "[" + instance.getInstanceId() + "] Die Runde ist voll! Du bist jetzt Zuschauer.");
            instance.addSpectator(player.getUniqueId());
        }

        plugin.getLogger().info("[" + instance.getInstanceId() + "] Game started with " + Math.min(players.size(), spawnPoints.size()) + " players");
        startInGameCountdown();
    }

    private void startInGameCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown <= 0) {
                    instance.setCanMoveInGame(true);
                    for (Player player : instance.getOnlinePlayers()) {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setMaxHealth(20.0);
                        player.setHealth(20.0);

                        Kit kit = plugin.getKitManager().getPlayerKit(player);
                        if (kit != null) {
                            kit.applyKit(player);
                        }

                        player.sendTitle(ChatColor.LIGHT_PURPLE + "Spiel beginnt jetzt!", "", 10, 70, 20);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                    cancel();

                    broadcastToInstance(ChatColor.GOLD + "Zone schrumpft in 5 Minuten!");

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (instance.isEndPhase()) {
                                cancel();
                                return;
                            }
                            broadcastToInstance(ChatColor.RED + "Zone beginnt jetzt zu schrumpfen!");
                            startZoneShrinking();
                        }
                    }.runTaskLater(plugin, 20L * 60 * 5);

                    startLootDropTask();
                } else {
                    broadcastToInstance(ChatColor.DARK_PURPLE + "Das Spiel startet in " + countdown + " Sekunden...");
                    for (Player player : instance.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                countdown--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startZoneShrinking() {
        World world = instance.getSkywarsWorld();
        if (world == null) return;

        List<Location> spawnPoints = getSpawnPointsForInstance();
        if (spawnPoints == null || spawnPoints.isEmpty()) return;

        double avgX = spawnPoints.stream().mapToDouble(Location::getX).average().orElse(0);
        double avgY = spawnPoints.stream().mapToDouble(Location::getY).average().orElse(64);
        double avgZ = spawnPoints.stream().mapToDouble(Location::getZ).average().orElse(0);

        Location zoneCenter = new Location(world, avgX, avgY, avgZ);

        ZoneManager zoneManager = new ZoneManager(zoneCenter, 200, 300);
        instance.setZoneManager(zoneManager);
        zoneManager.startShrinkingZone();
    }

    public void endGame(@Nullable UUID winnerId) {
        World world = instance.getSkywarsWorld();

        if (world == null) {
            plugin.getLogger().warning("[" + instance.getInstanceId() + "] endGame() called but world is null");
            instance.setGameStarted(false);
            instance.setEndPhase(false);
            plugin.getGameManager().removeInstance(instance.getInstanceId());
            return;
        }

        instance.setEndPhase(false);
        plugin.getScoreboardManager().stopGame();

        if (instance.getZoneManager() != null) {
            instance.getZoneManager().stop();
            instance.setZoneManager(null);
        }

        for (Player player : instance.getAllOnlinePlayers()) {
            player.sendActionBar(Component.empty());
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.CONFUSION);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
            instance.trackPlayTime(player);
            plugin.getKitManager().resetPlayerData(player);
        }

        Player winner = null;
        if (winnerId != null) {
            winner = Bukkit.getPlayer(winnerId);
        }

        if (winner != null) {
            broadcastToInstance(ChatColor.LIGHT_PURPLE + winner.getName() + " hat die Runde gewonnen!");
            plugin.getStatsManager().incrementStat(winner.getUniqueId(), "wins");
            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            }
        } else {
            broadcastToInstance(ChatColor.DARK_PURPLE + "[" + instance.getInstanceId() + "] Die Runde wurde beendet!");
        }

        for (Player player : world.getPlayers()) {
            player.teleport(plugin.getLobbyWorld().getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            try {
                player.setHealth(20.0);
                player.setSaturation(20.0f);
                player.setFoodLevel(20);
            } catch (Exception ignored) {}
        }

        Bukkit.unloadWorld(world, false);

        deleteWorld(world.getWorldFolder());

        instance.cleanup();
        instance.setGameStarted(false);

        plugin.getGameManager().removeInstance(instance.getInstanceId());

        plugin.getLogger().info("[" + instance.getInstanceId() + "] Game ended and cleaned up");
    }

    public void checkRemainingPlayers() {
        if (instance.getSkywarsWorld() == null || !instance.isGameStarted()) {
            return;
        }

        List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.getWorld().equals(instance.getSkywarsWorld()))
            .filter(player -> player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            .collect(java.util.stream.Collectors.toList());

        if (alivePlayers.size() == 1) {
            Player winner = alivePlayers.get(0);
            instance.setEndPhase(true);

            for (Player player : instance.getAllOnlinePlayers()) {
                player.sendMessage(ChatColor.DARK_PURPLE + "[" + instance.getInstanceId() + "] Runde beendet. Stoppe in 5 Sekunden!");
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> endGame(winner.getUniqueId()), 20 * 5);
        } else if (alivePlayers.isEmpty()) {
            instance.setEndPhase(true);

            for (Player player : instance.getAllOnlinePlayers()) {
                player.sendMessage(ChatColor.DARK_RED + "[" + instance.getInstanceId() + "] Keine Spieler mehr am Leben. Runde wird gestoppt!");
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> endGame(null), 20 * 2);
        }
    }

    private void broadcastToInstance(String message) {
        for (Player player : instance.getAllOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private List<Location> getSpawnPointsForInstance() {
        World world = instance.getSkywarsWorld();
        if (world == null) return Collections.emptyList();

        return loadSpawnPointsForWorld(world, world.getName().split("_")[0]);
    }

    private void copyWorld(File source, File target) throws IOException {
        Files.walk(source.toPath()).forEach(sourcePath -> {
            File destination = new File(target, source.toPath().relativize(sourcePath).toString());
            try {
                if (!destination.exists()) destination.getParentFile().mkdirs();
                Files.copy(sourcePath, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void deleteWorld(File path) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteWorld(file);
                }
            }
        }
        path.delete();
    }

    private void startLootDropTask() {
        if (Bukkit.getPluginManager().getPlugin("lootdrop") == null) {
            plugin.getLogger().warning("[" + instance.getInstanceId() + "] Lootdrop-Plugin not found, skipping lootdrop function.");
            return;
        }

        new BukkitRunnable() {
            boolean isFirstRun = true;

            @Override
            public void run() {
                if (!instance.isGameStarted() || instance.getSkywarsWorld() == null) {
                    cancel();
                    return;
                }

                if (!isFirstRun) {
                    int x = (int) (Math.random() * 101) - 50;
                    int z = (int) (Math.random() * 101) - 50;
                    int y = plugin.getConfig().getInt("lootDropHeight", 100);

                    String command = String.format("lootdrop %s %d %d %d", instance.getSkywarsWorld().getName(), x, y, z);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    for (Player player : instance.getOnlinePlayers()) {
                        if (player.getWorld().equals(instance.getSkywarsWorld())) {
                            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                            player.sendMessage(ChatColor.GOLD + "[" + instance.getInstanceId() + "] Ein Loot Drop ist erschienen!");
                        }
                    }
                }
                isFirstRun = false;
                scheduleNextLootDrop();
            }
        }.runTaskLater(plugin, 0L);
    }

    private void scheduleNextLootDrop() {
        int nextDelay = 30 * 20 + (int) (Math.random() * 31 * 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!instance.isGameStarted() || instance.getSkywarsWorld() == null) {
                    cancel();
                    return;
                }

                int x = (int) (Math.random() * 101) - 50;
                int z = (int) (Math.random() * 101) - 50;
                int y = plugin.getConfig().getInt("lootDropHeight", 100);

                String command = String.format("lootdrop %s %d %d %d", instance.getSkywarsWorld().getName(), x, y, z);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                for (Player player : instance.getOnlinePlayers()) {
                    if (player.getWorld().equals(instance.getSkywarsWorld())) {
                        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                        player.sendMessage(ChatColor.GOLD + "[" + instance.getInstanceId() + "] Ein Loot Drop ist erschienen!");
                    }
                }

                scheduleNextLootDrop();
            }
        }.runTaskLater(plugin, nextDelay);
    }
}

