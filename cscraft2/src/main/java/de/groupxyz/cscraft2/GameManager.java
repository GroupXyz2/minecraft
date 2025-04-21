package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.*;

public class GameManager {
    private final List<Player> players = new ArrayList<>();
    private final JavaPlugin plugin;
    private final StartGame startGame;
    private final EndGame endGame;
    private final GameConfig gameConfig;
    private final WeaponSelect weaponSelect;
    private final DeathListener deathListener;
    private final TeamManager teamManager;
    private final BombManager bombManager;
    private final FriendlyFire friendlyFire;
    private final StatisticsListener statisticsListener;
    private final PlayerStats playerStats;
    private final EndRoundGame endRoundGame;
    private final RoundEconomyManager economyManager;
    public Material bombPlantMaterial = Material.RED_CONCRETE;
    private BukkitTask bombPlacementTimer;
    private BukkitTask placeBombReminder;

    public GameManager(JavaPlugin plugin, GameConfig gameConfig, WeaponSelect weaponSelect, TeamManager teamManager) {
        this.plugin = plugin;
        this.gameConfig = gameConfig;
        this.teamManager = teamManager;
        this.startGame = new StartGame(plugin, this, gameConfig, weaponSelect, teamManager);
        this.deathListener = new DeathListener(this);
        this.bombManager = new BombManager((Cscraft2)plugin, this);
        this.weaponSelect = weaponSelect;
        this.friendlyFire = new FriendlyFire((Cscraft2) plugin, teamManager);
        this.playerStats = new PlayerStats((Cscraft2) plugin);
        this.statisticsListener = new StatisticsListener(this, playerStats);
        this.economyManager = new RoundEconomyManager(gameConfig);
        this.endRoundGame = new EndRoundGame(plugin, this, gameConfig, weaponSelect, teamManager, bombManager, playerStats, economyManager);
        this.endGame = new EndGame(plugin, this, teamManager, endRoundGame);
        plugin.getServer().getPluginManager().registerEvents(deathListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(new BombListener(this, bombManager), plugin);

        Cscraft2.getInstance().getCommand("csround").setExecutor(
                new RoundCommandHandler(gameConfig, endRoundGame));
    }

    public DeathListener getDeathListener() {
        return deathListener;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public EndRoundGame getEndRoundGame() {
        return endRoundGame;
    }

    public RoundEconomyManager getEconomyManager() {
        return economyManager;
    }

    public FriendlyFire getFriendlyFire() {return friendlyFire;}

    public PlayerStats getPlayerStats() {return playerStats;}

    public StatisticsListener getStatisticsListener() {return statisticsListener;}

    public void joinGame(Player player) {
        if (players.contains(player)) {
            player.sendMessage("You are already in the game.");
            return;
        }
        players.add(player);
        Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + " has joined the game.");
    }

    public void leaveGame(Player player) {
        if (!players.contains(player)) {
            player.sendMessage("You are not in the game.");
            return;
        }

        if (GameStates.CURRENT_STATE == GameStates.STARTING) {
            player.sendMessage("You cannot leave the game while it is starting.");
            return;
        }
        if (GameStates.CURRENT_STATE == GameStates.PLAYING) {
            player.sendMessage("You cannot leave the game while it is in progress.");
            return;
        }

        players.remove(player);
        Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + " has left the game.");
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void startGame(Boolean forced) {
        startGame.initiateGameStart(forced);
    }

    public void stopGame(String reason) {
        endGame.endGame(reason);
    }

    public void endGameWithResult(String winningTeam, String reason) {
        if (endRoundGame.isRoundBasedMode()) {
            endRoundGame.endRound(winningTeam, reason);
        } else {
            String teamColor = winningTeam.equals("CT") ? ChatColor.BLUE.toString() : ChatColor.RED.toString();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (this.getPlayers().contains(player)) {
                    player.sendTitle(
                            teamColor + "Team " + winningTeam + " has won!",
                            ChatColor.YELLOW + "The game ended!",
                            10, 70, 20
                    );
                    player.sendMessage(teamColor + "Team " + winningTeam + ChatColor.GREEN + " has won: " + ChatColor.GOLD+ reason);
                }
            }

            stopGame("Game ended");
        }
    }

