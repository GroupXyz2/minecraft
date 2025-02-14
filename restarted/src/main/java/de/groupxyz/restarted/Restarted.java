package de.groupxyz.restarted;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

public class Restarted extends JavaPlugin {

    private List<ZonedDateTime> restartTimes;
    private List<Integer> announcementTimes;
    private List<ZonedDateTime> scheduledRestarts;
    private ZoneId timeZone;

    @Override
    public void onEnable() {
        getLogger().info("Restarted by GroupXyz starting...");
        saveDefaultConfig();
        loadConfig();

        if (!restartTimes.isEmpty()) {
            getLogger().info("Taegliche Restarts festgelegt fuer " + restartTimes);
            for (ZonedDateTime time : restartTimes) {
                logRestart("Taeglicher Restart geplant um " + time + " bei Serverstart: " + LocalDateTime.now());
            }
            scheduleRestartCheck();
        } else {
            getLogger().warning("Keine restart_times in config.yml festgelegt!");
        }
    }

    private void loadConfig() {
        restartTimes = new ArrayList<>();
        scheduledRestarts = new ArrayList<>();

        if (!getConfig().contains("timezone")) {
            String systemTimeZone = ZoneId.systemDefault().toString();
            getConfig().set("timezone", systemTimeZone);
            saveConfig();
            getLogger().info("Keine Zeitzone in config.yml gefunden. Lokale Zeitzone (" + systemTimeZone + ") automatisch ausgewaehlt.");
        }

        String timeZoneConfig = getConfig().getString("timezone", ZoneId.systemDefault().toString());
        timeZone = ZoneId.of(timeZoneConfig);

        List<String> times = getConfig().getStringList("restart_times");
        for (String time : times) {
            try {
                LocalTime localTime = LocalTime.parse(time);
                ZonedDateTime zonedTime = ZonedDateTime.of(LocalDate.now(), localTime, timeZone);
                restartTimes.add(zonedTime);
            } catch (Exception e) {
                getLogger().severe("Fehler beim Parsen der Zeit " + time + ": " + e.getMessage());
            }
        }


        announcementTimes = getConfig().getIntegerList("announcement_times");
    }

    private void scheduleRestartCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForAnnouncements();
            }
        }.runTaskTimer(this, 0, 20 * 60);
    }

    private void checkForAnnouncements() {
        ZonedDateTime now = ZonedDateTime.now(timeZone);

        for (ZonedDateTime restartTime : restartTimes) {
            ZonedDateTime nextRestart = restartTime.withYear(now.getYear()).withMonth(now.getMonthValue()).withDayOfMonth(now.getDayOfMonth());

            if (now.isAfter(nextRestart)) {
                nextRestart = nextRestart.plusDays(1);
            }

            long minutesUntilRestart = Duration.between(now, nextRestart).toMinutes();

            if (minutesUntilRestart <= 0 && !scheduledRestarts.contains(restartTime)) {
                scheduledRestarts.add(restartTime);
                Bukkit.broadcastMessage(ChatColor.RED + "Der Server wird in 10 Sekunden neugestartet!");
                logRestart("Restart für " + restartTime + " geplant um " + now);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.shutdown();
                    }
                }.runTaskLater(this, 200L);
            } else {
                for (int minutes : announcementTimes) {
                    if (minutesUntilRestart == minutes) {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Der Server wird in " + minutes + " Minuten automatisch neugestartet!");
                    }
                }
            }
        }
    }

    private void logRestart(String message) {
        File logFile = new File(getDataFolder(), "restart_log.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            getLogger().severe("Fehler beim Schreiben ins Log-File: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Restarted shutting down.");
        logRestart("Restarting now at: " + LocalDateTime.now());
    }
}
