package de.groupxyz.groupsskywars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.codehaus.plexus.util.FileUtils.deleteDirectory;

public class Groupsskywars extends JavaPlugin implements Listener {
    private World lobbyWorld;
    private World skywarsWorld;
    private List<Location> spawnPoints;
    private int minPlayers;
    private File worldsFolder;
    private int countdownTaskId;
    private boolean gameStarted = false;
    private boolean isCountdown = false;
    private boolean oldWorldDeletionEnabled;
    private boolean canMoveInGame = true;
    private static final NamespacedKey LUCKY_BLOCK_KEY = new NamespacedKey("groupsskywars", "lucky_block");
    private boolean isDebug;
    private String userSelectedWorld;
    private StatsManager statsManager;
    private ScoreboardManager scoreboardManager;
    public Boolean isEndPhase;
    private FileConfiguration languageConfig;
    private final HashSet<UUID> mapEditorPlayers = new HashSet<>();
    boolean isFirstLootdropRun = true;
    int maxHeight = 100;
    boolean holdStart= false;

    @Override
    public void onEnable() {
        getLogger().info("GroupsSkyWars by GroupXyz starting!");

        saveDefaultConfig();
        loadLanguageConfig();
        minPlayers = getConfig().getInt("minPlayers", 4);
        worldsFolder = new File(getDataFolder(), "skywars_worlds");

        lobbyWorld = Bukkit.getWorld("world");

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, this::checkAndStartGame, 0L, 100L);

        File worldContainer = Bukkit.getWorldContainer();

        List<String> worldNames = getConfig().getStringList("spawnPoints");

        oldWorldDeletionEnabled = getConfig().getBoolean("oldWorldDeletionEnabled", false);

