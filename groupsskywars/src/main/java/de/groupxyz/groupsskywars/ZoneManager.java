package de.groupxyz.groupsskywars;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class ZoneManager {
    private final Location center;
    private final double initialRadius;
    private final double durationInSeconds;
    private double currentRadius;

    public ZoneManager(Location center, double initialRadius, double durationInSeconds) {
        this.center = center;
        this.initialRadius = initialRadius;
        this.currentRadius = initialRadius;
        this.durationInSeconds = durationInSeconds;
    }

    public void startShrinkingZone() {
        double shrinkPerSecond = initialRadius / durationInSeconds;
        double shrinkPerTick = shrinkPerSecond / 20.0;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentRadius <= 0) {
                    this.cancel();
                    return;
                }
                if (center.getWorld() == null) {
                    this.cancel();
                    return;
                }

                currentRadius -= shrinkPerTick;

                displayZone();

                damagePlayersOutsideZone();
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("groupsskywars"), 0L, 1L);
    }

    private void displayZone() {
        int particleCount = 100;
        int heightRange = 120;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = center.getX() + currentRadius * Math.cos(angle);
            double z = center.getZ() + currentRadius * Math.sin(angle);

            double y = center.getY() + (Math.random() * heightRange - (heightRange / 2.0));

            Location particleLocation = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(Particle.FLAME, particleLocation, 1);
        }
    }


    private void damagePlayersOutsideZone() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLocation = player.getLocation();

            double distance = playerLocation.distance(center);

            if (!playerLocation.getWorld().equals(center.getWorld())) {
                return;
            }

            if (distance > currentRadius) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, Integer.MAX_VALUE, 0, false, false));
                player.damage(2.0);
            } else {
                if (player.hasPotionEffect(PotionEffectType.CONFUSION)) {
                    player.removePotionEffect(PotionEffectType.CONFUSION);
                }
            }
        }
    }
}

