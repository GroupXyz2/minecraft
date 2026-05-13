package de.groupxyz.groupsskywars;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

public class ZoneManager {
    private final Location center;
    private final double initialRadius;
    private final double durationInSeconds;
    private double currentRadius;
    private BukkitTask shrinkTask;
    private boolean hasAnnounced50Percent = false;
    private boolean hasAnnounced25Percent = false;
    private boolean hasAnnouncedFinal = false;
    private long startTime;

    public ZoneManager(Location center, double initialRadius, double durationInSeconds) {
        this.center = center;
        this.initialRadius = initialRadius;
        this.currentRadius = initialRadius;
        this.durationInSeconds = durationInSeconds;
    }

    public void startShrinkingZone() {
        startTime = System.currentTimeMillis();

        announceZoneStart();

        double shrinkPerSecond = initialRadius / durationInSeconds;
        double shrinkPerTick = shrinkPerSecond / 20.0;

        shrinkTask = new BukkitRunnable() {
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

                double percentRemaining = (currentRadius / initialRadius) * 100;
                if (percentRemaining <= 50 && !hasAnnounced50Percent) {
                    announceZoneHalfway();
                    hasAnnounced50Percent = true;
                } else if (percentRemaining <= 25 && !hasAnnounced25Percent) {
                    announceZoneCritical();
                    hasAnnounced25Percent = true;
                } else if (percentRemaining <= 10 && !hasAnnouncedFinal) {
                    announceZoneFinal();
                    hasAnnouncedFinal = true;
                }

                displayZone();
                updatePlayerActionBars();
                damagePlayersOutsideZone();
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("groupsskywars"), 0L, 1L);
    }

    public void stop() {
        if (shrinkTask != null) {
            shrinkTask.cancel();
            shrinkTask = null;
        }

        if (center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                player.sendActionBar(Component.empty());
            }
        }
    }

    private void updatePlayerActionBars() {
        if (center.getWorld() == null) {
            return;
        }

        for (Player player : center.getWorld().getPlayers()) {

            Location playerLocation = player.getLocation();
            double distance = playerLocation.distance(center);
            double distanceToEdge = currentRadius - distance;

            Component actionBar;

            if (distanceToEdge < 0) {
                actionBar = Component.text("⚠ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("AUßERHALB DER ZONE! ", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text("Gehe " + String.format("%.1f", Math.abs(distanceToEdge)) + "m zurück!", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            } else if (distanceToEdge < 10) {

                actionBar = Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("Zonengrenze: ", NamedTextColor.GOLD))
                    .append(Component.text(String.format("%.1f", distanceToEdge) + "m", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Zone: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE));
            } else if (distanceToEdge < 30) {
                actionBar = Component.text("⚠ ", NamedTextColor.YELLOW)
                    .append(Component.text("Zonengrenze: ", NamedTextColor.GOLD))
                    .append(Component.text(String.format("%.1f", distanceToEdge) + "m", NamedTextColor.YELLOW))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Zone: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE));
            } else {
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                long remainingSeconds = (long) durationInSeconds - elapsedSeconds;

                actionBar = Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("In der Zone ", NamedTextColor.GREEN))
                    .append(Component.text("| ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Radius: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Zeit: ", NamedTextColor.GRAY))
                    .append(Component.text(formatTime(remainingSeconds), NamedTextColor.AQUA));
            }

            player.sendActionBar(actionBar);
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private void displayZone() {
        int particleCount = 120;
        int verticalLayers = 5;

        for (int layer = 0; layer < verticalLayers; layer++) {
            double y = center.getY() + (layer * 15) - 10;

            for (int i = 0; i < particleCount; i++) {
                double angle = 2 * Math.PI * i / particleCount;
                double x = center.getX() + currentRadius * Math.cos(angle);
                double z = center.getZ() + currentRadius * Math.sin(angle);

                Location particleLocation = new Location(center.getWorld(), x, y, z);

                Particle particleType;
                if (currentRadius < initialRadius * 0.25) {
                    particleType = Particle.FLAME;
                } else if (currentRadius < initialRadius * 0.5) {
                    particleType = Particle.SOUL_FIRE_FLAME;
                } else {
                    particleType = Particle.END_ROD;
                }

                center.getWorld().spawnParticle(particleType, particleLocation, 1, 0, 0, 0, 0);
            }
        }
    }


    private void damagePlayersOutsideZone() {
        if (center.getWorld() == null) {
            return;
        }

        for (Player player : center.getWorld().getPlayers()) {
            Location playerLocation = player.getLocation();


            double distance = playerLocation.distance(center);

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

    private void announceZoneStart() {
        Component title = Component.text("⚠ DIE ZONE SCHRUMPFT ⚠", NamedTextColor.RED, TextDecoration.BOLD);
        Component subtitle = Component.text("Bleibe innerhalb der Zone!", NamedTextColor.YELLOW);

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))
        );

        Component broadcastMessage = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("Zone", NamedTextColor.DARK_PURPLE))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Die Zone beginnt zu schrumpfen! ", NamedTextColor.RED))
            .append(Component.text("Startradius: ", NamedTextColor.GRAY))
            .append(Component.text(String.format("%.0f", initialRadius) + "m", NamedTextColor.WHITE));

        if (center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                player.showTitle(titleObj);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                player.sendMessage(broadcastMessage);
            }
        }
    }

    private void announceZoneHalfway() {
        Component title = Component.text("ZONE: 50%", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component subtitle = Component.text("Die Zone ist nur noch halb so groß!", NamedTextColor.YELLOW);

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))
        );

        Component broadcastMessage = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("Zone", NamedTextColor.DARK_PURPLE))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Die Zone ist nur noch ", NamedTextColor.GOLD))
            .append(Component.text("50% ", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("so groß! Radius: ", NamedTextColor.GOLD))
            .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE));

        if (center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                player.showTitle(titleObj);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
                player.sendMessage(broadcastMessage);
            }
        }
    }

    private void announceZoneCritical() {
        Component title = Component.text("⚠ ZONE: 25% ⚠", NamedTextColor.RED, TextDecoration.BOLD);
        Component subtitle = Component.text("Vorsicht! Die Zone ist sehr klein!", NamedTextColor.GOLD);

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))
        );

        Component broadcastMessage = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("Zone", NamedTextColor.DARK_PURPLE))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("⚠ KRITISCH! Die Zone ist nur noch ", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("25% ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text("so groß! Radius: ", NamedTextColor.RED))
            .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE));

        if (center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                player.showTitle(titleObj);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 2.0f);
                player.sendMessage(broadcastMessage);
            }
        }
    }

    private void announceZoneFinal() {
        Component title = Component.text("⚠⚠ ZONE: 10% ⚠⚠", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        Component subtitle = Component.text("Die Zone ist extrem klein!", NamedTextColor.RED);

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))
        );

        Component broadcastMessage = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Zone", NamedTextColor.DARK_PURPLE))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠⚠ FINALE PHASE! ⚠⚠ ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text("Radius: ", NamedTextColor.RED))
                .append(Component.text(String.format("%.0f", currentRadius) + "m", NamedTextColor.WHITE, TextDecoration.BOLD));

        if (center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                player.showTitle(titleObj);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                player.sendMessage(broadcastMessage);
            }
        }
    }
}

