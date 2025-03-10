package de.groupxyz.treasurehunt;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.session.ClipboardHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import de.groupxyz.treasurehunt.Mapcreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.Clip;

public final class Treasurehunt extends JavaPlugin implements Listener {

    private Map<UUID, Location> treasureLocations = new HashMap<>();
    private Set<UUID> playersInHunt = new HashSet<>();
    private UUID winner = null;
    private Player winnername = null;
    Location lastTreasureLocation = null;
    Boolean isTreasurehuntRunning = false;
    private Boolean GamemodeWarningTold = false;
    private Boolean isTreasurePlaced = false;

    private File configFile;
    private FileConfiguration config;
    private FileConfiguration languageConfig;
    double @NotNull [] serverTPS = getServer().getTPS();
    double recentTPS = serverTPS[serverTPS.length - 1];
    String serverVersion = getServer().getName();

    String serverPrefix = loadServerPrefix();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Treasurehunt by GroupXyz initialized!");
        loadTreasurehuntConfig();
        loadLanguageConfig();
        copyDefaultSchematics();
        int pluginId = 21507;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("server_version", () -> {
            return String.valueOf(serverVersion);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("server_tps", () -> {
            return String.valueOf(recentTPS);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("server_prefix", () -> {
            return String.valueOf(serverPrefix);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("winner_count", () -> {
            return String.valueOf(winnername);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("participants_count", () -> {
            return String.valueOf((long) playersInHunt.size());
        }));
        metrics.addCustomChart(new Metrics.SimplePie("treasure_count", () -> {
            return String.valueOf((long) treasureLocations.size());
        }));
        metrics.addCustomChart(new Metrics.SimplePie("used_language", () -> {
            return getConfig().getString("language", "en");
        }));

        if (!getConfig().contains("chestoffset")) {
            getConfig().set("chestoffset", 1);
            saveTreasurehuntConfig();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Treasurehunt shutting down...");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("treasurehuntc")) {
            if (args.length < 1) {
                sender.sendMessage("/treasurehuntc <world>");
                return true;
            }

            if (!(sender instanceof ConsoleCommandSender)) {
                return true;
            }

            startTreasureHuntConsole(null, args[0], sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getLocalizedString("commands.error_no_player"));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("treasurehunt")) {
            startTreasureHunt(player, null);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntdebug")) {
            showTreasureLocation(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntreload")) {
            reloadConfig();
            loadServerPrefix();
            player.sendMessage(serverPrefix + getLocalizedString("config.config_reloaded"));
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntversion")) {
            player.sendMessage(serverPrefix + getLocalizedString("config.author_version") + getPluginMeta().getVersion());
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntcustom")) {
            if (args.length < 3) {
                player.sendMessage(getLocalizedString("commands.treasurehuntcustom_usage"));
                return true;
            }
            int x, y, z;
            try {
                x = Integer.parseInt(args[0]);
                y = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(getLocalizedString("config.invalid_coordinates"));
                return true;
            }

            startCustomTreasureHunt(player, new Location(player.getWorld(), x, y, z));
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntrange")) {
            if (args.length < 2) {
                player.sendMessage(getLocalizedString("commands.treasurehuntrange_usage"));
                return true;
            }
            int min, max;
            try {
                min = Integer.parseInt(args[0]);
                max = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(getLocalizedString("config.invalid_range"));
                return true;
            }

            if (min >= max) {
                player.sendMessage(getLocalizedString("config.invalid_minimum_value"));
                return true;
            }

            config.set("treasureRange.min", min);
            config.set("treasureRange.max", max);
            saveTreasurehuntConfig();

            player.sendMessage(getLocalizedString("config.treasure_range_set") + " " + min + " " + max);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntlanguage")) {
            if (args.length < 1) {
                player.sendMessage(getLocalizedString("commands.treasurehuntlanguage_usage"));
                return true;
            }

            String langCode = args[0].toLowerCase();

            if (!Arrays.asList("en", "de", "sp").contains(langCode)) {
                player.sendMessage(getLocalizedString("config.invalid_language_code"));
                return true;
            }

            config.set("language", langCode);
            saveTreasurehuntConfig();
            reloadConfig();

            loadLanguageConfig();
            player.sendMessage(getLocalizedString("config.language_set") + langCode);

            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntuseloottables")) {
            if (args.length < 1) {
                player.sendMessage(getLocalizedString("commands.treasurehuntuseloottables_usage"));
                return true;
            }

            if (!args[0].equalsIgnoreCase("true") && !args[0].equalsIgnoreCase("false")) {
                player.sendMessage(getLocalizedString("commands.treasurehuntuseloottables_usage"));
                return true;
            }

            boolean useLootTables;
            try {
                useLootTables = Boolean.parseBoolean(args[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }

            if (useLootTables) {
                config.set("useLootTables", true);
                saveTreasurehuntConfig();
                player.sendMessage(getLocalizedString("config.loottables_success") + "true");
                return true;
            }

            config.set("useLootTables", false);
            saveTreasurehuntConfig();
            player.sendMessage(getLocalizedString("config.loottables_success") + "false");

            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntcompassautoremove")) {
            if (args.length < 1) {
                player.sendMessage(getLocalizedString("commands.treasurehuntcompassautoremove_usage"));
                return true;
            }

            if (!args[0].equalsIgnoreCase("true") && !args[0].equalsIgnoreCase("false")) {
                player.sendMessage(getLocalizedString("commands.treasurehuntcompassautoremove_usage"));
                return true;
            }

            boolean autoRemoveCompass;
            try {
                autoRemoveCompass = Boolean.parseBoolean(args[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }

            if (autoRemoveCompass) {
                config.set("autoRemoveCompass", true);
                saveTreasurehuntConfig();
                player.sendMessage(getLocalizedString("config.removecompass_success") + "true");
                return true;
            }

            config.set("autoRemoveCompass", false);
            saveTreasurehuntConfig();
            player.sendMessage(getLocalizedString("config.removecompass_success") + "false");

            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntloadtreasureroomfromstructure")) {
            if (args.length < 1) {
                player.sendMessage(getLocalizedString("commands.treasurehuntloadstructure_usage"));
                return true;
            }

            if (!args[0].equalsIgnoreCase("true") && !args[0].equalsIgnoreCase("false")) {
                player.sendMessage(getLocalizedString("commands.treasurehuntloadstructure_usage"));
                return true;
            }

            boolean autoRemoveCompass;
            try {
                autoRemoveCompass = Boolean.parseBoolean(args[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }

            if (autoRemoveCompass) {
                config.set("loadTreasureroomFromStructure", true);
                saveTreasurehuntConfig();
                player.sendMessage(getLocalizedString("config.loadstructure_success") + "true");
                return true;
            }

            config.set("loadTreasureroomFromStructure", false);
            saveTreasurehuntConfig();
            player.sendMessage(getLocalizedString("config.loadstructure_success") + "false");

            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntglide")) {
            glideToTreasure(player);
        }

        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Inventory inventory = player.getInventory();
        if (isTreasurehuntRunning) {
            if (!playersInHunt.contains(player.getUniqueId())) {
                boolean hasCompass = false;
                String compassName = getLocalizedString("messages.compass_name");

                for (ItemStack item : inventory.getContents()) {
                    if (item != null && item.getType() == Material.COMPASS && item.getItemMeta().getDisplayName().equals(compassName)) {
                        hasCompass = true;
                        break;
                    }
                }

                if (!hasCompass) {
                    treasureLocations.put(player.getUniqueId(), lastTreasureLocation);
                    giveCompassToPlayer(player, lastTreasureLocation, compassName);
                    playersInHunt.add(player.getUniqueId());
                }
            }
        }
    }


    private void loadTreasurehuntConfig() {
        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        config.options().copyDefaults(true);
        saveTreasurehuntConfig();
    }

    private void saveTreasurehuntConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String loadServerPrefix() {
        FileConfiguration config = getConfig();
        return ChatColor.translateAlternateColorCodes('&', config.getString("serverPrefix", "[&e&k1&r&eTreasure&3Hunt&k1&r] "));
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

    private void copyDefaultSchematics() {
        File structuresFolder = new File(getDataFolder(), "structures");
        if (!structuresFolder.exists()) {
            structuresFolder.mkdirs();
        }

        Boolean copyEnabled = config.getBoolean("copyExampleSchematics");

        if (copyEnabled) {
            String[] schematics = {"example_treasure_room1", "example_treasure_room2", "example_treasure_room3.schem"};

            for (String schematic : schematics) {
                File targetFile = new File(structuresFolder, schematic);
                if (!targetFile.exists()) {
                    try (InputStream in = getResource("structures/" + schematic)) {
                        if (in != null) {
                            Files.copy(in, targetFile.toPath());
                        } else {
                            getLogger().warning("Resource not found: structures/" + schematic);
                        }
                    } catch (IOException e) {
                        getLogger().severe("Failed to copy schematic file: " + schematic);
                        e.printStackTrace();
                    }
                }
            }
            config.set("copyExampleSchematics", false);
        }
    }


    private String getLocalizedString(String key, Object... args) {
        String rawMessage = languageConfig.getString(key, "Translation not found for key: " + key + " (If this happens after an Update, delete de.yml, en.yml, sp.yml in the plugin's config folder and restart)");
        return ChatColor.translateAlternateColorCodes('&', String.format(rawMessage, args));
    }



    private void startTreasureHunt(Player triggerPlayer, @Nullable Location treasureLocation) {
        reset();

        if (treasureLocation == null) {
            treasureLocation = generateRandomLocation(triggerPlayer.getWorld());
        } else {
            getLogger().info("Custom treasure location " + treasureLocation + " used");
        }

        lastTreasureLocation = treasureLocation;
        isTreasurehuntRunning = true;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            treasureLocations.put(onlinePlayer.getUniqueId(), treasureLocation);
            giveCompassToPlayer(onlinePlayer, treasureLocation, getLocalizedString("messages.compass_name"));
            playersInHunt.add(onlinePlayer.getUniqueId());
        }

        short mapId = Mapcreator.createTreasureMap(triggerPlayer.getWorld(), treasureLocation);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(serverPrefix + getLocalizedString("messages.treasure_start"));
            getLogger().info("A Treasurehunt has started!");
        }
    }

    private void startTreasureHuntConsole(@Nullable Location treasureLocation, String worldname, CommandSender sender) {
        reset();

        World startworld = null;

        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equals(worldname)) {
                startworld = world;
                break;
            }
        }

        if (startworld == null) {
            sender.sendMessage(worldname + " not found!");
            return;
        }

        if (treasureLocation == null) {
            treasureLocation = generateRandomLocation(startworld);
        } else {
            getLogger().info("Custom treasure location " + treasureLocation + " used");
        }

        lastTreasureLocation = treasureLocation;
        isTreasurehuntRunning = true;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            treasureLocations.put(onlinePlayer.getUniqueId(), treasureLocation);
            giveCompassToPlayer(onlinePlayer, treasureLocation, getLocalizedString("messages.compass_name"));
            playersInHunt.add(onlinePlayer.getUniqueId());
        }

        short mapId = Mapcreator.createTreasureMap(startworld, treasureLocation);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(serverPrefix + getLocalizedString("messages.treasure_start"));
            getLogger().info("A Treasurehunt has started!");
        }
    }

    private void startCustomTreasureHunt(Player triggerPlayer, Location customLocation) {
        startTreasureHunt(triggerPlayer, customLocation);
    }

    private void giveMapToPlayer(Player player, short mapId, Location treasureLocation) {
        ItemStack treasureMap = new ItemStack(Material.MAP);
        MapView mapView = Bukkit.getMap(mapId);

        if (mapView != null) {
            mapView.getRenderers().forEach(mapView::removeRenderer);
            Mapcreator mapRenderer = new Mapcreator(treasureLocation);
            mapView.addRenderer(mapRenderer);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                treasureMap.setDurability((short) 0);
                player.getInventory().addItem(treasureMap);
                player.sendMessage(serverPrefix + getLocalizedString("messages.map_received"));
            }, 20L);
        } else {
            getLogger().warning("MapView for ID " + mapId + " is null.");
        }
    }

    private void giveCompassToPlayer(Player player, Location treasureLocation, String compassName) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();

        compassMeta.setDisplayName(compassName);
        compassMeta.addEnchant(Enchantment.DURABILITY, 3, true);
        compass.setItemMeta(compassMeta);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setCompassTarget(treasureLocation);

            if (isInventoryFull(player)) {
                player.getWorld().dropItemNaturally(player.getLocation(), compass);
            } else {
                player.getInventory().addItem(compass);
            }

            player.sendMessage(serverPrefix + getLocalizedString("messages.compass_received"));
        }, 20L);
    }

    private boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }

    private void showTreasureLocation(Player player) {
        if (treasureLocations.containsKey(player.getUniqueId())) {
            Location treasureLocation = treasureLocations.get(player.getUniqueId());
            player.sendMessage(serverPrefix + getLocalizedString("messages.treasure_location") + treasureLocation.getBlockX() + ", Y=" + treasureLocation.getBlockY() + ", Z=" + treasureLocation.getBlockZ());
        } else {
            player.sendMessage(serverPrefix + getLocalizedString("messages.no_participation"));
        }
    }

    private void glideToTreasure(Player player) {
        if (treasureLocations.containsKey(player.getUniqueId())) {
            Location treasureLocation = treasureLocations.get(player.getUniqueId());
            Plugin glide = Bukkit.getPluginManager().getPlugin("glide");
            if (glide != null && glide.isEnabled()) {
                String commandToExecute = "glide " + treasureLocation.getBlockX() + " " + treasureLocation.getBlockY() + " " + treasureLocation.getBlockZ();
                player.performCommand(commandToExecute);

            } else {
                player.sendMessage(serverPrefix + getLocalizedString("messages.no_glide"));
            }
        } else {
            player.sendMessage(serverPrefix + getLocalizedString("messages.no_participation"));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        try {
            if (playersInHunt.contains(playerUUID)) {
                Location treasureLocation = treasureLocations.get(playerUUID);

                if (treasureLocation != null && player.getWorld().equals(treasureLocation.getWorld())) {
                    if (player.getLocation().distance(treasureLocation) < 5.0 && player.getGameMode().equals(GameMode.SURVIVAL)) {
                        checkPlayerWin(player);
                    } else if (player.getLocation().distance(treasureLocation) < 5.0 && !player.getGameMode().equals(GameMode.SURVIVAL) && !GamemodeWarningTold) {
                        player.sendMessage(serverPrefix + ChatColor.YELLOW + getLocalizedString("messages.gamemode_warning"));
                        GamemodeWarningTold = true;
                    }
                } else {
                    getLogger().warning("Treasure location is null!");
                }
            }

        } catch (Exception e) {
            getLogger().warning("Treasurehunt Exception: " + e.getMessage());
        }
    }

    private Location generateRandomLocation(World world) {
        Random random = new Random();
        int min = config.getInt("treasureRange.min", -10000);
        int max = config.getInt("treasureRange.max", 10000);
        int x = random.nextInt(max - min + 1) + min;
        int z = random.nextInt(max - min + 1) + min;
        int y = world.getHighestBlockYAt(x, z);

        return new Location(world, x, y, z);
    }

    private void giveTreasure(Player player, Location location) {
        boolean useLootTables = config.getBoolean("useLootTables", false);

        if (!isTreasurePlaced) {
            World world = player.getWorld();
            int centerX = location.getBlockX();
            int centerZ = location.getBlockZ();
            int centerY = world.getHighestBlockYAt(centerX, centerZ) - 10;

            boolean generateStructure = config.getBoolean("generateStructure", true);

            if (generateStructure) {
                createTreasureRoom(world, centerX, centerY, centerZ);
            }

            //int chestoffset = config.getInt("chestoffset", 1);
            int chestoffset = 3;

            world.getBlockAt(centerX, centerY + chestoffset, centerZ).setType(Material.CHEST);

            BlockState state = world.getBlockAt(centerX, centerY + chestoffset, centerZ).getState();
            if (state instanceof Chest) {
                Chest chest = (Chest) state;
                Inventory chestInventory = chest.getInventory();

                if (useLootTables) {
                    List<String> activeLootTables = new ArrayList<>();
                    ConfigurationSection lootTablesSection = config.getConfigurationSection("lootTables");
                    if (lootTablesSection != null) {
                        for (String key : lootTablesSection.getKeys(false)) {
                            String path = "lootTables." + key;
                            if (config.getBoolean(path + ".active", false)) {
                                activeLootTables.add(config.getString(path + ".name"));
                            }
                        }
                    } else {
                        getLogger().warning("Loot tables section is null!");
                    }

                    if (!activeLootTables.isEmpty()) {
                        Random random = new Random();
                        String selectedLootTable = activeLootTables.get(random.nextInt(activeLootTables.size()));
                        try {
                            NamespacedKey namespacedKey = NamespacedKey.fromString(selectedLootTable);
                            LootTable lootTable = Bukkit.getLootTable(namespacedKey);
                            chest.setLootTable(lootTable);
                            chest.update();
                            getLogger().info("Loottable " + lootTable.toString() + " loaded in chest at " + chest.getLocation());
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid loot table key: " + selectedLootTable);
                        }
                    } else {
                        getLogger().warning("No active loot tables found in the configuration.");
                    }
                } else {
                    ConfigurationSection lootSection = config.getConfigurationSection("loot");
                    if (lootSection != null) {
                        for (String key : lootSection.getKeys(false)) {
                            String path = "loot." + key;
                            ItemStack lootItem = createLootItem(config, path);

                            if (lootItem != null) {
                                chestInventory.setItem(config.getInt(path + ".slot"), lootItem);
                                getLogger().info("Loaded loot item: " + lootItem.getType() + " in slot " + config.getInt(path + ".slot"));
                            } else {
                                getLogger().info("Didn't load loot item for path: " + path);
                            }
                        }
                    } else {
                        getLogger().warning("Could not find 'loot' section in the configuration.");
                    }
                }
            } else {
                getLogger().warning("Chest not found at location: " + location);
            }
        } else {
            getLogger().warning("Treasure already placed!");
        }
    }

    private synchronized void checkPlayerWin(Player player) {
        UUID playerUUID = player.getUniqueId();
        boolean autoRemoveCompass = config.getBoolean("autoRemoveCompass", false);
        if (winner == null) {
            winner = playerUUID;
            winnername = player;
            player.sendMessage(serverPrefix + getLocalizedString("messages.treasure_found"));
            getServer().broadcastMessage(serverPrefix + ChatColor.YELLOW + player.getName() + getLocalizedString("messages.treasure_end"));
            getLogger().info("Treasurehunt ended, the winner is " + player.getName());
            giveTreasure(player, treasureLocations.get(playerUUID));
            isTreasurehuntRunning = false;
            if (autoRemoveCompass) {
                removeCompassFromAllPlayers();
            }

            reset();
        } else {
            getLogger().warning("Tried to add winner but winner already exists!");
            player.sendMessage(serverPrefix + getLocalizedString("messages.treasure_not_found"));
        }
    }

    private void removeCompassFromAllPlayers() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : onlinePlayer.getInventory().getContents()) {
                String compassName = getLocalizedString("messages.compass_name");
                if (item != null && item.getType() == Material.COMPASS && item.getItemMeta().getDisplayName().equals(compassName)) {
                    onlinePlayer.getInventory().remove(item);
                }
            }
        }
    }


