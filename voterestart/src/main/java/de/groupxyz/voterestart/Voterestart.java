package de.groupxyz.voterestart;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Voterestart extends JavaPlugin {

    private boolean isEnabled = true;
    private boolean isVoting = false;
    private int yesVotes = 0;
    private int totalPlayers = 0;
    double @NotNull [] serverTPS = getServer().getTPS();
    double recentTPS = serverTPS[serverTPS.length - 1];
    String serverVersion = getServer().getName();
    private Set<Player> votedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        getLogger().info("VoteRestart by GroupXyz starting...");
        int pluginId = 21504;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("voterestart_enabled", () -> {
            return String.valueOf(isEnabled);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("yes_votes", () -> {
            return String.valueOf(yesVotes);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("server_version", () -> {
            return String.valueOf(serverVersion);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("server_tps", () -> {
            return String.valueOf(recentTPS);
        }));
    }

    @Override
    public void onDisable() {
        getLogger().info("VoteRestart by GroupXyz shutting down!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player) {

            if (command.getName().equalsIgnoreCase("voterestart")) {
                startVoting(player);
                return true;
            } else if (command.getName().equalsIgnoreCase("vrenable")) {
                toggleStatus(player);
                return true;
            } else if (command.getName().equalsIgnoreCase("vr")) {
                countVote(player);
                return true;
            }
            return false;
        }
        sender.sendMessage("Nur Spieler können voten!");
        return false;

    }

    private void startVoting(Player player) {
        if (isEnabled) {
            if (!isVoting) {
                if (Bukkit.getOnlinePlayers().size() != 1) {
                    if (!votedPlayers.contains(player)) {
                        isVoting = true;
                        totalPlayers = Bukkit.getOnlinePlayers().size();
                        yesVotes = 0;
                        votedPlayers.clear();

                        getServer().broadcastMessage(ChatColor.AQUA + "Eine Neustart-Umfrage wurde gestartet, du hast 2 Minuten Zeit zum abstimmen!");

                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 50f, 50f);
                            TextComponent message = new TextComponent(ChatColor.GOLD + "Klicke hier zum Neustarten!");
                            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vr"));
                            onlinePlayer.spigot().sendMessage(message);
                        }

                        checkVotes();

                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            isVoting = false;
                            getServer().broadcastMessage(ChatColor.AQUA + "Die Abstimmung wurde beendet, es haben nicht genug Spieler abgestimmt!");
                            Bukkit.getScheduler().runTaskLater(this, this::releasePlayers, 300 * 20);

                        }, 2 * 60 * 20);
                    } else {
                        player.sendMessage(ChatColor.RED + "Du hast bereits abgestimmt oder der Cooldown ist noch nicht abgelaufen, bitte warte 5 Minuten!");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Es braucht mehr Spieler zum abstimmen!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Eine Abstimmung existiert bereits!");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Sorry, das voten ist derzeit deaktiviert!");
        }
    }

    public void countVote(Player player) {
        if (isEnabled) {
            if (isVoting) {
                if (Bukkit.getOnlinePlayers().size() != 1) {
                    if (!votedPlayers.contains(player)) {
                        yesVotes++;
                        getLogger().info(player.getName() + " has voted for a restart!");
                        player.sendMessage(ChatColor.AQUA + "Du hast zum Neustarten abgestimmt.");
                        votedPlayers.add(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "Du hast bereits abgestimmt oder der Cooldown ist noch nicht abgelaufen, bitte warte 5 Minuten!");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Sorry, es sind zu wenig Spieler zum abstimmen online!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Derzeit findet kein voting statt, bitte starte eins mit /voterestart");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Sorry, das voten ist derzeit deaktiviert!");
        }
    }

    private void checkVotes() {
        if (isVoting && yesVotes == totalPlayers && totalPlayers != 1) {
            isVoting = false;
            getServer().broadcastMessage(ChatColor.AQUA + "Alle Spieler haben zum Neustarten gestimmt, der Server restartet in 10 Sekunden!");
            Bukkit.getScheduler().runTaskLater(this, this::shutdownServer, 10 * 20);
        } else if (isVoting) {
            Bukkit.getScheduler().runTaskLater(this, this::checkVotes, 20);
        }
    }

    private void shutdownServer() {
        votedPlayers.clear();
        getLogger().warning("Shutting down because players voted to restart, this is just a reminder!");
        Bukkit.shutdown();
    }

    private void releasePlayers() {
        votedPlayers.clear();
    }

    public void toggleStatus(Player player) {
        if (!isEnabled) {
            isEnabled = true;
            player.sendMessage(ChatColor.GREEN + "VoteRestart enabled!");
        } else if (isEnabled) {
            isEnabled = false;
            player.sendMessage(ChatColor.GREEN + "VoteRestart disabled!");
        }
    }

}
