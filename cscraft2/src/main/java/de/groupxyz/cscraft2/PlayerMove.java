package de.groupxyz.cscraft2;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMove implements Listener {
    private final GameManager gameManager;

    public PlayerMove(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameManager.getPlayers().contains(event.getPlayer()) &&
                GameStates.CURRENT_STATE == 1) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }

        if (GameStates.CURRENT_STATE == GameStates.WAITING) {
            if (event.getPlayer().getSaturation() < 20) {
                event.getPlayer().setSaturation(20);
            }
        }
    }
}