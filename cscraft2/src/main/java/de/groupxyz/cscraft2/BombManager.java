package de.groupxyz.cscraft2;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BombManager {
    private final Cscraft2 plugin;
    private final GameManager gameManager;

    private ItemStack bombItem;
    private Player bombCarrier;
    public Block plantedBombBlock;
    private Location bombLocation;
    private BukkitTask explosionTimer;
    private BukkitTask defuseTimer;
    public Player defusingPlayer;
    private BukkitTask plantTimer;
    public Player plantingPlayer;
    private Block plantBlock;

    private boolean isPlanted = false;
    private boolean isDefused = false;
    private boolean hasExploded = false;

    private final int EXPLOSION_TIME = 60;
    private final int DEFUSE_TIME = 7;
    private final int PLANT_TIME = 3;

    public BombManager(Cscraft2 plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        createBombItem();
    }

    private void createBombItem() {
        bombItem = new ItemStack(Material.TNT);
        ItemMeta meta = bombItem.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "C4 Sprengstoff");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rechtsklick zum Platzieren");
        lore.add(ChatColor.GRAY + "Benötigt " + EXPLOSION_TIME + " Sekunden zum Explodieren");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bombItem.setItemMeta(meta);
    }

    public void giveBombToRandomT(List<Player> tPlayers) {
        if (tPlayers == null || tPlayers.isEmpty()) {
            return;
        }

        Random random = new Random();
        bombCarrier = tPlayers.get(random.nextInt(tPlayers.size()));

        bombCarrier.getInventory().addItem(bombItem.clone());
        bombCarrier.sendMessage(ChatColor.RED + "Du trägst die Bombe! Platziere sie am Bombenziel.");

        for (Player tPlayer : tPlayers) {
            if (tPlayer != bombCarrier) {
                tPlayer.sendMessage(ChatColor.RED + bombCarrier.getName() + " trägt die Bombe.");
            }
        }
    }

    public boolean plantBomb(Player player, Block block) {
        if (!isPlanted && player.equals(bombCarrier)) {
            isPlanted = true;
            plantedBombBlock = block;
            bombLocation = plantedBombBlock.getLocation();

            player.getInventory().remove(bombItem);

            block.setType(Material.TNT);

            startExplosionTimer();

            gameManager.getEconomyManager().addMoneyForBombAction(player, "plant");
            gameManager.getStatisticsListener().updateBombStats(player,"plant");

            for (Player gamePlayer : gameManager.getPlayers()) {
                gamePlayer.sendTitle(
                        ChatColor.RED + "BOMBE PLATZIERT!",
                        ChatColor.YELLOW + "Die Bombe explodiert in " + EXPLOSION_TIME + " Sekunden",
                        10, 40, 10
                );
            }

            return true;
        }
        return false;
    }

    public boolean startDefuse(Player player) {
        if (isPlanted && !isDefused && !hasExploded && defusingPlayer == null) {
            defusingPlayer = player;
            player.sendMessage(ChatColor.BLUE + "Du beginnst, die Bombe zu entschärfen...");

            defuseTimer = new BukkitRunnable() {
                int timeLeft = DEFUSE_TIME;

                @Override
                public void run() {
                    if (defusingPlayer == null) {
                        this.cancel();
                        return;
                    }

                    if (defusingPlayer.isDead() || !defusingPlayer.isOnline()) {
                        cancelDefuse("Du bist gestorben!");
                        this.cancel();
                        return;
                    }

                    if (timeLeft <= 0) {
                        defuseBomb();
                        this.cancel();
                    } else {
                        for ( Player gamePlayer : gameManager.getPlayers()) {
                            gamePlayer.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                        }

                        defusingPlayer.sendTitle(ChatColor.BLUE + "Entschärfe: " +
                                ChatColor.YELLOW + "■".repeat(timeLeft) +
                                ChatColor.GRAY + "■".repeat(DEFUSE_TIME - timeLeft), "", 0, 20, 10);
                        timeLeft--;
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);

            return true;
        }
        return false;
    }

    public void cancelDefuse(String reason) {
        if (defuseTimer != null) {
            defuseTimer.cancel();
            defuseTimer = null;
        }

        if (defusingPlayer != null) {
            defusingPlayer.sendMessage(ChatColor.RED + "Entschärfung abgebrochen! " + reason);
            defusingPlayer = null;
        }
    }

    private void defuseBomb() {
        if (!isDefused && defusingPlayer != null) {
            isDefused = true;

            if (explosionTimer != null) {
                explosionTimer.cancel();
                explosionTimer = null;
            }

            if (plantedBombBlock != null) {
                plantedBombBlock.setType(Material.AIR);
            }

            gameManager.getEconomyManager().addMoneyForBombAction(defusingPlayer, "defuse");
            gameManager.getStatisticsListener().updateBombStats(defusingPlayer,"defuse");
            gameManager.getStatisticsListener().updateWinStats(gameManager.getDeathListener().getCTAll());

            for (Player player : gameManager.getPlayers()) {
                player.sendTitle(
                        ChatColor.BLUE + "BOMBE ENTSCHÄRFT!",
                        ChatColor.AQUA + "Entschärft durch " + defusingPlayer.getName(),
                        10, 40, 10
                );
            }

            gameManager.endGameWithResult("CT", "Bombe wurde entschärft");
        }
    }

    public boolean startPlant(Player player, Block block) {
        if (!isPlanted && player.equals(bombCarrier) && plantingPlayer == null) {
            plantingPlayer = player;
            plantBlock = block;
            player.sendMessage(ChatColor.RED + "Du beginnst, die Bombe zu platzieren...");

            plantTimer = new BukkitRunnable() {
                int timeLeft = PLANT_TIME;

                @Override
                public void run() {
                    if (plantingPlayer == null) {
                        this.cancel();
                        return;
                    }

                    if (plantingPlayer.isDead() || !plantingPlayer.isOnline()) {
                        cancelPlant("Du bist gestorben!");
                        this.cancel();
                        return;
                    }

                    if (timeLeft <= 0) {
                        completePlant();
                        this.cancel();
                    } else {
                        plantingPlayer.sendTitle(
                                ChatColor.RED + "Platzieren: " +
                                        ChatColor.YELLOW + "■".repeat(timeLeft) +
                                        ChatColor.GRAY + "■".repeat(PLANT_TIME - timeLeft),
                                "", 0, 20, 10
                        );

                        for (Player gamePlayer : gameManager.getPlayers()) {
                            if (gamePlayer.getLocation().distance(plantBlock.getLocation()) < 20) {
                                gamePlayer.playSound(gamePlayer.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 1.0f);
                            }
                        }

                        timeLeft--;
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);

            return true;
        }
        return false;
    }

    public void cancelPlant(String reason) {
        if (plantTimer != null) {
            plantTimer.cancel();
            plantTimer = null;
        }

        if (plantingPlayer != null) {
            plantingPlayer.sendMessage(ChatColor.RED + "Platzierung abgebrochen! " + reason);
            plantingPlayer = null;
        }
        plantBlock = null;
    }

    private void completePlant() {
        if (plantingPlayer != null && plantBlock != null) {
            plantBomb(plantingPlayer, plantBlock);
            gameManager.stopBombPlacementTimer();
            plantingPlayer = null;
            plantBlock = null;
        }
    }

    private void startExplosionTimer() {
        explosionTimer = new BukkitRunnable() {
            int timeLeft = EXPLOSION_TIME;

            @Override
            public void run() {
                if (isDefused) {
                    this.cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    explodeBomb();
                    this.cancel();
                } else {
                    if (timeLeft <= 10 || timeLeft % 5 == 0) {
                        for (Player player : gameManager.getPlayers()) {
                            player.sendTitle(ChatColor.RED + "Bombe: " + timeLeft + "s", "", 0, 20, 10);

                            if (plantedBombBlock != null && player.getLocation().distance(plantedBombBlock.getLocation()) < 20) {
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                            }
                        }
                    }
                    timeLeft--;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void explodeBomb() {
        hasExploded = true;

        if (plantedBombBlock != null) {
            plantedBombBlock.getWorld().createExplosion(
                    plantedBombBlock.getLocation(),
                    0.0f,
                    false,
                    false
            );

            plantedBombBlock.getWorld().spawnParticle(
                    org.bukkit.Particle.EXPLOSION_HUGE,
                    plantedBombBlock.getLocation(),
                    5, 2, 2, 2
            );

            plantedBombBlock.setType(Material.AIR);
        }

        gameManager.getStatisticsListener().updateWinStats(gameManager.getDeathListener().getTAll());

        for (Player player : gameManager.getPlayers()) {
            player.sendTitle(
                    ChatColor.RED + "BOMBE EXPLODIERT!",
                    ChatColor.YELLOW + "Die Terroristen gewinnen!",
                    10, 60, 20
            );
        }

        gameManager.endGameWithResult("T", "Bombe ist explodiert");
    }

    public void reset() {
        if (explosionTimer != null) {
            explosionTimer.cancel();
            explosionTimer = null;
        }

        if (defuseTimer != null) {
            defuseTimer.cancel();
            defuseTimer = null;
        }

        if (plantTimer != null) {
            plantTimer.cancel();
            plantTimer = null;
        }

        if (plantedBombBlock != null) {
            plantedBombBlock.setType(gameManager.bombPlantMaterial);
            plantedBombBlock = null;
        }

        if (bombLocation != null) {
            bombLocation.getBlock().setType(gameManager.bombPlantMaterial);
        }

        if (bombCarrier != null) {
            if (bombCarrier.getInventory().contains(bombItem)) {
                bombCarrier.getInventory().remove(bombItem);
            }
        }

        bombCarrier = null;
        defusingPlayer = null;
        plantingPlayer = null;
        plantBlock = null;
        isPlanted = false;
        isDefused = false;
        hasExploded = false;
    }

    public Player getPlantingPlayer() {
        return plantingPlayer;
    }

    public boolean isPlanted() {
        return isPlanted;
    }

    public boolean isDefused() {
        return isDefused;
    }

    public boolean hasExploded() {
        return hasExploded;
    }

    public ItemStack getBombItem() {
        return bombItem;
    }

    public Player getBombCarrier() {
        return bombCarrier;
    }
}