    private ItemStack createLootItem(FileConfiguration config, String path) {
        ItemStack lootItem = null;

        String materialName = config.getString(path + ".material");
        Material material = Material.getMaterial(materialName);

        if (material != null) {
            lootItem = new ItemStack(material);

            if (config.contains(path + ".enchantments")) {
                ConfigurationSection enchantmentsSection = config.getConfigurationSection(path + ".enchantments");
                    for (String enchantmentKey : enchantmentsSection.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByName(enchantmentKey);
                    if (enchantment != null) {
                        int level = enchantmentsSection.getInt(enchantmentKey);
                        lootItem.addUnsafeEnchantment(enchantment, level);
                    }
                }
            }

            if (config.contains(path + ".amount")) {
                int amount = config.getInt(path + ".amount");
                lootItem.setAmount(amount);
            }

            if (config.contains(path + ".name")) {
                String customName = ChatColor.translateAlternateColorCodes('&', config.getString(path + ".name"));
                ItemMeta meta = lootItem.getItemMeta();

                if (config.contains(path + ".lore")) {
                    List<String> lore = config.getStringList(path + ".lore");
                    lore = lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList());
                    meta.setLore(lore);
                }

                meta.setDisplayName(customName);
                lootItem.setItemMeta(meta);
            }
        }

