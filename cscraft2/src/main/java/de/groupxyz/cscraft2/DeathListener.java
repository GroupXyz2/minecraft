package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {
    private final GameManager gameManager;
    private final List<Player> ctAlive = new ArrayList<>();
    private final List<Player> tAlive = new ArrayList<>();
    private final List<Player> ctAll = new ArrayList<>();
    private final List<Player> tAll = new ArrayList<>();

    public DeathListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void initializeTeams(List<Player> ctTeam, List<Player> tTeam) {
        ctAlive.clear();
        tAlive.clear();
        ctAlive.addAll(ctTeam);
        tAlive.addAll(tTeam);
        ctAll.clear();
        tAll.clear();
        ctAll.addAll(ctTeam);
        tAll.addAll(tTeam);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!gameManager.getPlayers().contains(player) || GameStates.CURRENT_STATE != GameStates.PLAYING) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isDead()) {
                    player.spigot().respawn();
                }

                if (GameStates.CURRENT_STATE == GameStates.WAITING || GameStates.CURRENT_STATE == GameStates.ENDING) {
                    return;
                }

                player.setGameMode(GameMode.SPECTATOR);
            }
        }.runTaskLater(Cscraft2.getInstance(), 2L);

        if (ctAlive.contains(player)) {
            ctAlive.remove(player);
            announcePlayerDeath(player, "CT");
            checkWinCondition();
        } else if (tAlive.contains(player)) {
            tAlive.remove(player);
            announcePlayerDeath(player, "T");
            checkWinCondition();
        }

        if (GameStates.CURRENT_STATE == GameStates.PLAYING ||
                (gameManager.getEndRoundGame().isRoundBasedMode() &&
                        (GameStates.CURRENT_STATE == GameStates.ENDING ||
                                GameStates.CURRENT_STATE == GameStates.STARTING))) {
            announcePlayerDeath(player, gameManager.getTeamManager().getPlayerTeam(player));
        }

        gameManager.checkTeamElimination();
    }

    private void announcePlayerDeath(Player player, String team) {
        String teamColor = team.equals("CT") ? ChatColor.BLUE.toString() : ChatColor.RED.toString();
        String message = teamColor + team + " " + ChatColor.YELLOW + "Player " +
                ChatColor.WHITE + player.getName() + ChatColor.YELLOW + " died!";

        Bukkit.broadcastMessage(message);
    }

    public void checkWinCondition() {
        if (gameManager.getBombManager().isPlanted() && (!ctAlive.isEmpty())) {
            return;
        }

        if (ctAlive.isEmpty()) {
            double healthLeft = 0.0;
            DecimalFormat df = new DecimalFormat("#,##0.0");

            gameManager.getStatisticsListener().updateWinStats(tAll);

            for (Player p : tAlive) {
                healthLeft = healthLeft + p.getHealth();
            }
            for (Player player : gameManager.getPlayers()) {
                player.sendTitle("", ChatColor.RED + "Terroristen gewinnen!", 10, 70, 20);
            }
            gameManager.endGameWithResult("T", "Alle Counter-Terroristen eliminiert, " + df.format(healthLeft) + " HP übrig");
        } else if (tAlive.isEmpty()) {
            double healthLeft = 0.0;
            DecimalFormat df = new DecimalFormat("#,##0.0");

            gameManager.getStatisticsListener().updateWinStats(ctAll);

            for (Player p : ctAlive) {
                healthLeft = healthLeft + p.getHealth();
            }
            for (Player player : gameManager.getPlayers()) {
                player.sendTitle("", ChatColor.BLUE + "Counter-Terroristen gewinnen!", 10, 70, 20);
            }
            gameManager.endGameWithResult("CT", "Alle Terroristen eliminiert, " + df.format(healthLeft) + " HP übrig");
        }
    }

    //TO BE REMOVED
    private void endGame(String winningTeam) {
        GameStates.CURRENT_STATE = GameStates.ENDING;
        String teamColor = winningTeam.equals("CT") ? ChatColor.BLUE.toString() : ChatColor.RED.toString();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gameManager.getPlayers().contains(player)) {
                player.sendTitle(
                        teamColor + "Team " + winningTeam + " has won!",
                        ChatColor.YELLOW + "The game ended!",
                        10, 70, 20
                );
                player.sendMessage(teamColor + "Team " + winningTeam + ChatColor.GREEN + " has won!");
            }
        }

        gameManager.stopGame("Game ended");
    }

    public List<Player> getCTAlive() {
        return ctAlive;
    }

    public List<Player> getTAlive() {
        return tAlive;
    }

    public List<Player> getCTAll() {
        return ctAll;
    }

    public List<Player> getTAll() {
        return tAll;
    }
}