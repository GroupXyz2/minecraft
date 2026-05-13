package de.groupxyz.groupsskywars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
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
import org.bukkit.scheduler.BukkitTask;
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

public class Groupsskywars extends JavaPlugin implements Listener, TabCompleter {
    private GameManager gameManager;

    private World lobbyWorld;
    private int minPlayers;
    private int maxPlayers;
    private File worldsFolder;
    private boolean isDebug;
    private String userSelectedWorld;

    private StatsManager statsManager;
    private ScoreboardManager scoreboardManager;
    private KitManager kitManager;
    private StatsGUI statsGUI;

    private FileConfiguration languageConfig;
    private final HashSet<UUID> mapEditorPlayers = new HashSet<>();
    private final HashMap<UUID, BukkitTask> mapEditorVisualizerTasks = new HashMap<>();
    boolean isFirstLootdropRun = true;
    int maxHeight = 200;
    boolean autoStartPaused = false;
    String forcedMapName = null;

    public StatsManager getStatsManager() { return statsManager; }
    public GameManager getGameManager() { return gameManager; }
    public KitManager getKitManager() { return kitManager; }
    public World getLobbyWorld() { return lobbyWorld; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public File getWorldsFolder() { return worldsFolder; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public String getForcedMapName() { return forcedMapName; }

    @Override
    public void onEnable() {
        getLogger().info("GroupsSkyWars by GroupXyz starting!");

        saveDefaultConfig();
        loadLanguageConfig();
        minPlayers = getConfig().getInt("minPlayers", 4);
        maxPlayers = getConfig().getInt("maxPlayers", -1);
        worldsFolder = new File(getDataFolder(), "skywars_worlds");

        lobbyWorld = Bukkit.getWorld("world");

        getServer().getPluginManager().registerEvents(this, this);

        gameManager = new GameManager(this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.cleanupEmptyInstances();
        }, 0L, 600L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!autoStartPaused) {
                checkAndStartGames();
            }
        }, 40L, 40L);

        File worldContainer = Bukkit.getWorldContainer();
        List<String> worldNames = getConfig().getStringList("spawnPoints");
        boolean oldWorldDeletionEnabled = getConfig().getBoolean("oldWorldDeletionEnabled", false);

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
        kitManager = new KitManager(this);
        getServer().getPluginManager().registerEvents(kitManager, this);
        statsGUI = new StatsGUI(statsManager);

