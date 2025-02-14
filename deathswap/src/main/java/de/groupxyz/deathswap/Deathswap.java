package de.groupxyz.deathswap;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Deathswap extends JavaPlugin implements Listener {

    Player deathPlayer;
    Boolean isStarted = false;
    Boolean allowJoin = true;
    private final Random random = new Random();
    private BukkitRunnable swapTask;

    @Override
    public void onEnable() {
        getLogger().info("Deathswap by GroupXyz starting...");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Deathswap by GroupXyz shutting down!");
        allowJoin = true;
        deathPlayer = null;
        isStarted = false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("togglejoin")) {
                toggleStatus(player);
                return true;
            }
            return false;
        } else {
            sender.sendMessage("Only players can use this command!");
            return false;
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (allowJoin) {
            if (Bukkit.getOnlinePlayers().size() == 2 && swapTask == null) {
                getServer().broadcastMessage(ChatColor.GOLD + "Deathswap is starting, swapping every 5 minutes, try to kill each other with the swap!");
                isStarted = true;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                scheduleSwapTask();
            } else if (Bukkit.getOnlinePlayers().size() <= 1) {
                Player player = event.getPlayer();
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage(ChatColor.GOLD + "Welcome to Deathswap!");
                getServer().broadcastMessage(ChatColor.YELLOW + "Waiting for one more player!");
            } else if (Bukkit.getOnlinePlayers().size() > 2) {
                Player player = event.getPlayer();
                player.sendMessage(ChatColor.GOLD + "Welcome to Deathswap!");
            }
        } else {
            event.setJoinMessage(null);
            event.getPlayer().kickPlayer(ChatColor.RED + "Sorry, currently joining is disabled for this round!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isStarted) {
            if (Bukkit.getOnlinePlayers().size() <= 1) {
                Player lastPlayer = Bukkit.getOnlinePlayers().iterator().next();
                getServer().broadcastMessage(ChatColor.GOLD + "Not enough players left!");
                deathswapWin(lastPlayer);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        deathPlayer = event.getPlayer();
        deathPlayer.setGameMode(GameMode.SPECTATOR);
        getServer().broadcastMessage(ChatColor.YELLOW + deathPlayer.getName() + " is now spectating!");
        if (isStarted) {
            if (onlyOneSurvivalPlayerLeft()) {
                Player lastSurvivalPlayer = getSurvivalPlayer();
                deathswapWin(lastSurvivalPlayer);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;
            if (damageByEntityEvent.getDamager() instanceof Player && event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    public void toggleStatus(Player player) {
        if (!allowJoin) {
            allowJoin = true;
            player.sendMessage(ChatColor.YELLOW+ "Joining enabled!");
        } else if (allowJoin) {
            allowJoin = false;
            player.sendMessage(ChatColor.YELLOW + "Joining disabled!");
        }
    }

    private boolean onlyOneSurvivalPlayerLeft() {
        int survivalPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                survivalPlayers++;
                if (survivalPlayers > 1) {
                    return false;
                }
            }
        }
        return survivalPlayers == 1;
    }

    private Player getSurvivalPlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                return player;
            }
        }
        return null;
    }

    public void deathswapWin(Player player) {
        if (player != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + " has won, resetting in 10 seconds!");
            Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                World world = player.getWorld();
                File worldFolder = world.getWorldFolder();
                Bukkit.unloadWorld(world, false);
                deleteWorld(worldFolder);
                getLogger().info("The game has ended, shutting down!");
                Bukkit.shutdown();
            }, 200L);
        }
    }

    private void deleteWorld(File worldFolder) {
        if (worldFolder.exists()) {
            File[] files = worldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorld(file);
                    } else {
                        file.delete();
                    }
                }
            }
            worldFolder.delete();
        }
    }

    private void scheduleSwapTask() {
        swapTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                Collections.shuffle(players);
                World defaultWorld = Bukkit.getWorlds().get(0);

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 10 seconds!");
                }, (5 * 60 * 20L) - (10 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 5");
                }, (5 * 60 * 20L) - (5 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 4");
                }, (5 * 60 * 20L) - (4 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 3");
                }, (5 * 60 * 20L) - (3 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 2");
                }, (5 * 60 * 20L) - (2 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Swapping in 1");
                }, (5 * 60 * 20L) - (1 * 20L));

                Bukkit.getScheduler().runTaskLater(Deathswap.this, () -> {
                    List<Location> playerLocations = new ArrayList<>();
                    for (Player player : players) {
                        playerLocations.add(player.getLocation());
                    }
                    for (int i = 0; i < players.size(); i++) {
                        players.get(i).teleport(playerLocations.get((i + 1) % players.size()));
                    }
                    getServer().broadcastMessage(ChatColor.GOLD + "Players got swapped!");
                }, 5 * 60 * 20L);
            }
        };
        swapTask.runTaskTimer(this, 0, 5 * 60 * 20L);
    }
}
