package de.groupxyz.groupsskywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private Scoreboard scoreboard;
    private Objective objective;
    private boolean gameRunning = false;
    private int timeInSeconds = 0;
    private BukkitRunnable timerTask;

    public void startGameTimer() {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("gameInfo", "dummy", ChatColor.BOLD + "Skywars");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        gameRunning = true;
        timeInSeconds = 0;

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) {
                    this.cancel();
                    return;
                }

                timeInSeconds++;
                updateScoreboard();
            }
        };
        timerTask.runTaskTimer(Bukkit.getPluginManager().getPlugin("groupsskywars"), 0L, 20L);
    }

    public void stopGame() {
        gameRunning = false;
        if (timerTask != null) {
            timerTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        playerKills.clear();
    }

    public void addKill(Player player) {
        if (!gameRunning) return;

        if (player == null) return;
        UUID playerId = player.getUniqueId();
        playerKills.put(playerId, playerKills.getOrDefault(playerId, 0) + 1);
        updateScoreboard();
    }

    private void updateScoreboard() {
        String formattedTime = String.format("%02d:%02d", timeInSeconds / 60, timeInSeconds % 60);

        scoreboard.getEntries().forEach(scoreboard::resetScores);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }

        objective.getScore(ChatColor.DARK_PURPLE + "Spielzeit:").setScore(15);
        objective.getScore(ChatColor.LIGHT_PURPLE + formattedTime).setScore(14);

        objective.getScore(ChatColor.AQUA + "Kills:").setScore(13);

        int scoreIndex = 12;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerName = ChatColor.AQUA + player.getName();
            int kills = playerKills.getOrDefault(player.getUniqueId(), 0);

            objective.getScore(playerName).setScore(scoreIndex);
            objective.getScore(ChatColor.AQUA + " " + kills + " Kills").setScore(scoreIndex - 1);

            scoreIndex -= 2;
            if (scoreIndex < 0) break;
        }
    }

}

