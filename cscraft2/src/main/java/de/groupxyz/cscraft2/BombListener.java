package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class BombListener implements Listener {
    private final GameManager gameManager;
    private final BombManager bombManager;

    public BombListener(GameManager gameManager, BombManager bombManager) {
        this.gameManager = gameManager;
        this.bombManager = bombManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (GameStates.CURRENT_STATE != GameStates.PLAYING) {
            if (!player.isOp()) {
                event.setCancelled(true);
            }
        }

        if (!gameManager.getPlayers().contains(player)) return;

        ItemStack item = event.getItem();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        if (item != null && item.getType() == Material.TNT &&
                (action == Action.RIGHT_CLICK_BLOCK) && clickedBlock != null) {

            if (player.equals(bombManager.getBombCarrier()) && !bombManager.isPlanted()) {
                event.setCancelled(true);

                if (isValidBombTarget(clickedBlock)) {
                    if (bombManager.startPlant(player, clickedBlock)) {
                        Bukkit.getLogger().info("Bomb planting started!");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Die Bombe kann hier nicht platziert werden!");
                }
            }
        }
        else if (action == Action.RIGHT_CLICK_BLOCK && clickedBlock != null &&
                clickedBlock.getType() == Material.TNT &&
                bombManager.isPlanted() &&
                isPlayerCT(player)) {

            event.setCancelled(true);
            bombManager.startDefuse(player);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (GameStates.CURRENT_STATE == GameStates.PLAYING) {
            event.setCancelled(true);
        }
        if (GameStates.CURRENT_STATE != GameStates.PLAYING && player.isOp()) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (GameStates.CURRENT_STATE == GameStates.PLAYING &&
                player.equals(bombManager.defusingPlayer)) {

            Location playerLoc = player.getLocation();
            Location bombLoc = bombManager.plantedBombBlock.getLocation();
            double xzDistance = Math.sqrt(
                    Math.pow(playerLoc.getX() - bombLoc.getX(), 2) +
                            Math.pow(playerLoc.getZ() - bombLoc.getZ(), 2)
            );

            if (xzDistance > 1.0) {
                bombManager.cancelDefuse("Du hast dich bewegt!");
            }
        }

        if (event.getPlayer().equals(bombManager.plantingPlayer) &&
                GameStates.CURRENT_STATE == GameStates.PLAYING) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                bombManager.cancelPlant("Du hast dich bewegt!");
            }
        }
    }

    private boolean isValidBombTarget(Block block) {
        return block.getType() == gameManager.bombPlantMaterial;
    }

    private boolean isPlayerCT(Player player) {
        return gameManager.getTeamManager().getPlayerTeam(player).equals("CT");
    }
}