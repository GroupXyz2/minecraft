package de.groupxyz.treasurehunt;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import de.groupxyz.treasurehunt.Mapcreator;
import org.slf4j.Logger;

public final class Treasurehunt extends JavaPlugin implements Listener {

    private Map<UUID, Location> treasureLocations = new HashMap<>();
    private Set<UUID> playersInHunt = new HashSet<>();
    private UUID winner = null;

    private Boolean GamemodeWarningTold = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Treasurehunt by GroupXyz initialized!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Treasurehunt shutting down...");
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("treasurehunt")) {
            startTreasureHunt(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("treasurehuntdebug")) {
            showTreasureLocation(player);
            return true;
        }

        return false;
    }

    private void startTreasureHunt(Player triggerPlayer) {
        Location treasureLocation = generateRandomLocation(triggerPlayer.getWorld());
        treasureLocations.put(triggerPlayer.getUniqueId(), treasureLocation);
        playersInHunt.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList());

        short mapId = Mapcreator.createTreasureMap(triggerPlayer.getWorld(), treasureLocation);
        for (UUID playerUUID : playersInHunt) {
            Player participant = Bukkit.getPlayer(playerUUID);
            if (participant != null) {
                giveMapToPlayer(participant, mapId, treasureLocation);
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(ChatColor.GREEN + "Schatzsuche gestartet! Eine Wettjagd hat begonnen.");
            getLogger().info("A Treasurehunt has started!");
        }
    }

    private void giveMapToPlayer(Player player, short mapId, Location treasureLocation) {
        ItemStack treasureMap = new ItemStack(Material.MAP);
        MapView mapView = Bukkit.getMap(mapId);

        if (mapView != null) {
            mapView.getRenderers().forEach(mapView::removeRenderer);
            Mapcreator mapRenderer = new Mapcreator(treasureLocation);
            mapView.addRenderer(mapRenderer);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                treasureMap.setDurability(mapId);
                player.getInventory().addItem(treasureMap);
                player.sendMessage(ChatColor.GREEN + "Du hast eine Schatzkarte erhalten! Folge den Hinweisen, um den Schatz zu finden.");
            }, 20L);
            //TODO: Fix Treasure Map not rendering
        }
    }

    private void showTreasureLocation(Player player) {
        if (treasureLocations.containsKey(player.getUniqueId())) {
            Location treasureLocation = treasureLocations.get(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Schatzkoordinaten: X=" + treasureLocation.getBlockX() +
                    ", Y=" + treasureLocation.getBlockY() + ", Z=" + treasureLocation.getBlockZ());
        } else {
            player.sendMessage(ChatColor.RED + "Du nimmst nicht an einer Schatzsuche teil.");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playersInHunt.contains(playerUUID)) {
            Location treasureLocation = treasureLocations.get(playerUUID);

            if (player.getLocation().distance(treasureLocation) < 20.0 && player.getGameMode().equals(GameMode.SURVIVAL)) {
                if (winner == null) {
                    winner = playerUUID;
                    for (UUID uuid : playersInHunt) {
                        Player onlinePlayer = Bukkit.getPlayer(uuid);
                        if (onlinePlayer != null) {
                            if (uuid.equals(winner)) {
                                onlinePlayer.sendMessage(ChatColor.YELLOW + player.getName() + " hat den Schatz gefunden. Die Wettjagd ist vorbei!");
                                getLogger().info("Treasurehunt ended, the winner is " + player.getName() );
                                giveTreasure(player, treasureLocation);
                                reset();
                            } else {
                                onlinePlayer.sendMessage(ChatColor.YELLOW + player.getName() + " hat den Schatz gefunden. Die Wettjagd ist vorbei!");
                                getLogger().info("Treasurehunt ended, the winner is " + player.getName() );
                                reset();
                            }
                        }
                    }
                    playersInHunt.clear();
                }
            }
            else if (player.getLocation().distance(treasureLocation) < 20.0 && !player.getGameMode().equals(GameMode.SURVIVAL) && !GamemodeWarningTold) {
                player.sendMessage(ChatColor.YELLOW + ("Treasure room not build, you aren't in survival mode!"));
                GamemodeWarningTold = true;
                //TODO: Fix Treasure Room variables not resetting properly and Treasure Room Command beeing unable to be reused!
            }
        }
    }

    private Location generateRandomLocation(World world) {
        Random random = new Random();
        int x = random.nextInt(10000) - 5000; //TODO: Tweaking required, currently spawning at ca. 5000
        int z = random.nextInt(10000) - 5000;
        int y = world.getHighestBlockYAt(x, z);

        return new Location(world, x, y, z);
    }

    private void giveTreasure(Player player, Location location) {
        World world = player.getWorld();
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int centerY = world.getHighestBlockYAt(centerX, centerZ) - 1;

        createTreasureRoom(world, centerX, centerY, centerZ);

        world.getBlockAt(centerX, centerY - 1, centerZ).setType(Material.CHEST);

        BlockState state = world.getBlockAt(centerX, centerY - 1, centerZ).getState();
        if (state instanceof Chest) {
            Chest chest = (Chest) state;
            Inventory chestInventory = chest.getInventory();

            ItemStack enchantedSword = new ItemStack(Material.DIAMOND_SWORD);
            enchantedSword.addEnchantment(Enchantment.DAMAGE_ALL, 5);
            enchantedSword.addEnchantment(Enchantment.LOOT_BONUS_MOBS, 3);
            enchantedSword.addEnchantment(Enchantment.MENDING, 1);
            ItemStack enchantedBow = new ItemStack(Material.BOW);
            enchantedBow.addEnchantment(Enchantment.ARROW_DAMAGE, 3);
            enchantedBow.addEnchantment(Enchantment.ARROW_FIRE, 1);
            enchantedBow.addEnchantment(Enchantment.ARROW_INFINITE, 1);
            enchantedBow.addEnchantment(Enchantment.MENDING, 1);
            ItemStack diamondArmor = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemStack diamonds = new ItemStack(Material.DIAMOND, 5);
            ItemStack emeralds = new ItemStack(Material.EMERALD, 3);
            ItemStack ironIngots = new ItemStack(Material.IRON_INGOT, 10);

            chestInventory.addItem(enchantedSword, enchantedBow, diamondArmor, diamonds, emeralds, ironIngots);
            //TODO: Better Loot
        }
    }


    private void createTreasureRoom(World world, int centerX, int centerY, int centerZ) {
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                for (int y = centerY - 2; y <= centerY; y++) {
                    Material blockType;
                    switch ((int) (Math.random() * 5)) {
                        case 0:
                            blockType = Material.DIAMOND_ORE;
                            break;
                        case 1:
                            blockType = Material.EMERALD_ORE;
                            break;
                        case 2:
                            blockType = Material.STONE_BRICKS;
                            break;
                        case 3:
                            blockType = Material.IRON_ORE;
                            break;
                        case 4:
                            blockType = Material.COAL_ORE;
                            break;
                        default:
                            blockType = Material.STONE;
                            break;
                    }
                    world.getBlockAt(x, y, z).setType(blockType);
                }
            }
            //TODO: Better Room generation
        }
    }

    private void reset() {
        winner = null;
        playersInHunt.clear();
        treasureLocations.clear();
    }

}