        for (File file : worldContainer.listFiles()) {
            if (file.isDirectory() && worldNames.contains(file.getName())) {
                if (Bukkit.getWorld(file.getName()) == null) {
                    getLogger().info("Discovered old world: " + file.getName());
                    if (oldWorldDeletionEnabled) {
                        getLogger().info("Deleting old world: " + file.getName());
                        try {
                            deleteDirectory(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    getLogger().warning("Old world is loaded and can't be deleted, Plugin should not be enabled twice without restarting the server, did you use Plugman you pesky litte [...] ?");
                }
            }
        }

        isDebug = getConfig().getBoolean("isDebug", false);
        statsManager = new StatsManager();
        scoreboardManager = new ScoreboardManager();
    }

    @Override
    public void onDisable() {
        getLogger().info("GroupsSkyWars shutting down...");

        if (skywarsWorld != null) {
            endGame(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("round")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /round <start|stop|list> (mapname)");
                return true;
            }

            if (args[0].equalsIgnoreCase("start")) {
                if (gameStarted) {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Das Spiel ist bereits gestartet.");
                } else {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Runde wird gestartet...");
                    if (args.length > 1 && args[1] != null) {
                        File[] worldFiles = worldsFolder.listFiles();
                        for (File file : worldFiles) {
                            if (file.getName().equals(args[1])) {
                                userSelectedWorld = args[1];
                                break;
                            }
                        }
                        if (userSelectedWorld == null) {
                            sender.sendMessage(ChatColor.DARK_RED + "Welt " + args[1] + " existiert nicht.");
                            return false;
                        }
                    }
                    loadRandomWorld();
                    startCountdown();
                }
            } else if (args[0].equalsIgnoreCase("stop")) {
                if (isCountdown) {
                    Bukkit.getScheduler().cancelTask(countdownTaskId);
                    isCountdown = false;
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Countdown wurde gestoppt.");
                    return true;
                }
                if (!gameStarted) {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Es existiert derzeit keine Runde.");
                } else {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Runde wird gestoppt...");
                    endGame(null);
                }
            } else if (args[0].equalsIgnoreCase("list")) {
                File[] worldFiles = worldsFolder.listFiles();
                ArrayList<String> names = new ArrayList<>();
                if (worldFiles != null) {
                    for (File file : worldFiles) {
                        names.add(file.getName());
                    }
                }
                if (!names.isEmpty()) {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Verfuegbare Maps: " + names);
                } else {
                    sender.sendMessage(ChatColor.DARK_RED + "Keine Maps verfuegbar!");
                }
            } else if (args[0].equalsIgnoreCase("hold")) {
                if (holdStart == false) {
                    holdStart = true;
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Runde wird gehalten!");
                } else {
                    holdStart = false;
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Runde wird nicht mehr gehalten!");
                }
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Unbekannter Befehl. Verwende /round <start|stop|list|hold>");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("mapeditor")) {
            if (args.length == 1) {

                String mapName = args[0];
                World world = Bukkit.getWorld(mapName);

                if (world == null) {
                    File worldFolder = new File(worldsFolder, mapName);
                    if (worldFolder.exists()) {
                        world = new WorldCreator(worldFolder.getPath()).createWorld();
                        if (world == null) {
                            sender.sendMessage(ChatColor.DARK_RED + "Fehler beim Laden der Welt: " + mapName);
                            return false;
                        }
                    } else {
                        sender.sendMessage(ChatColor.DARK_RED + "Welt " + mapName + " wurde nicht gefunden.");
                        return false;
                    }
                }

                if (sender instanceof Player) {
                    Player player = (Player) sender;

                    player.teleport(world.getSpawnLocation());
                    player.setGameMode(GameMode.CREATIVE);
                    UUID playerId = player.getUniqueId();
                    mapEditorPlayers.add(playerId);
                    player.sendMessage(ChatColor.DARK_PURPLE + "Du befindest dich jetzt im Bearbeitungsmodus der Karte: " + mapName);
                }
            } else if (args.length == 2) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;

                    String mapName = args[0];
                    World world = Bukkit.getWorld(mapName);

                    if (world == null) {
                        File worldFolder = new File(worldsFolder, mapName);
                        if (worldFolder.exists()) {
                            world = Bukkit.getWorld(worldFolder.getPath());
                            if (world == null) {
                                sender.sendMessage(ChatColor.DARK_RED + "Fehler beim unloaden der Welt: " + mapName + ", Server muss vor naechster Runde neugestartet werden!");
                            }
                        } else {
                            sender.sendMessage(ChatColor.DARK_RED + "Editor-Welt: " + mapName + " wurde nicht gefunden.");
                            return false;
                        }
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (world != null) {
                            if (p.getWorld() == world) {
                                p.teleport(lobbyWorld.getSpawnLocation());
                                p.setGameMode(GameMode.ADVENTURE);
                                UUID playerId = p.getUniqueId();
                                if (mapEditorPlayers.contains(playerId)) {
                                    mapEditorPlayers.remove(playerId);
                                }
                            }
                        }
                    }

                    if (world != null) {
                        Bukkit.unloadWorld(world, true);

                        File playerdataFolder = new File(world.getWorldFolder(), "playerdata");
                        if (playerdataFolder.exists()) {
                            playerdataFolder.delete();
                        }
                        File uidFile = new File(world.getWorldFolder(), "uid.dat");
                        if (uidFile.exists()) {
                            uidFile.delete();
                        } else {
                            getLogger().warning("Couldn't delete " + uidFile);
                        }
                    }

                }
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /mapeditor <originalmap_name> (off)");
                return false;
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("stats")) {
            if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (!sender.isOp() && target != sender) {
                    sender.sendMessage(ChatColor.DARK_RED + "Du kannst nur deine Statistiken einsehen!");
                    return false;
                }
                if (target != null) {
                    StatsManager.PlayerStats stats = statsManager.getStats(target.getUniqueId());
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "Statistiken fuer " + target.getName() + ":");
                    sender.sendMessage(ChatColor.AQUA + "Wins: " + stats.wins);
                    sender.sendMessage(ChatColor.AQUA + "Deaths: " + stats.deaths);
                    sender.sendMessage(ChatColor.AQUA + "Teilnahmen: " + stats.participations);
                } else {
                    sender.sendMessage(ChatColor.DARK_RED + "Spieler nicht gefunden.");
                }
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /stats <Spielername>");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("swreload")) {
            if (spawnPoints != null) {
                if (!spawnPoints.isEmpty()) {
                    spawnPoints.clear();
                }
            }
            reloadConfig();
            loadLanguageConfig();
            isDebug = getConfig().getBoolean("isDebug", false);
            minPlayers = getConfig().getInt("minPlayers", 4);
            sender.sendMessage(ChatColor.DARK_PURPLE + "Config reloaded!");
            return true;
        } else if (command.getName().equalsIgnoreCase("setfeature")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /setfeature (luckyblock|spawnpunkt)");
                return false;
            }

            Location loc = player.getLocation();
            FileConfiguration config = getConfig();
            String fullWorldName = player.getWorld().getName();
            String worldName = extractLastPart(fullWorldName);

            if (args[0].equalsIgnoreCase("luckyblock")) {
                List<Map<String, Object>> luckyBlocks = (List<Map<String, Object>>) config.getList("luckyBlocks", new ArrayList<>());
                Map<String, Object> newLuckyBlock = new HashMap<>();
                newLuckyBlock.put("world", worldName);
                newLuckyBlock.put("x", loc.getBlockX());
                newLuckyBlock.put("y", loc.getBlockY());
                newLuckyBlock.put("z", loc.getBlockZ());
                luckyBlocks.add(newLuckyBlock);
                config.set("luckyBlocks", luckyBlocks);
                saveConfig();

                player.sendMessage(ChatColor.DARK_PURPLE + "Luckyblock an der Position " + formatLocation(loc, worldName) + " hinzugefuegt!");
            } else if (args[0].equalsIgnoreCase("spawnpunkt")) {
                List<Map<String, Object>> spawnPoints = (List<Map<String, Object>>) config.getList("spawnPoints", new ArrayList<>());
                Map<String, Object> newSpawnPoint = new HashMap<>();
                newSpawnPoint.put("world", worldName);
                newSpawnPoint.put("x", loc.getBlockX());
                newSpawnPoint.put("y", loc.getBlockY());
                newSpawnPoint.put("z", loc.getBlockZ());
                newSpawnPoint.put("pitch", loc.getPitch());
                newSpawnPoint.put("yaw", loc.getYaw());
                spawnPoints.add(newSpawnPoint);
                config.set("spawnPoints", spawnPoints);
                saveConfig();

                player.sendMessage(ChatColor.DARK_PURPLE + "Spawnpunkt an der Position " + formatLocationWithRotation(loc, worldName) + " hinzugefuegt!");
            } else {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /setfeature (luckyblock|spawnpunkt)");
                return false;
            }
        } else if (command.getName().equalsIgnoreCase("swlanguage")) {
            if (true) {
                sender.sendMessage(ChatColor.DARK_RED + "Noch nicht implementiert!");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.DARK_RED + getLocalizedString("commands.swlanguage_usage"));
                return true;
            }

            String langCode = args[0].toLowerCase();

            if (!Arrays.asList("en", "de", "sp").contains(langCode)) {
                sender.sendMessage(ChatColor.DARK_RED + getLocalizedString("config.invalid_language_code"));
                return true;
            }

            getConfig().set("language", langCode);
            saveConfig();
            reloadConfig();

            loadLanguageConfig();
            sender.sendMessage(ChatColor.DARK_PURPLE + getLocalizedString("config.language_set") + langCode);
            return true;
        }
        return false;
    }

    private String extractLastPart(String fullPath) {
        String[] parts = fullPath.split("/");
        return parts[parts.length - 1];
    }


    private String formatLocation(Location loc, String worldName) {
        return "Welt: " + worldName + ", X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
    }

    private String formatLocationWithRotation(Location loc, String worldName) {
        return formatLocation(loc, worldName) + ", Pitch: " + loc.getPitch() + ", Yaw: " + loc.getYaw();
    }


    private void loadConfigSpawnPoints() {
        List<?> spawnPointList = new ArrayList<>(getConfig().getList("spawnPoints"));

        spawnPoints = spawnPointList.stream()
                .map(o -> {
                    if (o instanceof LinkedHashMap) {
                        LinkedHashMap<?, ?> map = (LinkedHashMap<?, ?>) o;
                        String configWorldName = (String) map.get("world");

                        debugMessage("Lade Spawnpunkt fuer Welt: " + configWorldName);

                        if (skywarsWorld != null && skywarsWorld.getName().equals(configWorldName)) {
                            double x = ((Number) map.get("x")).doubleValue();
                            double y = ((Number) map.get("y")).doubleValue();
                            double z = ((Number) map.get("z")).doubleValue();
                            float pitch = ((Number) map.get("pitch")).floatValue();
                            float yaw = ((Number) map.get("yaw")).floatValue();

                            debugMessage("Lade Koordinaten: " + x + ", " + y + ", " + z);

                            return new Location(skywarsWorld, x, y, z, yaw, pitch);
                        } else {
                            debugMessage("Die Welt " + configWorldName + " stimmt nicht mit der geladenen Welt ueberein, ignoriere Spawnpunkt.");
                        }
                    } else {
                        getLogger().warning("Fehler beim Laden eines Spawnpunkts.");
                    }
                    return null;
                })
                .filter(location -> location != null)
                .collect(Collectors.toList());

        if (spawnPoints.isEmpty()) {
            getLogger().severe("Keine gueltigen Spawnpunkte gefunden. Checke die Konfiguration.");
        } else {
            getLogger().info("Erfolgreich " + spawnPoints.size() + " fuer Welt " + skywarsWorld.getName() + " Spawnpunkte geladen.");
        }
    }

    public void loadLuckyBlocks() {
        List<?> luckyBlockConfigs = getConfig().getList("luckyBlocks");
        if (luckyBlockConfigs == null) return;

        for (Object obj : luckyBlockConfigs) {
            if (obj instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) obj;
                String worldName = (String) map.get("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                Location location = new Location(world, x, y, z);

                Block block = location.getBlock();
                block.setType(Material.SPONGE);
                debugMessage("Set luckyblock at " + location);
            }
        }
    }


    private void checkAndStartGame() {
        if (!gameStarted && Bukkit.getOnlinePlayers().size() >= minPlayers && skywarsWorld == null) {
            if (!holdStart) {
                loadRandomWorld();
                startCountdown();
            }
        }
    }

    private void loadRandomWorld() {
        File[] worldFiles = worldsFolder.listFiles();
        if (worldFiles == null || worldFiles.length == 0) {
            getLogger().severe("No worlds available in " + worldsFolder.getPath());
            return;
        }

        File randomWorld;

        if (userSelectedWorld != null) {
            randomWorld = new File(worldsFolder, userSelectedWorld);
            if (!randomWorld.exists() || !randomWorld.isDirectory()) {
                getLogger().warning("Die ausgewaehlte Welt '" + userSelectedWorld + "' existiert nicht oder ist kein gueltiges Verzeichnis.");
                return;
            }
            getLogger().info("User selected world: " + randomWorld.getName());
        } else {
            randomWorld = worldFiles[new Random().nextInt(worldFiles.length)];
            getLogger().info("Randomly selected world: " + randomWorld.getName());
        }

        File targetWorldFolder = new File(Bukkit.getWorldContainer(), randomWorld.getName());

        try {
            copyWorld(randomWorld, targetWorldFolder);
            skywarsWorld = Bukkit.createWorld(new WorldCreator(randomWorld.getName()));

            if (skywarsWorld != null) {
                Bukkit.getScheduler().runTask(this, this::updateSpawnPointsWorld);
                Bukkit.getScheduler().runTask(this, this::loadLuckyBlocks);
            } else {
                getLogger().severe("Failed to load the Skywars world.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateSpawnPointsWorld() {
        if (skywarsWorld == null) {
            getLogger().severe("Skywars world is null, cannot update spawn points.");
            return;
        }

        loadConfigSpawnPoints();
        for (Location spawnPoint : spawnPoints) {
            if (spawnPoint != null) {
                spawnPoint.setWorld(skywarsWorld);
            }
        }
    }


    private void teleportPlayersToSkywars() {
        if (skywarsWorld == null) {
            String errorMessage = "Skywars world is not loaded. Cannot teleport players.";
            getLogger().severe(errorMessage);
            Bukkit.broadcastMessage(errorMessage);
            return;
        }

        if (spawnPoints == null || spawnPoints.isEmpty()) {
            getLogger().severe("No spawn points available. Please check the configuration.");
            return;
        }

        gameStarted = true;
        isCountdown = false;
        int i = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location spawnLocation = spawnPoints.get(i % spawnPoints.size());
            if (spawnLocation.getWorld() != null) {
                player.teleport(spawnLocation);
                player.setGameMode(GameMode.ADVENTURE);
                canMoveInGame = false;
                player.getInventory().clear();
                player.setSaturation(20.0f);
                player.setFoodLevel(20);
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1.0f, 1.0f);
                i++;
            } else {
                getLogger().warning("Spawn location has a null world; cannot teleport player.");
            }
        }

        startSkywarsCountdown();
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


    private void startCountdown() {
        if (isCountdown) {
            return;
        }

        isCountdown = true;
        countdownTaskId = new BukkitRunnable() {
            int countdown = 15;

            @Override
            public void run() {
                if (countdown == 15) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 1.0f);
                    }
                }
                if (countdown <= 0) {
                    teleportPlayersToSkywars();
                    cancel();
                    isCountdown = false;
                } else if (countdown % 5 == 0 || countdown <= 5) {
                    Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "Die Runde startet in " + countdown + " Sekunden...");
                }
                countdown--;
            }
        }.runTaskTimer(this, 0L, 20L).getTaskId();
    }


    private void startSkywarsCountdown() {
        Plugin plugin = this;
        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown <= 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.setWalkSpeed(0.2f);
                        player.setMaxHealth(20.0);
                        canMoveInGame = true;
                        player.sendTitle(ChatColor.LIGHT_PURPLE + "Spiel beginnt jetzt!", "", 10, 70, 20);
                        player.setGameMode(GameMode.SURVIVAL);
                        statsManager.incrementStat(player.getUniqueId(), "participations");
                        scoreboardManager.startGameTimer();
                        if (skywarsWorld != null) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    Location zoneCenter = new Location(skywarsWorld, 0, 60, 0);
                                    ZoneManager zoneManager = new ZoneManager(zoneCenter, 200, 300);
                                    zoneManager.startShrinkingZone();
                                }
                            }.runTaskLater(plugin, 20 * 360L);
                        }
                    }
                    startLootDropTask();
                    cancel();
                } else {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "Das Spiel startet in " + countdown + " Sekunden...");
                }
                countdown--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startLootDropTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getPluginManager().getPlugin("lootdrop") != null) {
                    if (skywarsWorld != null && gameStarted) {
                        if (!isFirstLootdropRun) {
                            int x = (int) (Math.random() * 101) - 50;
                            int z = (int) (Math.random() * 101) - 50;
                            int y = 100;

                            String command = String.format("lootdrop %s %d %d %d", skywarsWorld.getName(), x, y, z);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.getWorld().equals(skywarsWorld)) {
                                    player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                                }
                            }
                        }
                        isFirstLootdropRun = false;
                        scheduleNextLootDrop();
                    }
                } else {
                    getLogger().warning("Lootdrop-Plugin not found, skipping lootdrop function.");
                    cancel();
                }
            }
        }.runTaskLater(this, 0L);
    }


    private void scheduleNextLootDrop() {
        // Zufälligen Zeitraum zwischen 30 und 60 Sekunden bestimmen (in Ticks)
        int nextDelay = 30 * 20 + (int) (Math.random() * 31 * 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                startLootDropTask();
            }
        }.runTaskLater(this, nextDelay);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        UUID playerId = player.getUniqueId();
        if (mapEditorPlayers.contains(playerId)) {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            mapEditorPlayers.remove(playerId);
        }

        if (skywarsWorld != null && gameStarted) {
            player.teleport(skywarsWorld.getSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.DARK_AQUA + "Du bist nun ein Zuschauer!");
        } else {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.sendMessage(ChatColor.GOLD + "Willkommen auf PG SkyWars!");
        int i = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            i++;
        }
        int playersToWaitFor = (4 - i);
        if (playersToWaitFor < 4) {
            player.sendMessage(ChatColor.BLUE + "Warte auf " + playersToWaitFor + " weitere Spieler.");
        } else if (playersToWaitFor == 4) {
            player.sendMessage(ChatColor.BLUE + "Starting...");
        } else {
            player.sendMessage(ChatColor.BLUE + "Runde startet nicht, bitte kontaktiere GroupXyz!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (gameStarted && player.getWorld().equals(skywarsWorld)) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + player.getName() + " hat das Spiel verlassen!");
            checkRemainingPlayers();
        } else {
            if (skywarsWorld == null) {
                player.teleport(lobbyWorld.getSpawnLocation());
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (skywarsWorld != null && player.getWorld().equals(skywarsWorld)) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + player.getName() + " ist gestorben!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(skywarsWorld)) {
                    p.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }
            }
            player.setGameMode(GameMode.SPECTATOR);
            statsManager.incrementStat(player.getUniqueId(), "deaths");
            scoreboardManager.addKill(player.getKiller());
            checkRemainingPlayers();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (skywarsWorld != null && (!player.getWorld().equals(lobbyWorld)) && gameStarted) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(skywarsWorld.getSpawnLocation());
        } if (isEndPhase != null) {
            if (skywarsWorld != null && (!player.getWorld().equals(lobbyWorld)) && isEndPhase) {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(skywarsWorld.getSpawnLocation());
            }
        }
    }

    public void checkRemainingPlayers() {
        List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().equals(skywarsWorld))
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                .collect(Collectors.toList());

        if (alivePlayers.size() == 1) {
            UUID winnerId = alivePlayers.get(0).getUniqueId();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(ChatColor.DARK_PURPLE + "Runde beendet. Stoppe in 5 Sekunden!");
                isEndPhase = true;
            }
            Bukkit.getScheduler().runTaskLater(this, () -> endGame(winnerId), 20 * 5);
        }
    }


    private void endGame(@Nullable UUID winnerId) {
        Player winner = null;
        isEndPhase = false;
        scoreboardManager.stopGame();
        if (winnerId != null) {
            winner = Bukkit.getPlayer(winnerId);
        }
        if (winner != null) {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + winner.getName() + " hat die Runde gewonnen!");
            statsManager.incrementStat(winner.getUniqueId(), "wins");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (skywarsWorld != null && p.getWorld().equals(skywarsWorld)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
                }
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "Die Runde wurde von einem Admin gestoppt!");
        }

        for (Player player : skywarsWorld.getPlayers()) {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            try {
                player.setHealth(player.getMaxHealth());
                player.setSaturation(20.0f);
                player.setFoodLevel(20);
            } catch (Exception ignored) {}
        }

        Bukkit.unloadWorld(skywarsWorld, false);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == lobbyWorld) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }

        deleteWorld(skywarsWorld.getWorldFolder());
        skywarsWorld = null;
        gameStarted = false;
    }

    private void deleteWorld(File path) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                deleteWorld(file);
            }
        }
        path.delete();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
            Boolean isActivated = false;
            if (event.getPlayer().getWorld().equals(skywarsWorld)) {
                if (!canMoveInGame) {
                    event.setCancelled(true);
                }
            } else if (skywarsWorld != null && (!isCountdown)) {
                if (isActivated) {
                    if (skywarsWorld.getPlayers().isEmpty()) {
                        Plugin plugin = this;
                        new BukkitRunnable() {
                            int countdown = 10;
                            int graceSeconds = 5;

                            @Override
                            public void run() {
                                if (!skywarsWorld.equals(null)) {
                                    if (skywarsWorld.getPlayers().isEmpty()) {
                                        if (graceSeconds <= 0) {
                                            getLogger().info("Skywars world empty, unloading in 15 seconds!");
                                            if (countdown <= 0) {
                                                getLogger().info("Skywars world empty, unloading now!");
                                                Bukkit.unloadWorld(skywarsWorld, false);
                                                cancel();
                                            }
                                            countdown--;
                                        }
                                        graceSeconds--;
                                    } else {
                                        graceSeconds = 5;
                                        debugMessage("Unloading aboarded!");
                                    }
                                }

                            }
                        }.runTaskTimer(this, 0L, 20L);
                    }
                }
            } if (skywarsWorld != null && gameStarted && !(isCountdown)) {
                if (!(skywarsWorld.getPlayers().size() <= 1)) {
                    if (event.getPlayer().getWorld().equals(lobbyWorld)) {
                        event.getPlayer().setGameMode(GameMode.SPECTATOR);
                        event.getPlayer().teleport(skywarsWorld.getSpawnLocation());
                    }
                }
            }

    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            Player player = (Player) event.getEntity();
            if (player.getWorld().equals(lobbyWorld)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.SPONGE && isLuckyBlock(block.getLocation())) {
            event.setCancelled(true);
            block.setType(Material.AIR);
            debugMessage("Lucky Block zerstoert, lade Loottable oder starte Event...");

            Random random = new Random();
            boolean triggerEvent = random.nextBoolean();

            if (triggerEvent) {
                triggerRandomEvent(player);
            } else {
                World world = block.getWorld();
                Location loc = block.getLocation();
                NamespacedKey namespacedKey = NamespacedKey.fromString(getConfig().getString("lootTable"), null);
                LootTable lootTable;
                if (namespacedKey != null) {
                    lootTable = Bukkit.getLootTable(namespacedKey);
                    loc.getBlock().setType(Material.CHEST);
                    BlockState blockState = world.getBlockAt(loc).getState();
                    if (blockState instanceof Chest chest) {
                        chest.setLootTable(lootTable);
                        chest.update();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                chest.getBlock().breakNaturally();
                                debugMessage("Luckyblock bei " + chest.getLocation() + " wurde zerstoert.");
                            }
                        }.runTaskLater(this, 1L);
                    } else {
                        getLogger().warning("Blockstate not a Chest!");
                    }
                } else {
                    getLogger().severe("Lootable konnte nicht geladen werden!");
                }
            }
        }
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        if (skywarsWorld != null && gameStarted && !(isCountdown)) {
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL && event.getPlayer().getWorld() == skywarsWorld) {
                if (event.getBlock().getY() > maxHeight) {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Du darfst nicht hoeher als " + maxHeight + " bauen!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();

        if (inventory.getType() == InventoryType.CHEST && mapEditorPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);

            if (event.getPlayer() instanceof Player p) {
                p.sendMessage(ChatColor.RED + "DU DARFST IM MAPEDITOR KEINE KISTEN OEFFNEN, SONST WERDEN DIE LOOTTABLES GENERIERT!");
            }
        }
    }

    private boolean isLuckyBlock(Location location) {
        return location.getWorld() == skywarsWorld;
    }

    public void debugMessage(String message) {
        if (isDebug) {
            getLogger().info(message);
        }
    }

    private String getLocalizedString(String key, Object... args) {
        String rawMessage = languageConfig.getString(key, "Translation not found for key: " + key + " (If this happens after an Update, delete de.yml, en.yml, sp.yml in the plugin's config folder and restart)");
        return ChatColor.translateAlternateColorCodes('&', String.format(rawMessage, args));
    }

    private void loadLanguageConfig() {
        String langCode = getConfig().getString("language", "en");
        String langFileName = langCode + ".yml";

        File langFile = new File(getDataFolder(), langFileName);

        if (!langFile.exists()) {
            saveResource(langFileName, false);
        }

        languageConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void triggerRandomEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        List<Consumer<Player>> events = Arrays.asList(
                this::floorIsLavaEvent,
                this::lightningEvent,
                this::slownessEvent,
                this::invisibilityEvent,
                this::monsterApocalypseEvent,
                this::glowingEvent,
                this::blindnessEvent,
                this::teleportEvent,
                this::raidEvent,
                this::healthBoostEvent,
                this::inventorySwapEvent,
                this::speedEvent,
                this::bombeEvent
        );

        Random random = new Random();
        Consumer<Player> randomEvent = events.get(random.nextInt(events.size()));

        randomEvent.accept(player);
    }

    private void floorIsLavaEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§6--§0§k--§6 FLOOR IS LAVA §0§k--§6--", "", 10, 360, 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§6--§0§k--§6 In 3 §0§k--§6--", 10, 70, 20);
            }
        }.runTaskLater(this, 20 * 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§6--§0§k--§6 In 2 §0§k--§6--", 10, 70, 20);
            }
        }.runTaskLater(this, 20 * 2);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§6--§0§k--§6 In 1 §0§k--§6--", 10, 40, 20);
            }
        }.runTaskLater(this, 20 * 3);

        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = player.getLocation().subtract(0, 1, 0);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        player.getWorld().getBlockAt(loc.clone().add(x, 0, z)).setType(Material.LAVA);
                    }
                }
                player.playSound(player.getLocation(), "minecraft:entity.enderman.teleport", 100, 1);
            }
        }.runTaskLater(this, 20 * 4);
    }


    private void lightningEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        Random random = new Random();

        player.sendTitle("§e--§0§k--§e LIGHTNING STORM §0§k--§e--", "", 10, 70, 20);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int lightningCount = 0;

            @Override
            public void run() {
                if (lightningCount >= 10) {
                    return;
                }

                Location loc = player.getLocation().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                player.getWorld().strikeLightning(loc);
                lightningCount++;
            }
        }, 0L, 20L);

        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }

    private void slownessEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§9--§0§k--§9 SLOWMODE §0§k--§9--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 60, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }

    private void speedEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§9--§0§k--§9 QUICKMODE §0§k--§9--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 2));
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 100, 1);
    }


    private void invisibilityEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§f--§0§k--§f IST DA JEMAND? §0§k--§f--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }


    private void monsterApocalypseEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§4--§0§k--§4 MONSTER-APOKALYPSE §0§k--§4--", "", 10, 70, 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§4--§0§k--§6 In 3 §0§k--§4--", 10, 70, 20);
            }
        }.runTaskLater(this, 20 * 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§4--§0§k--§6 In 2 §0§k--§4--", 10, 70, 20);
            }
        }.runTaskLater(this, 20 * 2);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "§4--§0§k--§6 In 1 §0§k--§4--", 10, 40, 20);
            }
        }.runTaskLater(this, 20 * 3);

        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = player.getLocation().add(0, 0, 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 3, 1));
                player.playSound(player.getLocation(), "minecraft:entity.wither.death", 100, 1);

                Creeper creeper = (Creeper) player.getWorld().spawnEntity(loc, EntityType.CREEPER);
                creeper.setMaxFuseTicks(60);
                creeper.setFuseTicks(60);
                player.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                player.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                player.getWorld().spawnEntity(loc, EntityType.SKELETON);
                player.playSound(player.getLocation(), "minecraft:entity.enderman.teleport", 100, 1);
            }
        }.runTaskLater(this, 20 * 4);
    }

    private void glowingEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle(
                "§7--§0§k--§7 ICH LEUCHTE! §0§k--§7--",
                "",
                10, 70, 20
        );

        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 120, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }


    private void blindnessEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle(
                "§8--§0§k--§8 WO BIN ICH? §0§k--§8--",
                "",
                10, 70, 20
        );

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }

    private void teleportEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player p : players) {
            if (p != null) {
                if (!(p.getGameMode().equals(GameMode.SURVIVAL)) || !(p.getWorld().equals(skywarsWorld))) {
                    players.remove(p);
                }
            }
        }

        Player player1;
        Player player2;
        if (players.size() >= 2) {
            Random random = new Random();

            player1 = players.remove(random.nextInt(players.size()));
            player2 = players.remove(random.nextInt(players.size()));
        } else {
            return;
        }

        player1.sendTitle("§5--§0§k--§3 SPIELERTAUSCH MIT " + player2.getName() + " §0§k--§5--", "", 10, 70, 20);
        player2.sendTitle("§5--§0§k--§3 SPIELERTAUSCH MIT " + player2.getName() + " §0§k--§5--", "", 10, 70, 20);

        new BukkitRunnable() {
            @Override
            public void run() {


                Location loc1 = player1.getLocation();
                Location loc2 = player2.getLocation();

                player1.teleport(loc2);
                player2.teleport(loc1);

                player1.playSound(player1.getLocation(), "minecraft:entity.ender_eye.launch", 1.0F, 1.0F);
                player2.playSound(player2.getLocation(), "minecraft:entity.ender_eye.launch", 1.0F, 1.0F);

                if (!(player.isEmpty())) {
                    players.clear();
                }
            }
        }.runTask(this);
    }

    private void inventorySwapEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : players) {
            if (p != null) {
                if (!(p.getGameMode().equals(GameMode.SURVIVAL)) || !(p.getWorld().equals(skywarsWorld))) {
                    players.remove(p);
                }
            }
        }

        Player player1;
        Player player2;
        if (players.size() >= 2) {
            Random random = new Random();

            player1 = players.remove(random.nextInt(players.size()));
            player2 = players.remove(random.nextInt(players.size()));
        } else {
            return;
        }

        player1.sendTitle("§5--§0§k--§3 INVENTAR TAUSCH MIT " + player2.getName() + "§0§k--§5--", "", 10, 70, 20);
        player2.sendTitle("§5--§0§k--§3 INVENTAR TAUSCH MIT " + player1.getName() + "§0§k--§5--", "", 10, 70, 20);

        new BukkitRunnable() {
            @Override
            public void run() {

                Inventory inventory1 = player1.getInventory();
                Inventory inventory2 = player2.getInventory();

                ItemStack[] player1Items = inventory1.getContents();
                ItemStack[] player2Items = inventory2.getContents();

                player1.getInventory().clear();
                player2.getInventory().clear();

                inventory1.setContents(player2Items);
                inventory2.setContents(player1Items);

                player1.playSound(player1.getLocation(), "minecraft:entity.player.levelup", 1.0F, 1.0F);
                player2.playSound(player2.getLocation(), "minecraft:entity.player.levelup", 1.0F, 1.0F);
            }
        }.runTask(this);
    }


    private void raidEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawnLocation = skywarsWorld.getSpawnLocation();
                int numberOfPillagers = 5;

                for (int i = 0; i < numberOfPillagers; i++) {
                    Pillager pillager = (Pillager) spawnLocation.getWorld().spawnEntity(
                            spawnLocation.add((Math.random() - 0.5) * 5, 0, (Math.random() - 0.5) * 5),
                            EntityType.PILLAGER);
                    pillager.setTarget(null);
                    pillager.setAI(true);
                }

                Ravager ravager = (Ravager) spawnLocation.getWorld().spawnEntity(
                        spawnLocation.add((Math.random() - 0.5) * 5, 0, (Math.random() - 0.5) * 5),
                        EntityType.RAVAGER);
                ravager.setTarget(null);
                ravager.setAI(true);

                player.sendTitle("§5--§0§k--§8 RAID §0§k--§5--", "", 10, 70, 20);
                player.playSound(player.getLocation(), "minecraft:entity.pillager.celebrate", 1.0F, 1.0F);

            }
        }.runTask(this);
    }

    private void healthBoostEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        player.sendTitle("§5--§0§k--§5 HEALTH BOOST §0§k--§5--", "", 10, 70, 20);
        player.setMaxHealth(player.getMaxHealth() + 4.0);
        player.setHealth(player.getHealth());
    }

    private void bombeEvent(Player player) {
        if (player.getWorld() != skywarsWorld) return;

        Plugin plugin = this;

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("§5--§0§k--§5 WANDELNDE BOMBE §0§k--§5--", "", 10, 70, 20);

                Location playerLocation = player.getLocation();
                TNTPrimed tnt = (TNTPrimed) player.getWorld().spawnEntity(playerLocation, EntityType.PRIMED_TNT);

                tnt.setFuseTicks(200);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        tnt.teleport(player.getLocation());
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendTitle("", "§4--§0§k--§6 In 10 §0§k--§4--", 10, 70, 20);
                    }
                }.runTaskLater(plugin, 20 * 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendTitle("", "§4--§0§k--§6 In 5 §0§k--§4--", 10, 70, 20);
                    }
                }.runTaskLater(plugin, 20 * 5);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendTitle("", "§4--§0§k--§6 In 3 §0§k--§4--", 10, 40, 20);
                    }
                }.runTaskLater(plugin, 20 * 7);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendTitle("", "§4--§0§k--§6 In 2 §0§k--§4--", 10, 40, 20);
                    }
                }.runTaskLater(plugin, 20 * 8);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendTitle("", "§4--§0§k--§6 In 1 §0§k--§4--", 10, 40, 20);
                    }
                }.runTaskLater(plugin, 20 * 9);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        tnt.setFuseTicks(0);
                        player.playSound(player.getLocation(), "minecraft:entity.tnt.primed", 1.0F, 1.0F);
                    }
                }.runTaskLater(plugin, 20 * 10);
            }
        }.runTask(this);




    }

}
