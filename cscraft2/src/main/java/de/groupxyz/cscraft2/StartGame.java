package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StartGame {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final GameConfig config;
    private final WeaponSelect weaponSelect;
    private final TeamManager teamManager;
    private boolean isCountingDown = false;

    public StartGame(JavaPlugin plugin, GameManager gameManager, GameConfig config, WeaponSelect weaponSelect, TeamManager teamManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.config = config;
        this.weaponSelect = weaponSelect;
        this.teamManager = teamManager;
    }

    public void initiateGameStart(Boolean forced) {
        List<Player> players = gameManager.getPlayers();

        if (players.isEmpty()) {
            plugin.getLogger().info("Game start canceled: No players in the game.");
            Bukkit.broadcastMessage(ChatColor.RED + "Game start canceled: No players in the game.");
            return;
        }

        if (!forced) {
            if (players.size() < config.getMinPlayers()) {
                for (Player player : players) {
                    player.sendMessage(ChatColor.RED + "Game canceled, not enough players! (Min: "
                            + config.getMinPlayers() + ")");
                    player.sendMessage(ChatColor.RED + "Not enough players! (Min: "
                            + config.getMinPlayers() + ")");
                }
                return;
            }

            if (players.size() > config.getMaxPlayers()) {
                Bukkit.getLogger().info(ChatColor.RED + "Game canceled, too much players! (Max: "
                        + config.getMaxPlayers() + ")");
                for (Player player : players) {
                    player.sendMessage(ChatColor.RED + "Too much players! (Max: " + config.getMaxPlayers() + ")");
                }
                return;
            }
        }

        if (config.getCtSpawn() == null || config.getTSpawn() == null) {
            for (Player player : players) {
                player.sendMessage(ChatColor.RED + "Spawn positions not configured!");
            }
            return;
        }

        if (!isCountingDown) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Spiel startet!");
            startCountdown(forced);
        } else {
            for (Player player : players) {
                player.sendMessage(ChatColor.YELLOW + "Game already starting!");
            }
        }
    }

    private void startCountdown(Boolean forced) {
        isCountingDown = true;
        GameStates.CURRENT_STATE = GameStates.STARTING;
        final int countdownTime = config.getCountdownTime();

        List<Player> players = new ArrayList<>(gameManager.getPlayers());
        Collections.shuffle(players);

        int midPoint = players.size() / 2;
        List<Player> ctTeam = players.subList(0, midPoint);
        List<Player> tTeam = players.subList(midPoint, players.size());

        teamManager.assignTeams(ctTeam, tTeam);

        Location ctSpawn = config.getCtSpawn();
        Location tSpawn = config.getTSpawn();

        teleportTeam(ctTeam, ctSpawn);
        for (Player player : ctTeam) {
            //player.teleport(ctSpawn);
            player.sendMessage(ChatColor.BLUE + "You are in Team [CT]!");
        }

        teleportTeam(tTeam, tSpawn);
        for (Player player : tTeam) {
            //player.teleport(tSpawn);
            player.sendMessage(ChatColor.RED + "You are in Team [T]!");
        }

        for (Player player : gameManager.getPlayers()) {
            player.getInventory().clear();
            player.setHealth(20);
            player.setFoodLevel(20);
            player.setInvulnerable(true);
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.sendMessage(ChatColor.YELLOW + "Round starting, you have " + countdownTime + " seconds to select your weapon!");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gameManager.getPlayers().contains(player)) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
        }

        for (Player player : ctTeam) {
            weaponSelect.startWeaponSelect(player, "CT", true);
        }
        for (Player player : tTeam) {
            weaponSelect.startWeaponSelect(player, "T", true);
        }

        new BukkitRunnable() {
            int timeLeft = countdownTime;

            @Override
            public void run() {
                List<Player> players = gameManager.getPlayers();

                if (!forced) {
                    if (players.size() < config.getMinPlayers()) {
                        Bukkit.getLogger().info(ChatColor.RED + "Countdown canceled! Not enough players.");
                        for (Player player : players) {
                            player.sendMessage(ChatColor.RED + "Countdown canceled! Not enough players.");
                        }
                        isCountingDown = false;
                        cancel();
                        return;
                    }
                }

                if (timeLeft > 0 && (timeLeft <= 5 || timeLeft % 5 == 0)) {
                    for (Player player : players) {
                        player.sendMessage(ChatColor.YELLOW + "Game starting in " + timeLeft + " seconds!");
                        player.sendTitle("", ChatColor.YELLOW + "Game starting in " + timeLeft + " seconds!", 0, 20, 10);
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1, 1);
                    }
                }

                if (timeLeft <= 0) {
                    startGame(players);
                    isCountingDown = false;
                    cancel();
                    return;
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void teleportTeam(List<Player> team, Location baseLocation) {
        int radius = 1;
        for (Player player : team) {
            Location safeLocation = findSafeLocation(baseLocation, radius);
            player.teleport(safeLocation);
        }
    }

    private Location findSafeLocation(Location center, int radius) {
        int maxAttempts = 100;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = (int) (Math.random() * (radius * 2 + 1)) - radius;
            int z = (int) (Math.random() * (radius * 2 + 1)) - radius;

            if (x == 0 && z == 0) continue;

            Location loc = center.clone().add(x, 0, z);

            loc.setY(center.getY());

            if (isSafeToStand(loc)) {
                if (isSafeToStand(loc.add(0, 1, 0))) {
                    return loc.add(0, 1, 0).clone();
                } else {
                    return loc.clone();
                }
            }
        }

        return center.clone().add(0, 1, 0);
    }

    private boolean isSafeToStand(Location loc) {
        Block blockBelow = loc.getBlock();
        if (blockBelow.getType() == Material.AIR && loc.add(0, -1, 0).getBlock().getType().isSolid()) {
            return true;
        }

        if (!blockBelow.getType().isSolid()) {
            return false;
        }

        if (!(blockBelow.getBoundingBox().getWidthX() == 1.0 &&
                blockBelow.getBoundingBox().getHeight() == 1.0 &&
                blockBelow.getBoundingBox().getWidthZ() == 1.0)) {
            return false;
        }

        Block playerPos = loc.clone().add(0, 1, 0).getBlock();
        Block playerHead = loc.clone().add(0, 2, 0).getBlock();

        return playerPos.getType() == Material.AIR &&
                playerHead.getType() == Material.AIR;
    }

    private void startGame(List<Player> players) {
        GameStates.CURRENT_STATE = GameStates.PLAYING;

        List<Player> ctTeam = new ArrayList<>();
        List<Player> tTeam = new ArrayList<>();

        for (Player player : players) {
            if (player.getLocation().distance(config.getCtSpawn()) < 10) {
                ctTeam.add(player);
            } else {
                tTeam.add(player);
            }

            player.setInvulnerable(false);

            ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
            ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
            ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);

            helmet.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            chestplate.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            leggings.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            boots.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);

            player.getInventory().setHelmet(helmet);
            player.getInventory().setChestplate(chestplate);
            player.getInventory().setLeggings(leggings);
            player.getInventory().setBoots(boots);

            player.sendMessage(ChatColor.GREEN + "Spiel hat begonnen!");
        }

        gameManager.getDeathListener().initializeTeams(ctTeam, tTeam);

        gameManager.getBombManager().giveBombToRandomT(tTeam);

        gameManager.startBombPlacementTimer();
    }

    public GameConfig getConfig() {
        return config;
    }
}