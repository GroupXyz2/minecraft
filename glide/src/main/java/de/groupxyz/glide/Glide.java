package de.groupxyz.glide;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class Glide extends JavaPlugin {

    private double globalSpeed = 0.75;
    private double glideHeight = 100;
    private final Set<Player> glidingPlayers = new HashSet<>();
    private boolean avoidingObstacles = false;
    private boolean hasToLand = false;

    @Override
    public void onEnable() {
        this.getCommand("glide").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
                    stopGlide(player);
                } else if (args.length == 1 && args[0].matches("\\d*\\.?\\d+")) {
                    globalSpeed = Double.parseDouble(args[0]);
                    player.sendMessage(ChatColor.DARK_BLUE + "Global glide speed set to " + globalSpeed);
                } else if (args.length == 1 && args[0].matches("\\d*\\.?\\d+" + "m")) {
                    glideHeight = Double.parseDouble(args[0]);
                    player.sendMessage(ChatColor.DARK_BLUE + "Global glide height set to " + glideHeight);
                } else if (args.length == 3) {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    Location target = new Location(player.getWorld(), x, y + 150, z);
                    liftAndGlide(player, target);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /glide <x> <y> <z> or /glide stop or /glide <speed> or /glide <height m>");
                }
            }
            return true;
        });
    }

    private void liftAndGlide(Player player, Location target) {
        glidingPlayers.add(player);
        player.sendMessage(ChatColor.DARK_BLUE + "Gliding to " + target.getX() + " " + target.getY() + " " + target.getZ());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!glidingPlayers.contains(player)) {
                    this.cancel();
                    return;
                }
                Location startLocation = player.getLocation();
                Location liftLocation = startLocation.clone().add(0, 150, 0);
                liftPlayer(player, liftLocation, target);
            }
        }.runTask(this);
    }

    private void liftPlayer(Player player, Location liftLocation, Location target) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!glidingPlayers.contains(player)) {
                    this.cancel();
                    return;
                }
                Location currentLocation = player.getLocation();
                if (currentLocation.getY() >= liftLocation.getY()) {
                    this.cancel();
                    hasToLand = true;
                    glideToLocation(player, target);
                    return;
                }

                Vector direction = liftLocation.toVector().subtract(currentLocation.toVector()).normalize().multiply(globalSpeed*2);
                player.setVelocity(direction);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void glideToLocation(Player player, Location target) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!glidingPlayers.contains(player)) {
                    this.cancel();
                    return;
                }
                Location currentLocation = player.getLocation();
                if (currentLocation.distance(target) < 1) {
                    this.cancel();
                    stopGlide(player);
                    if (hasToLand) {
                        hasToLand = false;
                        glideToLocation(player, target.subtract(0, 150, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10, 1));
                    }
                    return;
                }
                player.setGliding(true);

                Vector direction = target.toVector().subtract(currentLocation.toVector()).normalize();
                avoidObstacles(player, direction);

                player.setVelocity(direction.multiply(globalSpeed));
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void avoidObstacles(Player player, Vector direction) {
        if (avoidingObstacles) {
            return;
        }

        avoidingObstacles = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location playerLocation = player.getLocation();
                Location locationAhead = playerLocation.clone().add(direction);

                if (locationAhead.getBlock().getType() != Material.AIR) {
                    try {
                        direction.setY(1);
                    } catch (Exception e){
                        try{
                            direction.setZ(1);
                        } catch (Exception e1) {
                            try {
                                direction.setX(1);
                            } catch (Exception e2) {
                                try {
                                    direction.setY(-1);
                                } catch (Exception e3) {
                                    avoidObstacles2(player, direction);
                                }
                            }
                        }
                    }
                }

                avoidingObstacles = false;
            }
        }.runTaskLater(this, 10L);
    }


    private void avoidObstacles2(Player player, Vector direction) {
        Location playerLocation = player.getLocation();

        Location locationAhead = playerLocation.clone().add(direction.clone().multiply(1.5));

        boolean canMoveUp = playerLocation.clone().add(0, 1, 0).getBlock().getType() == Material.AIR;
        boolean canMoveDown = playerLocation.clone().add(0, -1, 0).getBlock().getType() == Material.AIR;
        boolean canMoveLeft = playerLocation.clone().add(-1, 0, 0).getBlock().getType() == Material.AIR;
        boolean canMoveRight = playerLocation.clone().add(1, 0, 0).getBlock().getType() == Material.AIR;
        boolean canMoveForward = playerLocation.clone().add(0, 0, 1).getBlock().getType() == Material.AIR;
        boolean canMoveBackward = playerLocation.clone().add(0, 0, -1).getBlock().getType() == Material.AIR;

        if (locationAhead.getBlock().getType() != Material.AIR) {
            if (Math.abs(direction.getZ()) > 0.1 && (locationAhead.getBlock().getType() != Material.AIR)) {
                if (direction.getZ() > 0 && canMoveForward && (locationAhead.getBlock().getType() != Material.AIR)) {
                    direction.setZ(1);
                }
                else if (direction.getY() > 0 && canMoveUp && (locationAhead.getBlock().getType() != Material.AIR)) {
                    direction.setY(1);
                } else if (Math.abs(direction.getX()) > 0.1 && (locationAhead.getBlock().getType() != Material.AIR)) {
                    if (direction.getX() > 0 && canMoveRight && (locationAhead.getBlock().getType() != Material.AIR)) {
                        direction.setX(1);
                    } else if (direction.getX() < 0 && canMoveLeft && (locationAhead.getBlock().getType() != Material.AIR)) {
                        direction.setX(-1);
                    }
                } else if (direction.getY() < 0 && canMoveDown && (locationAhead.getBlock().getType() != Material.AIR)) {
                    direction.setY(-1);
                } else if (locationAhead.getBlock().getType() != Material.AIR && (locationAhead.getBlock().getType() != Material.AIR)) {

                    if (Math.abs(direction.getZ()) > 0.1 && (locationAhead.getBlock().getType() != Material.AIR)) {
                        if (direction.getZ() > 0 && canMoveForward && (locationAhead.getBlock().getType() != Material.AIR)) {
                            direction.setZ(1);
                        } else if (direction.getZ() < 0 && canMoveBackward && (locationAhead.getBlock().getType() != Material.AIR)) {
                            direction.setZ(-1);
                        }
                    }
                }
            }
        }
    }







    private void stopGlide(Player player) {
        glidingPlayers.remove(player);
        player.setGliding(false);
        player.setVelocity(new Vector(0, 0, 0));
    }
}