    public void checkTeamElimination() {
        if (GameStates.CURRENT_STATE != GameStates.PLAYING || !endRoundGame.isRoundBasedMode()) {
            return;
        }

        boolean ctAlive = !deathListener.getCTAlive().isEmpty();
        boolean tAlive = !deathListener.getTAlive().isEmpty();
        boolean bombPlanted = bombManager.isPlanted();

        if (!ctAlive && tAlive) {
            double healthLeft = 0.0;
            DecimalFormat df = new DecimalFormat("#,##0.0");

            for (Player p : deathListener.getTAlive()) {
                healthLeft += p.getHealth();
            }

            endRoundGame.endRound("T", "Team CT eliminiert, " + df.format(healthLeft) + " HP übrig");
        }

        else if (!tAlive && ctAlive && !bombPlanted) {
            double healthLeft = 0.0;
            DecimalFormat df = new DecimalFormat("#,##0.0");

            for (Player p : deathListener.getCTAlive()) {
                healthLeft += p.getHealth();
            }

            endRoundGame.endRound("CT", "Alle Terroristen eliminiert, " + df.format(healthLeft) + " HP übrig");
        }

        else if (!ctAlive && !tAlive) {
            endRoundGame.endRound("CT", "Unentschieden - CT gewinnt");
        }
    }

    public void openWeaponShop(Player player) {
        if (GameStates.CURRENT_STATE != GameStates.STARTING) {
            player.sendMessage(ChatColor.RED + "You can only open the shop while starting!");
            return;
        }
        if (teamManager.getPlayerTeam(player) == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return;
        }
        String playerteam = teamManager.getPlayerTeam(player);
        weaponSelect.openWeaponMenu(player, playerteam);
    }

    public BombManager getBombManager() {
        return bombManager;
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().isOp()) {
            event.getPlayer().getInventory().clear();
            if (GameStates.CURRENT_STATE == GameStates.PLAYING) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "You joined the game while it is in progress, you are now spectator.");
                event.getPlayer().setGameMode(GameMode.SPECTATOR);
            } else if (GameStates.CURRENT_STATE == GameStates.STARTING || GameStates.CURRENT_STATE == GameStates.ENDING) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "You joined the game while it is starting, you are now spectator.");
                event.getPlayer().setGameMode(GameMode.SPECTATOR);
            } else {
                event.getPlayer().setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (GameStates.CURRENT_STATE == GameStates.PLAYING) {
            if (deathListener.getCTAlive().contains(event.getPlayer())) {
                deathListener.getCTAlive().remove(event.getPlayer());
            } else if (deathListener.getTAlive().contains(event.getPlayer())) {
                deathListener.getTAlive().remove(event.getPlayer());
            }
            if (endRoundGame.isRoundBasedMode()) {
                checkTeamElimination();
            } else {
                deathListener.checkWinCondition();
            }
        }
    }

    public void startBombPlacementTimer() {
        if (bombPlacementTimer != null) {
            bombPlacementTimer.cancel();
            bombPlacementTimer = null;
        }
        if (placeBombReminder != null) {
            placeBombReminder.cancel();
            placeBombReminder = null;
        }

        int BOMB_PLACEMENT_TIME = gameConfig.getBombPlacementTime();

        if (BOMB_PLACEMENT_TIME > 30) {
            placeBombReminder = new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getLogger().info("Bombenplatzierungs-Reminder ausgelöst");
                    if (GameStates.CURRENT_STATE == GameStates.PLAYING && !getBombManager().isPlanted()) {
                        for (Player tPlayer : getDeathListener().getTAlive()) {
                            tPlayer.sendMessage(ChatColor.YELLOW + "Die Bombe muss in 30 Sekunden platziert sein!");
                        }
                    }
                }
            }.runTaskLater(plugin, (BOMB_PLACEMENT_TIME - 30) * 20L);
        }

        bombPlacementTimer = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Bombenplatzierungs-Timer abgelaufen");
                if (GameStates.CURRENT_STATE == GameStates.PLAYING && !getBombManager().isPlanted()) {
                    for (Player player : getPlayers()) {
                        player.sendTitle(ChatColor.BLUE + "Zeit abgelaufen!",
                                ChatColor.YELLOW + "Bombe nicht platziert, CT gewinnt!", 10, 70, 20);
                    }

                    getStatisticsListener().updateWinStats(getDeathListener().getCTAll());
                    endGameWithResult("CT", "Zeitlimit für Bombenplatzierung abgelaufen");
                }
            }
        }.runTaskLater(plugin, BOMB_PLACEMENT_TIME * 20L);
    }

    public void stopBombPlacementTimer() {
        if (bombPlacementTimer != null) {
            bombPlacementTimer.cancel();
            bombPlacementTimer = null;
        }
        if (placeBombReminder != null) {
            placeBombReminder.cancel();
            placeBombReminder = null;
        }
    }
}