        return lootItem;
    }

    private void createTreasureRoom(World world, int centerX, int centerY, int centerZ) {

        boolean loadTreasureroomFromStructure = config.getBoolean("loadTreasureroomFromStructure", false);

        if (loadTreasureroomFromStructure) {
            pasteStructureFromSchematic(world, new Location(world, centerX, centerY, centerZ));
        } else {

            int roomWidth = 10;
            int roomHeight = 5;
            int roomDepth = 10;

            Material[] wallMaterials = {
                    Material.STONE_BRICKS,
                    Material.COBBLESTONE,
                    Material.MOSSY_COBBLESTONE
            };

            for (int x = centerX - roomWidth / 2; x <= centerX + roomWidth / 2; x++) {
                for (int z = centerZ - roomDepth / 2; z <= centerZ + roomDepth / 2; z++) {
                    for (int y = centerY - roomHeight; y <= centerY + 1; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }

            for (int x = centerX - roomWidth / 2; x <= centerX + roomWidth / 2; x++) {
                for (int z = centerZ - roomDepth / 2; z <= centerZ + roomDepth / 2; z++) {
                    for (int y = centerY - roomHeight; y <= centerY; y++) {
                        if (x == centerX - roomWidth / 2 || x == centerX + roomWidth / 2 || z == centerZ - roomDepth / 2 || z == centerZ + roomDepth / 2) {
                            Material wallMaterial = wallMaterials[(int) (Math.random() * wallMaterials.length)];
                            world.getBlockAt(x, y, z).setType(wallMaterial);
                        }
                    }
                }
            }

            for (int x = centerX - roomWidth / 2 + 1; x <= centerX + roomWidth / 2 - 1; x++) {
                for (int z = centerZ - roomDepth / 2 + 1; z <= centerZ + roomDepth / 2 - 1; z++) {
                    world.getBlockAt(x, centerY - roomHeight, z).setType(Material.SMITHING_TABLE);
                }
            }

            for (int x = centerX - roomWidth / 2; x <= centerX + roomWidth / 2; x++) {
                for (int z = centerZ - roomDepth / 2 + 1; z <= centerZ + roomDepth / 2 - 1; z++) {
                    world.getBlockAt(x, centerY + 1, z).setType(Material.CRACKED_STONE_BRICKS);
                }
            }

            world.getBlockAt(centerX, centerY - roomHeight + 1, centerZ).setType(Material.DIAMOND_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 2, centerZ).setType(Material.DIAMOND_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 3, centerZ).setType(Material.DIAMOND_BLOCK);

            world.getBlockAt(centerX, centerY - roomHeight + 1, centerZ + 1).setType(Material.GOLD_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 1, centerZ - 1).setType(Material.GOLD_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 2, centerZ + 1).setType(Material.GOLD_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 2, centerZ - 1).setType(Material.GOLD_BLOCK);
            world.getBlockAt(centerX + 1, centerY - roomHeight + 1, centerZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(centerX - 1, centerY - roomHeight + 1, centerZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(centerX + 1, centerY - roomHeight + 2, centerZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(centerX - 1, centerY - roomHeight + 2, centerZ).setType(Material.EMERALD_BLOCK);
            world.getBlockAt(centerX + 2, centerY - roomHeight + 1, centerZ).setType(Material.COAL_BLOCK);
            world.getBlockAt(centerX - 2, centerY - roomHeight + 1, centerZ).setType(Material.COAL_BLOCK);
            world.getBlockAt(centerX, centerY - roomHeight + 2, centerZ).setType(Material.ANCIENT_DEBRIS);
            world.getBlockAt(centerX, centerY - roomHeight + 1, centerZ + 2).setType(Material.BREWING_STAND);
            world.getBlockAt(centerX, centerY - roomHeight + 1, centerZ - 2).setType(Material.ANVIL);
            world.getBlockAt(centerX, centerY - roomHeight + 3, centerZ + 1).setType(Material.TORCH);
            world.getBlockAt(centerX, centerY - roomHeight + 3, centerZ - 1).setType(Material.TORCH);
        }

    }

    private void pasteStructureFromSchematic(org.bukkit.World bukkitWorld, Location location) {
        File folder = new File(getDataFolder(), "structures");
        if (!folder.exists() || !folder.isDirectory()) {
            getLogger().warning("Structures folder not found.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (files == null || files.length == 0) {
            getLogger().warning("No schematic files found in the structures folder.");
            return;
        }

        File structureFile = files[new Random().nextInt(files.length)];
        getLogger().info("Selected random structure file: " + structureFile.getName());

        String structureName = structureFile.getName();

        Clipboard clipboard;
        try (FileInputStream inputStream = new FileInputStream(structureFile)) {
            ClipboardFormat format = ClipboardFormats.findByFile(structureFile);
            if (format == null) {
                getLogger().warning("Unknown clipboard format for file: " + structureFile.getName());
                return;
            }

            try (ClipboardReader reader = format.getReader(inputStream)) {
                clipboard = reader.read();
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load structure: " + structureName, e);
            return;
        }

        com.sk89q.worldedit.world.World adaptedWorld = new BukkitWorld(bukkitWorld);
        EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld);

        Mask mask = blockVector3 -> {
            Block block = location.getBlock();
            return block.getType() != Material.CHEST;
        };


        try {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BukkitAdapter.asBlockVector(location))
                    .maskSource(mask)
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            editSession.flushSession();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to paste structure: " + structureName, e);
        }
    }

    private void reset() {
        winner = null;
        playersInHunt.clear();
        treasureLocations.clear();
        GamemodeWarningTold = false;
        isTreasurePlaced = false;
    }

}

