package de.groupxyz.cscraft2;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndRoundGame {
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final GameConfig config;
    private final WeaponSelect weaponSelect;
    private final TeamManager teamManager;
    private final BombManager bombManager;
    private final PlayerStats playerStats;
    private final RoundEconomyManager economyManager;

    private int currentRound = 0;
    private int ctScore = 0;
    private int tScore = 0;
    private int maxRounds = 30;
    private int halfTime = 15;
    private boolean swapTeamsAtHalf = true;
    private boolean roundBasedMode = true;
    public boolean forceStopped = false;

    public EndRoundGame(JavaPlugin plugin, GameManager gameManager, GameConfig config,
                        WeaponSelect weaponSelect, TeamManager teamManager, BombManager bombManager,
                        PlayerStats playerStats, RoundEconomyManager economyManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.config = config;
        this.weaponSelect = weaponSelect;
        this.teamManager = teamManager;
        this.bombManager = bombManager;
        this.playerStats = playerStats;
        this.economyManager = economyManager;

        this.maxRounds = config.getMaxRounds();
        this.halfTime = maxRounds / 2;
        this.roundBasedMode = config.isRoundBased();
    }

    public void endRound(String winningTeam, String reason) {
        if (GameStates.CURRENT_STATE != GameStates.PLAYING) {
            return;
        }

        GameStates.CURRENT_STATE = GameStates.ENDING;
        currentRound++;

        if (winningTeam.equals("CT")) {
            ctScore++;
        } else {
            tScore++;
        }

        List<Player> winners = winningTeam.equals("CT") ?
                gameManager.getDeathListener().getCTAll() :
                gameManager.getDeathListener().getTAll();

        List<Player> losers = winningTeam.equals("CT") ?
                gameManager.getDeathListener().getTAll() :
                gameManager.getDeathListener().getCTAll();

        economyManager.distributeRoundEndMoney(winners, losers);

        gameManager.getStatisticsListener().updateWinStats(winners);
        gameManager.getStatisticsListener().updateRoundStats();

        double healthLeft = 0.0;
        DecimalFormat df = new DecimalFormat("#,##0.0");

        for (Player p : winners) {
            healthLeft = healthLeft + p.getHealth();
        }

        String teamColor = winningTeam.equals("CT") ? ChatColor.BLUE.toString() : ChatColor.RED.toString();
        Bukkit.broadcastMessage(teamColor + "Team " + winningTeam + ChatColor.YELLOW +
                " hat Runde " + currentRound + " mit " +  df.format(healthLeft) + "HP übrig gewonnen! (" + reason + ")");

        Bukkit.broadcastMessage(ChatColor.GOLD + "Punktestand: " +
                ChatColor.BLUE + "CT " + ctScore + ChatColor.GOLD + " : " +
                ChatColor.RED + tScore + " T");

        int neededToWin = (maxRounds / 2) + 1;
        boolean gameOver = ctScore >= neededToWin || tScore >= neededToWin;

        boolean isHalfTime = currentRound == halfTime && swapTeamsAtHalf;

        if (gameOver) {
            String finalWinner = ctScore > tScore ? "CT" : "T";
            endFullGame(finalWinner, "Spielende nach " + currentRound + " Runden (" + ctScore + ":" + tScore + ")");
        } else {
            if (isHalfTime) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "HALBZEIT! Die Teams werden getauscht.");
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isHalfTime) {
                        swapTeams();
                    }
                    prepareNextRound();
                }
            }.runTaskLater(plugin, 200L);
        }
    }

    private void swapTeams() {
        List<Player> oldCTPlayers = gameManager.getDeathListener().getCTAll();
        List<Player> oldTPlayers = gameManager.getDeathListener().getTAll();

        Map<Player, Integer> playerMoneySnapshot = new HashMap<>();
        for (Player player : oldCTPlayers) {
            playerMoneySnapshot.put(player, economyManager.getPlayerMoney(player));
            player.getInventory().clear();
        }
        for (Player player : oldTPlayers) {
            playerMoneySnapshot.put(player, economyManager.getPlayerMoney(player));
            player.getInventory().clear();
        }

        teamManager.resetTeams();
        teamManager.assignTeams(oldTPlayers, oldCTPlayers);

        for (Player player : oldCTPlayers) {
            if (player.isOnline()) {
                economyManager.setPlayerMoney(player, playerMoneySnapshot.getOrDefault(player, 0));
            }
        }

        for (Player player : oldTPlayers) {
            if (player.isOnline()) {
                economyManager.setPlayerMoney(player, playerMoneySnapshot.getOrDefault(player, 0));
            }
        }

        int tempScore = ctScore;
        ctScore = tScore;
        tScore = tempScore;

        Bukkit.broadcastMessage(ChatColor.GOLD + "Teams wurden getauscht!");
    }

    private void prepareNextRound() {
        List<Player> alivePlayers = new ArrayList<>();
        alivePlayers.addAll(gameManager.getDeathListener().getCTAlive());
        alivePlayers.addAll(gameManager.getDeathListener().getTAlive());

        for (Player player : gameManager.getPlayers()) {
            if (!alivePlayers.contains(player)) {
                player.getInventory().clear();
            }
            player.setHealth(20);
            player.setFoodLevel(20);
            player.setGameMode(GameMode.SURVIVAL);
        }

        bombManager.reset();
        startNextRound();
    }

    private void startNextRound() {
        if (forceStopped) {
            forceStopped = false;
            return;
        }

        GameStates.CURRENT_STATE = GameStates.STARTING;

        List<Player> ctPlayers = gameManager.getDeathListener().getCTAll();
        List<Player> tPlayers = gameManager.getDeathListener().getTAll();

        Location ctSpawn = config.getCtSpawn();
        Location tSpawn = config.getTSpawn();

        teleportTeam(ctPlayers, ctSpawn);
        for (Player player : ctPlayers) {
            //player.teleport(ctSpawn);
            player.sendMessage(ChatColor.BLUE + "Du bist in Team [CT]!");
        }

        teleportTeam(tPlayers, tSpawn);
        for (Player player : tPlayers) {
            //player.teleport(tSpawn);
            player.sendMessage(ChatColor.RED + "Du bist in Team [T]!");
        }

        for (Player player : gameManager.getPlayers()) {
            player.setInvulnerable(true);
            player.sendMessage(ChatColor.YELLOW + "Runde " + (currentRound + 1) + " beginnt, wähle deine Waffen!");
        }

        for (Player player : ctPlayers) {
            int playerMoney = economyManager.getPlayerMoney(player);
            weaponSelect.setPlayerMoney(player.getName(), playerMoney);
            weaponSelect.startWeaponSelect(player, "CT", false);
        }

        for (Player player : tPlayers) {
            int playerMoney = economyManager.getPlayerMoney(player);
            weaponSelect.setPlayerMoney(player.getName(), playerMoney);
            weaponSelect.startWeaponSelect(player, "T", false);
        }

        int countdownTime = config.getCountdownTime();

        new BukkitRunnable() {
            int timeLeft = countdownTime;

            @Override
            public void run() {
                if (timeLeft > 0 && (timeLeft <= 5 || timeLeft % 5 == 0)) {
                    for (Player player : gameManager.getPlayers()) {
                        player.sendMessage(ChatColor.YELLOW + "Runde startet in " + timeLeft + " Sekunden!");
                        player.sendTitle("", ChatColor.YELLOW + "Runde startet in " + timeLeft + " Sekunden!", 0, 20, 10);
                    }
                }

                if (timeLeft <= 0) {
                    startRound();
                    this.cancel();
                    return;
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startRound() {
        GameStates.CURRENT_STATE = GameStates.PLAYING;

        for (Player player : gameManager.getPlayers()) {
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

            player.sendMessage(ChatColor.GREEN + "Runde " + (currentRound + 1) + " hat begonnen!");
        }

        bombManager.giveBombToRandomT(gameManager.getDeathListener().getTAll());

        gameManager.startBombPlacementTimer();
    }

    private void endFullGame(String winningTeam, String reason) {
        GameStates.CURRENT_STATE = GameStates.ENDING;
        String teamColor = winningTeam.equals("CT") ? ChatColor.BLUE.toString() : ChatColor.RED.toString();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gameManager.getPlayers().contains(player)) {
                player.sendTitle(
                        teamColor + "Team " + winningTeam + " hat gewonnen!",
                        ChatColor.YELLOW + "Endstand: CT " + ctScore + " : " + tScore + " T",
                        10, 70, 20
                );
            }
        }

        playerStats.registerWin(winningTeam);
        economyManager.clear();
        gameManager.stopGame(reason);
        resetRoundSystem();
    }

    public void resetRoundSystem() {
        currentRound = 0;
        ctScore = 0;
        tScore = 0;
    }

    public boolean isRoundBasedMode() {
        return roundBasedMode;
    }

    public void setRoundBasedMode(boolean roundBasedMode) {
        this.roundBasedMode = roundBasedMode;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
        this.halfTime = maxRounds / 2;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getCTScore() {
        return ctScore;
    }

    public int getTScore() {
        return tScore;
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
}