        getLogger().info("Kit system initialized!");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(lobbyWorld)) {
                    GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
                    if (instance == null) {
                        instance = gameManager.findAvailableInstance();
                        if (instance == null) {
                            instance = gameManager.createGameInstance();
                        }
                        gameManager.assignPlayerToInstance(player.getUniqueId(), instance);
                        getLogger().info("Spieler " + player.getName() + " zu Instanz " + instance.getInstanceId() + " hinzugefügt");
                    }
                }
            }
        }, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("GroupsSkyWars shutting down...");

        for (GameInstance instance : new ArrayList<>(gameManager.getAllInstances())) {
            MultiInstanceGameController controller = gameManager.getController(instance);
            if (controller != null && instance.getSkywarsWorld() != null) {
                controller.endGame(null);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("round")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /round <start|stop|list|info|pause|setmap> (mapname)");
                return true;
            }

            if (args[0].equalsIgnoreCase("start")) {
                String worldName = null;
                if (args.length > 1 && args[1] != null) {
                    File[] worldFiles = worldsFolder.listFiles();
                    boolean found = false;
                    if (worldFiles != null) {
                        for (File file : worldFiles) {
                            if (file.getName().equals(args[1])) {
                                worldName = args[1];
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        sender.sendMessage(ChatColor.DARK_RED + "Welt " + args[1] + " existiert nicht.");
                        return false;
                    }
                } else {
                    worldName = getRandomWorldName();
                    if (worldName == null) {
                        sender.sendMessage(ChatColor.DARK_RED + "Keine Welten verfuegbar!");
                        return false;
                    }
                }

                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    sender.sendMessage(ChatColor.DARK_RED + "Keine Spieler online! Mindestens ein Spieler muss online sein.");
                    return false;
                }

                GameInstance instance = gameManager.createGameInstance();
                instance.setSelectedMapName(worldName);

                MultiInstanceGameController controller = gameManager.getController(instance);
                if (controller != null) {
                    int spawnCount = controller.getSpawnPointCountForWorld(worldName);
                    if (spawnCount > 0) {
                        instance.setInitialMaxPlayers(spawnCount);
                    }
                }

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.getWorld().equals(lobbyWorld)) {
                        gameManager.assignPlayerToInstance(onlinePlayer.getUniqueId(), instance);
                    }
                }

                if (instance.getPlayerCount() == 0) {
                    sender.sendMessage(ChatColor.DARK_RED + "Keine Spieler in der Lobby-Welt gefunden!");
                    return false;
                }

                if (instance.isGameStarted() || instance.isCountdown()) {
                    sender.sendMessage(ChatColor.DARK_RED + "Runde läuft bereits!");
                    return false;
                }

                if (controller != null) {
                    if (controller.loadWorld(worldName)) {
                        controller.startCountdown();
                        sender.sendMessage(ChatColor.GREEN + "Runde für Instanz " + instance.getInstanceId() + " gestartet mit " + instance.getPlayerCount() + " Spielern!");
                    } else {
                        sender.sendMessage(ChatColor.DARK_RED + "Fehler beim Laden der Welt!");
                    }
                } else {
                    sender.sendMessage(ChatColor.DARK_RED + "Controller nicht gefunden!");
                }

            } else if (args[0].equalsIgnoreCase("stop")) {
                if (args.length > 1) {
                    String instanceId = args[1];
                    if (Objects.equals(instanceId, "all")) {
                        for (GameInstance instance : new ArrayList<>(gameManager.getAllInstances())) {
                            MultiInstanceGameController controller = gameManager.getController(instance);
                            if (controller != null) {
                                controller.endGame(null);
                            }
                        }
                        sender.sendMessage(ChatColor.DARK_PURPLE + "Alle Runden werden gestoppt...");
                        return true;
                    }
                    GameInstance instance = gameManager.getAllInstances().stream()
                        .filter(i -> i.getInstanceId().equals(instanceId))
                        .findFirst()
                        .orElse(null);

                    if (instance != null) {
                        MultiInstanceGameController controller = gameManager.getController(instance);
                        if (controller != null) {
                            controller.endGame(null);
                            sender.sendMessage(ChatColor.DARK_PURPLE + "Instanz " + instanceId + " wird gestoppt...");
                        }
                    } else {
                        sender.sendMessage(ChatColor.DARK_RED + "Instanz " + instanceId + " nicht gefunden!");
                    }
                } else {
                    if (gameManager.getAllInstances().isEmpty()) {
                        sender.sendMessage(ChatColor.DARK_PURPLE + "Es existiert derzeit keine Runde.");
                    } else {
                        sender.sendMessage(ChatColor.DARK_PURPLE + "Bitte nutze /round stop all um alle runden zu beenden oder wähle eine aus.");
                    }
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
            } else if (args[0].equalsIgnoreCase("pauseautostart")) {
                if (!autoStartPaused) {
                    autoStartPaused = true;
                    sender.sendMessage(ChatColor.RED + "Automatischer Start PAUSIERT!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Automatischer Start ist bereits pausiert!");
                }
            } else if (args[0].equalsIgnoreCase("unpauseautostart") || args[0].equalsIgnoreCase("resumeautostart")) {
                if (autoStartPaused) {
                    autoStartPaused = false;
                    sender.sendMessage(ChatColor.GREEN + "Automatischer Start AKTIVIERT!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Automatischer Start war nicht pausiert!");
                }
            } else if (args[0].equalsIgnoreCase("toggleautostart")) {
                autoStartPaused = !autoStartPaused;
                sender.sendMessage(ChatColor.DARK_PURPLE + "Automatischer Start: " + (autoStartPaused ? ChatColor.RED + "PAUSIERT" : ChatColor.GREEN + "AKTIV"));
            } else if (args[0].equalsIgnoreCase("setmap")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /round setmap <mapname|random>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("random")) {
                    forcedMapName = null;
                    sender.sendMessage(ChatColor.GREEN + "Map-Auswahl auf RANDOM gesetzt!");
                } else {
                    File[] worldFiles = worldsFolder.listFiles();
                    boolean found = false;
                    if (worldFiles != null) {
                        for (File file : worldFiles) {
                            if (file.getName().equalsIgnoreCase(args[1])) {
                                forcedMapName = file.getName();
                                found = true;
                                break;
                            }
                        }
                    }

                    if (found) {
                        sender.sendMessage(ChatColor.GREEN + "Map für Autostart auf " + ChatColor.GOLD + forcedMapName + ChatColor.GREEN + " gesetzt!");
                    } else {
                        sender.sendMessage(ChatColor.DARK_RED + "Map " + args[1] + " existiert nicht!");
                    }
                }
            } else if (args[0].equalsIgnoreCase("info")) {
                sender.sendMessage(ChatColor.DARK_PURPLE + "=== SkyWars Multi-Instance Info ===");
                sender.sendMessage(ChatColor.GRAY + "Aktive Instanzen: " + ChatColor.WHITE + gameManager.getAllInstances().size());
                sender.sendMessage(ChatColor.GRAY + "Laufende Spiele: " + ChatColor.WHITE + gameManager.getRunningGames().size());
                sender.sendMessage(ChatColor.GRAY + "Countdowns: " + ChatColor.WHITE + gameManager.getCountdownGames().size());
                sender.sendMessage(ChatColor.GRAY + "Gesamt Spieler: " + ChatColor.WHITE + gameManager.getTotalActivePlayers());
                sender.sendMessage(ChatColor.GRAY + "Min. Spieler: " + ChatColor.WHITE + minPlayers);
                sender.sendMessage(ChatColor.GRAY + "Max. Spieler: " + ChatColor.WHITE + (maxPlayers > 0 ? maxPlayers : "Auto"));
                sender.sendMessage(ChatColor.GRAY + "Auto-Start: " + (autoStartPaused ? ChatColor.RED + "PAUSIERT" : ChatColor.GREEN + "AKTIV"));
                sender.sendMessage(ChatColor.GRAY + "Forced Map: " + (forcedMapName != null ? ChatColor.GOLD + forcedMapName : ChatColor.YELLOW + "Random"));

                if (!gameManager.getAllInstances().isEmpty()) {
                    sender.sendMessage(ChatColor.GOLD + "Details:");
                    for (GameInstance inst : gameManager.getAllInstances()) {
                        String status = inst.isGameStarted() ? "Running" : (inst.isCountdown() ? "Countdown" : "Waiting");
                        sender.sendMessage(ChatColor.GRAY + "  - " + inst.getInstanceId() + ": " +
                            ChatColor.WHITE + status + " (" + inst.getPlayerCount() + "/" + inst.getMaxPlayers() + ")");
                    }
                }
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Unbekannter Befehl. Verwende /round <start|stop|list|info|pause|setmap>");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("mapeditor")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                    return false;
                }

                Player player = (Player) sender;
                String mapName = args[1];
                File worldFolder = new File(worldsFolder, mapName);

                if (worldFolder.exists()) {
                    sender.sendMessage(ChatColor.DARK_RED + "Eine Map mit dem Namen '" + mapName + "' existiert bereits!");
                    return false;
                }

                sender.sendMessage(ChatColor.DARK_PURPLE + "Erstelle neue Void-Welt: " + mapName + "...");

                WorldCreator creator = new WorldCreator(worldFolder.getPath());
                creator.environment(World.Environment.NORMAL);
                creator.generateStructures(false);
                creator.generator(new VoidWorldGenerator());

                World world = creator.createWorld();

                if (world == null) {
                    sender.sendMessage(ChatColor.DARK_RED + "Fehler beim Erstellen der Welt: " + mapName);
                    return false;
                }

                world.setSpawnLocation(0, 100, 0);
                world.setDifficulty(Difficulty.PEACEFUL);
                world.setAutoSave(true);

                player.teleport(new Location(world, 0.5, 100, 0.5));
                player.setGameMode(GameMode.CREATIVE);
                UUID playerId = player.getUniqueId();
                mapEditorPlayers.add(playerId);
                player.sendMessage(ChatColor.DARK_PURPLE + "Neue Map '" + mapName + "' wurde erstellt!");
                player.sendMessage(ChatColor.GRAY + "Du befindest dich jetzt im Bearbeitungsmodus.");
                player.sendMessage(ChatColor.GRAY + "Verwende /setfeature spawnpunkt um Spawnpunkte zu setzen.");
                player.sendMessage(ChatColor.GRAY + "Verwende /mapeditor " + mapName + " off um den Editor zu verlassen.");

                return true;
            } else if (args.length == 1) {

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
                                    stopVisualization(playerId);
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
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /mapeditor <mapname> [off] | /mapeditor create <mapname>");
                return false;
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }
            Player player = (Player) sender;

            if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (!sender.isOp() && target != sender) {
                    sender.sendMessage(ChatColor.DARK_RED + "Du kannst nur deine Statistiken einsehen!");
                    return false;
                }
                if (target != null) {
                    statsGUI.openStatsGUI(player, target);
                } else {
                    sender.sendMessage(ChatColor.DARK_RED + "Spieler nicht gefunden.");
                }
            } else if (args.length == 0) {
                statsGUI.openStatsGUI(player, player);
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Verwendung: /stats [Spielername]");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("leaderboard") || command.getName().equalsIgnoreCase("lb")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von Spielern ausgeführt werden!");
                return true;
            }

            Player player = (Player) sender;
            statsGUI.openLeaderboardGUI(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("swreload")) {
            reloadConfig();
            loadLanguageConfig();
            isDebug = getConfig().getBoolean("isDebug", false);
            minPlayers = getConfig().getInt("minPlayers", 4);
            maxPlayers = getConfig().getInt("maxPlayers", -1);
            sender.sendMessage(ChatColor.DARK_PURPLE + "Config reloaded!");
            return true;
        } else if (command.getName().equalsIgnoreCase("kit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }
            Player player = (Player) sender;

            if (player.getWorld() != lobbyWorld) {
                player.sendMessage(ChatColor.DARK_RED + "Du kannst dein Kit nur in der Lobby auswaehlen!");
                return false;
            }

            GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
            if (instance != null && instance.isGameStarted()) {
                player.sendMessage(ChatColor.DARK_RED + "Du kannst dein Kit nicht während einem laufenden Spiel ändern!");
                return false;
            }

            kitManager.openKitSelector(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("skywarsinfo")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }
            Player player = (Player) sender;

            player.sendMessage("");
            player.sendMessage(ChatColor.DARK_PURPLE + "╔════════════════════════════════════╗");
            player.sendMessage(ChatColor.DARK_PURPLE + "║  " + ChatColor.GOLD + ChatColor.BOLD + "SkyWars Info" + ChatColor.DARK_PURPLE + "                    ║");
            player.sendMessage(ChatColor.DARK_PURPLE + "╚════════════════════════════════════╝");
            player.sendMessage("");

            player.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.GOLD + ChatColor.BOLD + "KITS:");
            player.sendMessage(ChatColor.GRAY + "  Wähle dein Kit mit " + ChatColor.YELLOW + "/kit" + ChatColor.GRAY + "!");
            player.sendMessage("");

            Map<String, Kit> kits = kitManager.getKits();
            for (Kit kit : kits.values()) {
                KitAbility ability = kit.getAbility();
                String abilityDesc = ability != null ? ability.getDescription() : "Keine Fähigkeit";
                player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + kit.getName() + ChatColor.GRAY + " - " + kit.getDescription());
                if (ability != null) {
                    player.sendMessage(ChatColor.DARK_GRAY + "    Fähigkeit: " + ChatColor.LIGHT_PURPLE + ability.getName() + ChatColor.GRAY + " (" + abilityDesc + ")");
                    if (ability.getCooldownSeconds() > 0) {
                        player.sendMessage(ChatColor.DARK_GRAY + "    Cooldown: " + ChatColor.RED + ability.getCooldownSeconds() + "s");
                    }
                }
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.GOLD + ChatColor.BOLD + "LUCKY BLOCKS:");
            player.sendMessage(ChatColor.GRAY + "  Spezielle Blöcke die auf der Map verteilt sind!");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Zerstöre sie für zufällige Items");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Können gute oder schlechte Effekte haben");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Spawn zufällig auf der Map");

            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.GOLD + ChatColor.BOLD + "LOOT DROPS:");
            player.sendMessage(ChatColor.GRAY + "  Regelmäßige Loot-Drops während des Spiels!");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Erscheinen alle paar Minuten");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Enthalten wertvolle Items und Ausrüstung");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Spawnen in der Luft und fallen herunter");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Markiert durch Partikel-Effekte");

            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.GOLD + ChatColor.BOLD + "SPIELABLAUF:");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Spawne auf deiner Insel");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Sammle Items aus Kisten");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Nach 5 Minuten beginnt die Zone zu schrumpfen");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Kämpfe gegen andere Spieler");
            player.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + "Sei der letzte Überlebende!");

            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Viel Erfolg im Kampf!");
            player.sendMessage("");

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
                List<?> existingList = config.getList("luckyBlocks", new ArrayList<>());
                List<Map<String, Object>> luckyBlocks = new ArrayList<>();
                for (Object obj : existingList) {
                    if (obj instanceof Map) {
                        luckyBlocks.add((Map<String, Object>) obj);
                    }
                }
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
                List<?> existingList = config.getList("spawnPoints", new ArrayList<>());
                List<Map<String, Object>> spawnPoints = new ArrayList<>();
                for (Object obj : existingList) {
                    if (obj instanceof Map) {
                        spawnPoints.add((Map<String, Object>) obj);
                    }
                }
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
        } else if (command.getName().equalsIgnoreCase("removefeature")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }

            if (args.length != 2) {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /removefeature (luckyblock|spawnpunkt) <range>");
                return false;
            }

            int range;
            try {
                range = Integer.parseInt(args[1]);
                if (range < 1 || range > 50) {
                    player.sendMessage(ChatColor.DARK_RED + "Range muss zwischen 1 und 50 sein!");
                    return false;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.DARK_RED + "Ungueltige Range! Benutzung: /removefeature (luckyblock|spawnpunkt) <range>");
                return false;
            }

            Location loc = player.getLocation();
            FileConfiguration config = getConfig();
            String fullWorldName = player.getWorld().getName();
            String worldName = extractLastPart(fullWorldName);

            if (args[0].equalsIgnoreCase("luckyblock")) {
                List<?> existingList = config.getList("luckyBlocks", new ArrayList<>());
                List<Map<String, Object>> luckyBlocks = new ArrayList<>();
                for (Object obj : existingList) {
                    if (obj instanceof Map) {
                        luckyBlocks.add((Map<String, Object>) obj);
                    }
                }
                List<Map<String, Object>> toRemove = new ArrayList<>();

                for (Map<String, Object> lb : luckyBlocks) {
                    if (worldName.equals(lb.get("world"))) {
                        int x = (int) lb.get("x");
                        int y = (int) lb.get("y");
                        int z = (int) lb.get("z");
                        Location lbLoc = new Location(player.getWorld(), x, y, z);
                        if (loc.distance(lbLoc) <= range) {
                            toRemove.add(lb);
                        }
                    }
                }

                if (!toRemove.isEmpty()) {
                    luckyBlocks.removeAll(toRemove);
                    config.set("luckyBlocks", luckyBlocks);
                    saveConfig();
                    player.sendMessage(ChatColor.DARK_PURPLE + "" + toRemove.size() + " Luckyblock(s) im Umkreis von " + range + " Bloecken entfernt!");
                } else {
                    player.sendMessage(ChatColor.DARK_RED + "Keine Luckyblocks im Umkreis von " + range + " Bloecken gefunden!");
                }
            } else if (args[0].equalsIgnoreCase("spawnpunkt")) {
                List<?> existingList = config.getList("spawnPoints", new ArrayList<>());
                List<Map<String, Object>> spawnPoints = new ArrayList<>();
                for (Object obj : existingList) {
                    if (obj instanceof Map) {
                        spawnPoints.add((Map<String, Object>) obj);
                    }
                }
                List<Map<String, Object>> toRemove = new ArrayList<>();

                for (Map<String, Object> sp : spawnPoints) {
                    if (worldName.equals(sp.get("world"))) {
                        int x = (int) sp.get("x");
                        int y = (int) sp.get("y");
                        int z = (int) sp.get("z");
                        Location spLoc = new Location(player.getWorld(), x, y, z);
                        if (loc.distance(spLoc) <= range) {
                            toRemove.add(sp);
                        }
                    }
                }

                if (!toRemove.isEmpty()) {
                    spawnPoints.removeAll(toRemove);
                    config.set("spawnPoints", spawnPoints);
                    saveConfig();
                    player.sendMessage(ChatColor.DARK_PURPLE + "" + toRemove.size() + " Spawnpunkt(e) im Umkreis von " + range + " Bloecken entfernt!");
                } else {
                    player.sendMessage(ChatColor.DARK_RED + "Keine Spawnpunkte im Umkreis von " + range + " Bloecken gefunden!");
                }
            } else {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /removefeature (luckyblock|spawnpunkt) <range>");
                return false;
            }
        } else if (command.getName().equalsIgnoreCase("visualize")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.DARK_RED + "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden!");
                return false;
            }

            if (!mapEditorPlayers.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.DARK_RED + "Du musst dich im Map-Editor-Modus befinden!");
                return false;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /visualize <on|off>");
                return false;
            }

            if (args[0].equalsIgnoreCase("on")) {
                if (mapEditorVisualizerTasks.containsKey(player.getUniqueId())) {
                    player.sendMessage(ChatColor.DARK_RED + "Visualisierung ist bereits aktiviert!");
                    return false;
                }

                BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    if (!player.isOnline() || !mapEditorPlayers.contains(player.getUniqueId())) {
                        stopVisualization(player.getUniqueId());
                        return;
                    }

                    visualizeFeatures(player);
                }, 0L, 10L);

                mapEditorVisualizerTasks.put(player.getUniqueId(), task);
                player.sendMessage(ChatColor.DARK_PURPLE + "Feature-Visualisierung aktiviert!");
                player.sendMessage(ChatColor.GRAY + "Gruen = Spawnpunkte, Gelb = Luckyblocks");
            } else if (args[0].equalsIgnoreCase("off")) {
                if (!mapEditorVisualizerTasks.containsKey(player.getUniqueId())) {
                    player.sendMessage(ChatColor.DARK_RED + "Visualisierung ist bereits deaktiviert!");
                    return false;
                }

                stopVisualization(player.getUniqueId());
                player.sendMessage(ChatColor.DARK_PURPLE + "Feature-Visualisierung deaktiviert!");
            } else {
                player.sendMessage(ChatColor.DARK_RED + "Benutzung: /visualize <on|off>");
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

    private void visualizeFeatures(Player player) {
        FileConfiguration config = getConfig();
        String fullWorldName = player.getWorld().getName();
        String worldName = extractLastPart(fullWorldName);
        Location playerLoc = player.getLocation();
        double maxDistance = 50.0;

        List<Map<String, Object>> spawnPoints = (List<Map<String, Object>>) config.getList("spawnPoints", new ArrayList<>());
        for (Map<String, Object> sp : spawnPoints) {
            if (worldName.equals(sp.get("world"))) {
                int x = (int) sp.get("x");
                int y = (int) sp.get("y");
                int z = (int) sp.get("z");
                Location spLoc = new Location(player.getWorld(), x + 0.5, y + 1, z + 0.5);

                if (playerLoc.distance(spLoc) <= maxDistance) {
                    player.spawnParticle(Particle.GLOW, spLoc, 3, 0.2, 0.5, 0.2, 0.01);
                }
            }
        }

        List<Map<String, Object>> luckyBlocks = (List<Map<String, Object>>) config.getList("luckyBlocks", new ArrayList<>());
        for (Map<String, Object> lb : luckyBlocks) {
            if (worldName.equals(lb.get("world"))) {
                int x = (int) lb.get("x");
                int y = (int) lb.get("y");
                int z = (int) lb.get("z");
                Location lbLoc = new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5);

                if (playerLoc.distance(lbLoc) <= maxDistance) {
                    player.spawnParticle(Particle.END_ROD, lbLoc, 2, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
    }

    private void stopVisualization(UUID playerId) {
        BukkitTask task = mapEditorVisualizerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void loadLuckyBlocks() {
        List<?> luckyBlockConfigs = getConfig().getList("luckyBlocks");
        if (luckyBlockConfigs == null) return;

        for (Object obj : luckyBlockConfigs) {
            if (obj instanceof Map<?, ?> map) {
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

    public void loadLuckyBlocksForInstance(GameInstance instance, String originalWorldName) {
        World instanceWorld = instance.getSkywarsWorld();
        if (instanceWorld == null) {
            getLogger().warning("Cannot load luckyblocks: Instance world is null");
            return;
        }

        List<?> luckyBlockConfigs = getConfig().getList("luckyBlocks");
        if (luckyBlockConfigs == null) return;

        int count = 0;
        for (Object obj : luckyBlockConfigs) {
            if (obj instanceof Map<?, ?> map) {
                String worldName = (String) map.get("world");
                if (!worldName.equals(originalWorldName)) continue;

                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                Location location = new Location(instanceWorld, x, y, z);

                Block block = location.getBlock();
                block.setType(Material.SPONGE);
                count++;
            }
        }
        getLogger().info("[" + instance.getInstanceId() + "] " + count + " Luckyblocks gesetzt");
    }

    private void checkAndStartGames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(lobbyWorld)) {
                GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
                if (instance == null) {
                    instance = gameManager.findAvailableInstance();
                    if (instance == null) {
                        instance = gameManager.createGameInstance();
                    }
                    gameManager.assignPlayerToInstance(player.getUniqueId(), instance);
                    
                    int maxPlayers = instance.getMaxPlayers();
                    int currentPlayers = instance.getPlayerCount();
                    
                    if (maxPlayers > 0 && currentPlayers >= maxPlayers) {
                        getLogger().info("[AutoStart] Runde " + instance.getInstanceId() + " ist voll (" + currentPlayers + "/" + maxPlayers + ")");
                    }
                }
            }
        }

        for (GameInstance instance : new ArrayList<>(gameManager.getAllInstances())) {
            int maxPlayers = instance.getMaxPlayers();
            int currentPlayers = instance.getPlayerCount();

            if (maxPlayers > 0 && currentPlayers > maxPlayers && !instance.isGameStarted() && !instance.isCountdown()) {
                List<Player> players = instance.getOnlinePlayers();
                
                for (int i = maxPlayers; i < players.size(); i++) {
                    Player p = players.get(i);
                    instance.removePlayer(p.getUniqueId());
                    gameManager.removePlayer(p.getUniqueId());
                    
                    GameInstance newInstance = gameManager.findAvailableInstance();
                    if (newInstance == null) {
                        newInstance = gameManager.createGameInstance();
                    }
                    gameManager.assignPlayerToInstance(p.getUniqueId(), newInstance);
                    p.sendMessage(ChatColor.GOLD + "Du wurdest zu Runde " + newInstance.getInstanceId() + " verschoben!");
                }
            }
        }

        for (GameInstance instance : gameManager.getAllInstances()) {
            int playerCount = instance.getPlayerCount();
            boolean isStarted = instance.isGameStarted();
            boolean isCountdown = instance.isCountdown();
            boolean hasWorld = instance.getSkywarsWorld() != null;


            if (!isStarted && !isCountdown && playerCount >= minPlayers) {
                if (!hasWorld) {
                    MultiInstanceGameController controller = gameManager.getController(instance);
                    if (controller != null) {
                        String worldName = instance.getSelectedMapName();
                        if (worldName != null) {
                            getLogger().info("[AutoStart] Starte Countdown für Instanz " + instance.getInstanceId() + " mit " + playerCount + " Spielern auf Map " + worldName);
                            if (controller.loadWorld(worldName)) {

                                controller.startCountdown();
                                for (Player p : instance.getOnlinePlayers()) {
                                    p.sendMessage(ChatColor.GREEN + "[" + instance.getInstanceId() + "] Spieler erreicht! Countdown startet...");
                                }
                            } else {
                                getLogger().warning("[AutoStart] Fehler beim Laden der Welt " + worldName);
                            }
                        } else {
                            getLogger().warning("[AutoStart] Keine Map für Instanz ausgewählt!");
                        }
                    } else {
                        getLogger().warning("[AutoStart] Controller für Instanz " + instance.getInstanceId() + " nicht gefunden!");
                    }
                }
            }
        }
    }

    public String getRandomWorldName() {
        File[] worldFiles = worldsFolder.listFiles(File::isDirectory);
        if (worldFiles == null || worldFiles.length == 0) {
            getLogger().warning("Keine gültigen Welten im Ordner gefunden!");
            return null;
        }
        return worldFiles[new Random().nextInt(worldFiles.length)].getName();
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld() == Bukkit.getWorld("world")) {
            if (!player.isOp()) {
                player.getInventory().clear();
            }
        }

        UUID playerId = player.getUniqueId();
        if (mapEditorPlayers.contains(playerId)) {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            mapEditorPlayers.remove(playerId);
            stopVisualization(playerId);
            return;
        }

        GameInstance instance = gameManager.getPlayerInstance(playerId);

        if (instance != null && instance.isGameStarted()) {
            World world = instance.getSkywarsWorld();
            if (world != null) {
                instance.addSpectator(playerId);
                player.teleport(world.getSpawnLocation());
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.DARK_AQUA + "[" + instance.getInstanceId() + "] Du bist nun ein Zuschauer!");
            }
            return;
        }

        player.teleport(lobbyWorld.getSpawnLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setHealth(player.getMaxHealth());
        player.sendMessage(ChatColor.GOLD + "Nutze " + ChatColor.AQUA + "/kit" + ChatColor.GOLD + " um dein Kit auszuwählen!");

        if (instance == null) {
            instance = gameManager.findAvailableInstance();
            if (instance == null) {
                instance = gameManager.createGameInstance();
            }
        }

        gameManager.assignPlayerToInstance(playerId, instance);

        int currentPlayers = instance.getPlayerCount();
        int playersNeeded = minPlayers - currentPlayers;

        String maxPlayersDisplay = instance.getMaxPlayers() > 0 ? String.valueOf(instance.getMaxPlayers()) : "?";


        if (!instance.isGameStarted() && !instance.isCountdown()) {
            if (playersNeeded > 0) {
                player.sendMessage(ChatColor.BLUE + "[" + instance.getInstanceId() + "] Warte auf " + playersNeeded + " weitere Spieler. (" + currentPlayers + "/" + maxPlayersDisplay + ")");
            } else if (!autoStartPaused) {
                player.sendMessage(ChatColor.GREEN + "[" + instance.getInstanceId() + "] Countdown startet...");

                MultiInstanceGameController controller = gameManager.getController(instance);
                if (controller != null) {
                    if (instance.getSkywarsWorld() == null) {
                        String worldName = instance.getSelectedMapName();
                        if (worldName == null) {
                            player.sendMessage(ChatColor.DARK_RED + "Fehler: Keine Map ausgewählt!");
                            return;
                        }
                        if (!controller.loadWorld(worldName)) {
                            player.sendMessage(ChatColor.DARK_RED + "Fehler beim Laden der Welt!");
                            return;
                        }
                    }
                    controller.startCountdown();
                } else {
                    player.sendMessage(ChatColor.DARK_RED + "Fehler: Controller nicht gefunden!");
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "[" + instance.getInstanceId() + "] Automatischer Start ist pausiert! (" + currentPlayers + "/" + maxPlayersDisplay + ")");
            }
        } else if (instance.isCountdown()) {
            player.sendMessage(ChatColor.YELLOW + "[" + instance.getInstanceId() + "] Countdown läuft bereits! (" + currentPlayers + "/" + maxPlayersDisplay + ")");
        }

        if (instance.getMaxPlayers() > 0 && currentPlayers >= instance.getMaxPlayers() - 2 && !instance.isGameStarted()) {
            player.sendMessage(ChatColor.GOLD + "Tipp: Bei " + instance.getMaxPlayers() + " Spielern startet das Spiel sofort!");
        }

        if (instance.getMaxPlayers() > 0 && currentPlayers >= instance.getMaxPlayers() && !instance.isGameStarted()) {
            player.sendMessage(ChatColor.GOLD + "Die Lobby ist voll! Weitere Spieler werden als Zuschauer gesetzt.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        stopVisualization(playerId);
        mapEditorPlayers.remove(playerId);

        GameInstance instance = gameManager.getPlayerInstance(playerId);

        if (instance != null) {
            if (instance.isGameStarted() && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
                instance.trackPlayTime(player);
                kitManager.resetPlayerData(player);

                for (Player p : instance.getAllOnlinePlayers()) {
                    p.sendMessage(ChatColor.DARK_AQUA + "[" + instance.getInstanceId() + "] " + player.getName() + " hat das Spiel verlassen!");
                }

                instance.removePlayer(playerId);
                gameManager.removePlayer(playerId);

                MultiInstanceGameController controller = gameManager.getController(instance);
                if (controller != null) {
                    controller.checkRemainingPlayers();
                }
            } else {
                instance.removePlayer(playerId);
                gameManager.removePlayer(playerId);

                if (instance.isCountdown() && instance.getPlayerCount() < minPlayers) {
                    MultiInstanceGameController controller = gameManager.getController(instance);
                    if (controller != null) {
                        controller.cancelCountdown();
                        for (Player p : instance.getOnlinePlayers()) {
                            p.sendMessage(ChatColor.RED + "[" + instance.getInstanceId() + "] Countdown abgebrochen - nicht genug Spieler!");
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        GameInstance instance = gameManager.getPlayerInstance(playerId);

        if (instance != null && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
            Player killer = player.getKiller();
            String deathMessage;

            if (killer != null && killer != player) {
                deathMessage = ChatColor.DARK_AQUA + "[" + instance.getInstanceId() + "] " +
                              ChatColor.RED + player.getName() +
                              ChatColor.GRAY + " wurde von " +
                              ChatColor.GOLD + killer.getName() +
                              ChatColor.GRAY + " getötet!";
                statsManager.incrementStat(killer.getUniqueId(), "kills");
                scoreboardManager.addKill(killer);
            } else {
                deathMessage = ChatColor.DARK_AQUA + "[" + instance.getInstanceId() + "] " +
                              ChatColor.RED + player.getName() +
                              ChatColor.GRAY + " ist gestorben!";
            }

            for (Player p : instance.getAllOnlinePlayers()) {
                p.sendMessage(deathMessage);
                if (p.getWorld().equals(instance.getSkywarsWorld())) {
                    p.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }
            }

            statsManager.incrementStat(player.getUniqueId(), "deaths");

            instance.addSpectator(playerId);
            player.setGameMode(GameMode.SPECTATOR);
            kitManager.resetPlayerData(player);

            for (Player p : instance.getOnlinePlayers()) {
                if (p.getWorld().equals(instance.getSkywarsWorld())) {
                    scoreboardManager.updateScoreboard(p);
                }
            }

            MultiInstanceGameController controller = gameManager.getController(instance);
            if (controller != null) {
                controller.checkRemainingPlayers();
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        GameInstance instance = gameManager.getPlayerInstance(playerId);

        if (instance != null && instance.getSkywarsWorld() != null && instance.isGameStarted()) {
            event.setRespawnLocation(instance.getSkywarsWorld().getSpawnLocation());

            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.GOLD + "Du bist jetzt Zuschauer!");
            }, 1L);
        } else if (instance != null && instance.isEndPhase()) {
            if (instance.getSkywarsWorld() != null) {
                event.setRespawnLocation(instance.getSkywarsWorld().getSpawnLocation());

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawned(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());

        if (instance == null && player.getWorld().equals(lobbyWorld)) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
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
        Player player = event.getPlayer();
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());

        if (instance != null && instance.getSkywarsWorld() != null) {
            if (player.getWorld().equals(instance.getSkywarsWorld())) {
                if (!instance.canMoveInGame()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            Player player = (Player) event.getEntity();
            if (player.getWorld().equals(lobbyWorld)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            Player player = (Player) event.getEntity();
            if (player.getWorld().equals(lobbyWorld)) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());

        if (block.getType() == Material.SPONGE && instance != null && instance.getSkywarsWorld() != null && block.getWorld().equals(instance.getSkywarsWorld())) {
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
        Player player = event.getPlayer();
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());

        if (instance != null && instance.getSkywarsWorld() != null && instance.isGameStarted()) {
            if (player.getGameMode() == GameMode.SURVIVAL && player.getWorld().equals(instance.getSkywarsWorld())) {
                if (event.getBlock().getY() > maxHeight) {
                    player.sendMessage(ChatColor.DARK_RED + "Du darfst nicht hoeher als " + maxHeight + " bauen!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();

            if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST ||
                 block.getType() == Material.BARREL) && mapEditorPlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Du darfst im Map-Editor keine Kisten öffnen! Die Loot-Tables würden sonst generiert werden.");
            }
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();

        if (inventory.getType() == InventoryType.CHEST && mapEditorPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        } else if (inventory.getType() == InventoryType.CHEST) {
            GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
            if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
                statsManager.incrementStat(player.getUniqueId(), "chestsOpened");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();

        if (title.equals(ChatColor.DARK_PURPLE + "Wähle dein Kit")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName == null) return;

            String kitName = ChatColor.stripColor(displayName).toLowerCase();

            kitManager.selectKit(player, kitName);
            player.closeInventory();
        } else if (title.startsWith(ChatColor.DARK_PURPLE + "📊 Statistiken:") ||
                   title.equals(ChatColor.GOLD + "🏆 Globale Rangliste")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        }
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

    private boolean checkPlayerInInstance(Player player) {
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        return instance != null && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld());
    }

    private void triggerRandomEvent(Player player) {
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance == null || instance.getSkywarsWorld() == null || !player.getWorld().equals(instance.getSkywarsWorld())) {
            return;
        }

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
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance == null || instance.getSkywarsWorld() == null || !player.getWorld().equals(instance.getSkywarsWorld())) {
            return;
        }

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
        if (!checkPlayerInInstance(player)) return;

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
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle("§9--§0§k--§9 SLOWMODE §0§k--§9--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 60, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }

    private void speedEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle("§9--§0§k--§9 QUICKMODE §0§k--§9--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 2));
        player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 100, 1);
    }


    private void invisibilityEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle("§f--§0§k--§f IST DA JEMAND? §0§k--§f--", "", 10, 70, 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }


    private void monsterApocalypseEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

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
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle(
                "§7--§0§k--§7 ICH LEUCHTE! §0§k--§7--",
                "",
                10, 70, 20
        );

        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 120, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }


    private void blindnessEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle(
                "§8--§0§k--§8 WO BIN ICH? §0§k--§8--",
                "",
                10, 70, 20
        );

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1));
        player.playSound(player.getLocation(), "minecraft:entity.wither.ambient", 100, 1);
    }

    private void teleportEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance == null || instance.getSkywarsWorld() == null) return;

        List<Player> players = new ArrayList<>(instance.getOnlinePlayers());
        players.removeIf(p -> p.getGameMode() != GameMode.SURVIVAL || !p.getWorld().equals(instance.getSkywarsWorld()));

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
        if (!checkPlayerInInstance(player)) return;

        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance == null || instance.getSkywarsWorld() == null) return;

        List<Player> players = new ArrayList<>(instance.getOnlinePlayers());
        players.removeIf(p -> p.getGameMode() != GameMode.SURVIVAL || !p.getWorld().equals(instance.getSkywarsWorld()));

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
        if (!checkPlayerInInstance(player)) return;

        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance == null || instance.getSkywarsWorld() == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawnLocation = instance.getSkywarsWorld().getSpawnLocation();
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
        if (!checkPlayerInInstance(player)) return;

        player.sendTitle("§5--§0§k--§5 HEALTH BOOST §0§k--§5--", "", 10, 70, 20);
        player.setMaxHealth(player.getMaxHealth() + 4.0);
        player.setHealth(player.getHealth());
    }

    private void bombeEvent(Player player) {
        if (!checkPlayerInInstance(player)) return;

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

    @EventHandler
    public void onStatsBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
            statsManager.incrementStat(player.getUniqueId(), "blocksPlaced");
        }
    }

    @EventHandler
    public void onStatsBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
        if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
            statsManager.incrementStat(player.getUniqueId(), "blocksBroken");
        }
    }


    @EventHandler
    public void onStatsProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            GameInstance instance = gameManager.getPlayerInstance(player.getUniqueId());
            if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && player.getWorld().equals(instance.getSkywarsWorld())) {
                if (event.getEntity() instanceof Arrow) {
                    statsManager.incrementStat(player.getUniqueId(), "arrowsShot");
                }
            }
        }
    }

    @EventHandler
    public void onStatsProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Player shooter) {
            if (event.getHitEntity() instanceof Player victim) {
                GameInstance instance = gameManager.getPlayerInstance(shooter.getUniqueId());
                if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && shooter.getWorld().equals(instance.getSkywarsWorld())) {
                    if (event.getEntity() instanceof Arrow) {
                        statsManager.incrementStat(shooter.getUniqueId(), "arrowsHit");
                    } else if (event.getEntity() instanceof Snowball || event.getEntity() instanceof Egg) {
                        victim.damage(0.5);

                        org.bukkit.util.Vector knockback = victim.getLocation().toVector().subtract(shooter.getLocation().toVector()).normalize().multiply(0.4).setY(0.1);
                        victim.setVelocity(victim.getVelocity().add(knockback));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onStatsEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            GameInstance instance = gameManager.getPlayerInstance(victim.getUniqueId());
            if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && victim.getWorld().equals(instance.getSkywarsWorld())) {
                statsManager.incrementStat(victim.getUniqueId(), "damageTaken", (int) event.getFinalDamage());
            }
        }
    }

    @EventHandler
    public void onStatsEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && event.getEntity() instanceof Player) {
            GameInstance instance = gameManager.getPlayerInstance(damager.getUniqueId());
            if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && damager.getWorld().equals(instance.getSkywarsWorld())) {
                statsManager.incrementStat(damager.getUniqueId(), "damageDealt", (int) event.getFinalDamage());
            }
        } else if (event.getDamager() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player shooter && event.getEntity() instanceof Player) {
                GameInstance instance = gameManager.getPlayerInstance(shooter.getUniqueId());
                if (instance != null && instance.isGameStarted() && instance.getSkywarsWorld() != null && shooter.getWorld().equals(instance.getSkywarsWorld())) {
                    statsManager.incrementStat(shooter.getUniqueId(), "damageDealt", (int) event.getFinalDamage());
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("round")) {
            if (args.length == 1) {
                completions.add("start");
                completions.add("stop");
                completions.add("list");
                completions.add("info");
                completions.add("pauseautostart");
                completions.add("unpauseautostart");
                completions.add("resumeautostart");
                completions.add("toggleautostart");
                completions.add("setmap");
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("setmap")) {
                    File[] worldFiles = worldsFolder.listFiles();
                    if (worldFiles != null) {
                        for (File file : worldFiles) {
                            if (file.isDirectory()) {
                                completions.add(file.getName());
                            }
                        }
                    }
                    if (args[0].equalsIgnoreCase("setmap")) {
                        completions.add("random");
                    }
                } else if (args[0].equalsIgnoreCase("stop")) {
                    for (GameInstance instance : gameManager.getAllInstances()) {
                        completions.add(instance.getInstanceId());
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("mapeditor")) {
            if (args.length == 1) {
                completions.add("create");
                File[] worldFiles = worldsFolder.listFiles();
                if (worldFiles != null) {
                    for (File file : worldFiles) {
                        if (file.isDirectory()) {
                            completions.add(file.getName());
                        }
                    }
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("create")) {
                    completions.add("<mapname>");
                } else {
                    completions.add("off");
                }
            }
        } else if (command.getName().equalsIgnoreCase("stats")) {
            if (args.length == 1) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (command.getName().equalsIgnoreCase("kit")) {
            if (args.length == 1) {
                for (Kit kit : kitManager.getKits().values()) {
                    completions.add(kit.getName().toLowerCase());
                }
            }
        } else if (command.getName().equalsIgnoreCase("setfeature")) {
            if (args.length == 1) {
                completions.add("luckyblock");
                completions.add("spawnpunkt");
            }
        } else if (command.getName().equalsIgnoreCase("removefeature")) {
            if (args.length == 1) {
                completions.add("luckyblock");
                completions.add("spawnpunkt");
            } else if (args.length == 2) {
                completions.add("5");
                completions.add("10");
                completions.add("20");
                completions.add("50");
            }
        } else if (command.getName().equalsIgnoreCase("visualize")) {
            if (args.length == 1) {
                completions.add("on");
                completions.add("off");
            }
        } else if (command.getName().equalsIgnoreCase("swlanguage")) {
            if (args.length == 1) {
                completions.add("de");
                completions.add("en");
            }
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .sorted()
            .toList();
    }
}
