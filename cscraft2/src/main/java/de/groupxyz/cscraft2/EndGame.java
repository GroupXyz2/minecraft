package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class EndGame {
    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final EndRoundGame endRoundGame;

    public EndGame(JavaPlugin plugin, GameManager gameManager, TeamManager teamManager, EndRoundGame endRoundGame) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.teamManager = teamManager;
        this.endRoundGame = endRoundGame;
    }

    public void endGame(String reason) {
        if (GameStates.CURRENT_STATE == GameStates.WAITING) {
            return;
        }

        if (Objects.equals(reason, "Stopped by admin!")) {
            Bukkit.broadcastMessage(ChatColor.RED + "The Game ended: " + reason);
            endRoundGame.forceStopped = true;
        }

        removeAllDroppedItems();

        for (Player player : gameManager.getPlayers()) {
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setInvulnerable(false);
            player.setGameMode(GameMode.SURVIVAL);

            player.teleport(player.getWorld().getSpawnLocation());
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead()) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.teleport(player.getWorld().getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.setGameMode(GameMode.CREATIVE);
            }
        }

        gameManager.getBombManager().reset();

        GameStates.CURRENT_STATE = GameStates.WAITING;

        teamManager.resetTeams();
        gameManager.getPlayers().clear();
    }

    private void removeAllDroppedItems() {
        if (gameManager.getPlayers().isEmpty()) {
            return;
        }

        Player firstPlayer = gameManager.getPlayers().iterator().next();
        firstPlayer.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)
                .forEach(org.bukkit.entity.Entity::remove);
    }
}