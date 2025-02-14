package de.groupxyz.starryban;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Starryban extends JavaPlugin implements Listener {

    private mutemanager muteManager;
    private banmanager banManager;
    public BanList banList;
    private BukkitTask unmuteTask;
    private BukkitTask unbanTask;

    @Override
    public void onEnable() {
        muteManager = new mutemanager(this);
        banManager = new banmanager(this);
        banList = Bukkit.getBanList(BanList.Type.NAME);
        getLogger().info("Starryban by GroupXyz enabled successfully!");

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("ban").setTabCompleter(new ChatCompleter(banManager, muteManager));
        getCommand("unban").setTabCompleter(new ChatCompleter(banManager, muteManager));
        getCommand("mute").setTabCompleter(new ChatCompleter(banManager, muteManager));
        getCommand("unmute").setTabCompleter(new ChatCompleter(banManager, muteManager));

        unbanTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            List<BanEntry> bannedPlayers = new ArrayList<>(banList.getBanEntries());

            long currentTimeMillis = System.currentTimeMillis();

            for (BanEntry banEntry : bannedPlayers) {
                if (banEntry.getExpiration() != null && banEntry.getExpiration().getTime() <= currentTimeMillis) {
                    banManager.unbanPlayer(Bukkit.getOfflinePlayer(banEntry.getTarget()));
                    banList.pardon(banEntry.getTarget());
                    getLogger().info("Player " + banEntry.getTarget() + " has been automatically unbanned.");
                }
            }
        }, 0L, 20L * 60L * 5L);
    }

    @Override
    public void onDisable() {
        if (unbanTask != null) {
            unbanTask.cancel();
        }
        getLogger().warning("Starryban disabled, take care!!!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mute")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED+ "Usage: /mute <player> <time in minutes> <reason>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            int muteDurationMinutes;
            try {
                muteDurationMinutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid mute duration.");
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            mutePlayer(target, muteDurationMinutes, reason);
            sender.sendMessage(ChatColor.RED + "Player " + target.getName() + " muted for " + muteDurationMinutes + " minutes. Reason: " + reason);
            return true;
        } else if (command.getName().equalsIgnoreCase("unmute")) {
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            unmutePlayer(target, sender);
            sender.sendMessage(ChatColor.RED + "Player " + target.getName() + " unmuted.");
            return true;
        } else if (command.getName().equalsIgnoreCase("ban")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /ban <player> <time> <reason>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            Long banDurationMilliseconds = parseDuration(args[1]);
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            banPlayer(target, banDurationMilliseconds, reason);
            sender.sendMessage(ChatColor.RED + "Player " + target.getName() + " banned for " + args[1] + ". Reason: " + reason);
            return true;
        } else if (command.getName().equalsIgnoreCase("unban")) {
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
                return true;
            }

            String playerName = args[0];
            unbanPlayer(playerName, sender);
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " unbanned.");
            return true;
        }

        return false;
    }

    private void mutePlayer(OfflinePlayer player, int muteDurationMinutes, String reason) {
        long muteDurationMilliseconds = muteDurationMinutes * 60 * 1000;
        muteManager.mutePlayer(player, muteDurationMilliseconds);
        if (player.isOnline()) {
            player.getPlayer().sendMessage(ChatColor.RED + "You have been muted for " + muteDurationMinutes + " minutes. Reason: " + reason);
        }
    }

    private void unmutePlayer(OfflinePlayer player, CommandSender sender) {
        muteManager.unmutePlayer(player);
        if (player.isOnline()) {
            player.getPlayer().sendMessage(ChatColor.RED + "You have been unmuted.");
        } else {
            if (sender != null) {
                sender.sendMessage(ChatColor.YELLOW + "INFO: Player is not online, could not verify succes");
            }
        }
    }

    private void banPlayer(OfflinePlayer player, Long banDurationMilliseconds, String reason) {
        if (player != null) {
            if (banDurationMilliseconds != null) {
                Date expireDate = new Date(System.currentTimeMillis() + banDurationMilliseconds);
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, expireDate, "Starryban");
            } else {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, null, "Starryban");
            }
            if (player.isOnline()) {
                player.getPlayer().kickPlayer(ChatColor.RED + "You are banned for " + (banDurationMilliseconds == null ? "permanently" : formatDuration(banDurationMilliseconds)) + ". Reason: " + reason);
            }
        } else {
            banManager.banPlayer(player, banDurationMilliseconds);
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, null, "Starryban");
        }
    }

    private void unbanPlayer(String playerName, CommandSender sender) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target != null) {
            banManager.unbanPlayer(target);
            banList.pardon(playerName);
        } else {
            sender.sendMessage(ChatColor.RED + "Player not found!");

        }
    }

    private Long parseDuration(String duration) {
        Pattern pattern = Pattern.compile("(\\d+)([smhd])");
        Matcher matcher = pattern.matcher(duration);
        if (matcher.matches()) {
            int value = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 's': return value * 1000L;
                case 'm': return value * 60 * 1000L;
                case 'h': return value * 60 * 60 * 1000L;
                case 'd': return value * 24 * 60 * 60 * 1000L;
            }
        }
        return null;
    }

    private String formatDuration(Long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) return seconds + " seconds";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " minutes";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hours";
        long days = hours / 24;
        return days + " days";
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        OfflinePlayer player = event.getPlayer();
        if (muteManager.isMuted(player)) {
            long unmuteTimeMillis = muteManager.getUnmuteTime(player);
            long currentTimeMillis = System.currentTimeMillis();

            if (unmuteTimeMillis > currentTimeMillis) {
                long timeRemainingMillis = unmuteTimeMillis - currentTimeMillis;
                int timeRemainingMinutes = (int) (timeRemainingMillis / (1000 * 60));

                event.setCancelled(true);
                if (player.isOnline()) {
                    player.getPlayer().sendMessage(ChatColor.RED + "You are currently muted. " + timeRemainingMinutes + " minutes remaining.");
                }
            } else {
                unmutePlayer(player, null);
            }
        }
    }
}





