package de.groupxyz.treasurehunt;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import de.groupxyz.treasurehunt.Mapcreator;

public final class Treasurehunt extends JavaPlugin implements Listener {

    private Map<UUID, Location> treasureLocations = new HashMap<>();
    private Set<UUID> playersInHunt = new HashSet<>();
    private UUID winner = null;
    private Player winnername = null;
    private Boolean GamemodeWarningTold = false;
    private boolean isTreasurePlaced = false;
    private Location customTreasureLocation = null;

    private File configFile;
    private FileConfiguration config;

    String serverPrefix = loadServerPrefix();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Treasurehunt by GroupXyz initialized!");
        loadTreasurehuntConfig();
    }

    @Override
    public void onDisable() {
        getLogger().info("Treasurehunt shutting down...");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED +  "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("treasurehunt")) {
            startTreasureHunt(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntdebug")) {
            showTreasureLocation(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntreload")) {
            reloadConfig();
            loadServerPrefix();
            player.sendMessage(serverPrefix + ChatColor.YELLOW + "Config reloaded!");
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntversion")) {
            player.sendMessage(serverPrefix + ChatColor.GREEN + "The author of treasurehunt is GroupXyz and the version of Treasurehunt is: " + getPluginMeta().getVersion());
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntcustom")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /treasurehuntcustom <X> <Y> <Z>");
                return true;
            }
            int x, y, z;
            try {
                x = Integer.parseInt(args[0]);
                y = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid Corrdinates.");
                return true;
            }

            startCustomTreasureHunt(player, new Location(player.getWorld(), x, y, z));
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntrange")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /treasurehuntrange <min> <max>");
                return true;
            }
            int min, max;
            try {
                min = Integer.parseInt(args[0]);
                max = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid range values.");
                return true;
            }

            if (min >= max) {
                player.sendMessage(ChatColor.RED + "Minimum value must be less than maximum value.");
                return true;
        }

        config.set("treasureRange.min", min);
        config.set("treasureRange.max", max);
        saveTreasurehuntConfig();

        player.sendMessage(ChatColor.GREEN + "Treasure range set to: " + min + " to " + max);
        return true;
    }


        return false;
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

    private void startTreasureHunt(Player triggerPlayer) {
        playersInHunt.clear();
        treasureLocations.clear();

        Location treasureLocation;

        if (customTreasureLocation== null) {
            treasureLocation = generateRandomLocation(triggerPlayer.getWorld());
        } else {
            treasureLocation = customTreasureLocation;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            treasureLocations.put(onlinePlayer.getUniqueId(), treasureLocation);
            giveCompassToPlayer(onlinePlayer, treasureLocation, ChatColor.YELLOW + "Schatz-Kompass");
            playersInHunt.add(onlinePlayer.getUniqueId());
        }

        short mapId = Mapcreator.createTreasureMap(triggerPlayer.getWorld(), treasureLocation);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(serverPrefix + ChatColor.DARK_AQUA + "Eine Schatzsuche hat begonnen!");
            getLogger().info("A Treasurehunt has started!");
        }
    }

    private void startCustomTreasureHunt(Player triggerPlayer, Location customLocation) {
        customTreasureLocation = customLocation;
        startTreasureHunt(triggerPlayer);
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
                player.sendMessage(serverPrefix + ChatColor.GREEN + "Du hast eine Schatzkarte erhalten! Folge den Hinweisen, um den Schatz zu finden.");
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

            player.sendMessage(serverPrefix + ChatColor.GREEN + "Du hast einen Schatz-Kompass erhalten! Folge ihm, um den Schatz zu finden.");
        }, 20L);
    }

    private boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }

    private void showTreasureLocation(Player player) {
        if (treasureLocations.containsKey(player.getUniqueId())) {
            Location treasureLocation = treasureLocations.get(player.getUniqueId());
            player.sendMessage(serverPrefix + ChatColor.YELLOW + "Schatzkoordinaten: X=" + treasureLocation.getBlockX() +
                    ", Y=" + treasureLocation.getBlockY() + ", Z=" + treasureLocation.getBlockZ());
        } else {
            player.sendMessage(serverPrefix + ChatColor.RED + "Du nimmst nicht an einer Schatzsuche teil.");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        try {
            if (playersInHunt.contains(playerUUID)) {
                Location treasureLocation = treasureLocations.get(playerUUID);

                if (treasureLocation != null) {
                    if (player.getLocation().distance(treasureLocation) < 5.0 && player.getGameMode().equals(GameMode.SURVIVAL)) {
                        checkPlayerWin(player);
                    } else if (player.getLocation().distance(treasureLocation) < 5.0 && !player.getGameMode().equals(GameMode.SURVIVAL) && !GamemodeWarningTold) {
                        player.sendMessage(serverPrefix + ChatColor.YELLOW + ("Treasure room not build, you aren't in survival mode!"));
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
        if (!isTreasurePlaced) {
            World world = player.getWorld();
            int centerX = location.getBlockX();
            int centerZ = location.getBlockZ();
            int centerY = world.getHighestBlockYAt(centerX, centerZ) - 10;

            createTreasureRoom(world, centerX, centerY, centerZ);

            world.getBlockAt(centerX, centerY - 1, centerZ).setType(Material.CHEST);

            BlockState state = world.getBlockAt(centerX, centerY - 1, centerZ).getState();
            if (state instanceof Chest) {
                Chest chest = (Chest) state;
                Inventory chestInventory = chest.getInventory();

                FileConfiguration config = getConfig();
                ConfigurationSection lootSection = config.getConfigurationSection("loot");
                if (lootSection != null) {
                    for (String key : lootSection.getKeys(false)) {
                        String path = "loot." + key;
                        ItemStack lootItem = createLootItem(config, path);

                        if (lootItem != null) {
                            chestInventory.setItem(config.getInt(path + ".slot"), lootItem);
                            getLogger().info("Loaded loot item: " + lootItem.getType() + " in slot " + config.getInt(path + ".slot"));
                        } else {
                            getLogger().warning("Failed to load loot item for path: " + path);
                        }
                    }
                } else {
                    getLogger().warning("Could not find 'loot' section in the configuration.");
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
        if (winner == null) {
            winner = playerUUID;
            winnername = player;
            player.sendMessage(serverPrefix + ChatColor.GREEN + "Du hast die Schatzsuche gewonnen! Herzlichen Glückwunsch!");
            getServer().broadcastMessage(serverPrefix + ChatColor.YELLOW + player.getName() + " hat den Schatz gefunden. Die Schatzsuche ist vorbei!");
            getLogger().info("Treasurehunt ended, the winner is " + player.getName());
            giveTreasure(player, treasureLocations.get(playerUUID));
            reset();
        } else {
            getLogger().warning("Tried to add winner but winner already exists!");
            player.sendMessage(serverPrefix + ChatColor.YELLOW + "Leider hast du nicht gewonnen. Versuche es beim nächsten Mal!");
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
        int roomWidth = 10;
        int roomHeight = 5;
        int roomDepth = 10;

        Material[] wallMaterials = {
                Material.STONE_BRICKS,
                Material.COBBLESTONE,
                Material.MOSSY_COBBLESTONE
        };

        for (int x = centerX - roomWidth/2; x <= centerX + roomWidth/2; x++) {
            for (int z = centerZ - roomDepth/2; z <= centerZ + roomDepth/2; z++) {
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

        for (int x = centerX - roomWidth/2 + 1; x <= centerX + roomWidth/2 - 1; x++) {
            for (int z = centerZ - roomDepth/2 + 1; z <= centerZ + roomDepth/2 - 1; z++) {
                world.getBlockAt(x, centerY - roomHeight, z).setType(Material.SMITHING_TABLE);
            }
        }

        for (int x = centerX - roomWidth/2; x <= centerX + roomWidth/2; x++) {
            for (int z = centerZ - roomDepth/2 + 1; z <= centerZ + roomDepth/2 - 1; z++) {
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


    private void reset() {
        winner = null;
        customTreasureLocation = null;
        playersInHunt.clear();
        treasureLocations.clear();
        GamemodeWarningTold = false;
        isTreasurePlaced = false;
    }

}
