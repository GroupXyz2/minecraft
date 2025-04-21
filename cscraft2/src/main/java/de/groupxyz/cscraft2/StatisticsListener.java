package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;

public class StatisticsListener implements Listener {
    private final GameManager gameManager;
    private final PlayerStats playerStats;

    public StatisticsListener(GameManager gameManager, PlayerStats playerStats) {
        this.gameManager = gameManager;
        this.playerStats = playerStats;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (GameStates.CURRENT_STATE != GameStates.PLAYING) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            playerStats.incrementStat(killer, "kills");

            gameManager.getEconomyManager().addMoneyForKill(killer);
        }

        playerStats.incrementStat(victim, "deaths");
        playerStats.saveStats();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (GameStates.CURRENT_STATE != GameStates.PLAYING) return;
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        double damage = event.getFinalDamage();

        playerStats.incrementStat(damager, "damage", (int) Math.round(damage));
    }

    public void updateBombStats(Player player, String action) {
        switch (action) {
            case "plant":
                playerStats.incrementStat(player, "bombsPlanted");
                break;
            case "defuse":
                playerStats.incrementStat(player, "bombsDefused");
                break;
        }
        playerStats.saveStats();
    }

    public void updateWinStats(List<Player> winners) {
        for (Player player : gameManager.getPlayers()) {
            if (player.isOnline()) {
                if (winners.contains(player)) {
                    playerStats.incrementStat(player, "wins");
                } else {
                    playerStats.incrementStat(player, "losses");
                }
            }
        }
        playerStats.saveStats();
    }

    public void updateRoundStats() {
        for (Player player : gameManager.getPlayers()) {
            playerStats.incrementStat(player, "roundsPlayed");
        }
        playerStats.saveStats();
    }
}