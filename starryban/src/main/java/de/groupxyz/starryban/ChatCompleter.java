package de.groupxyz.starryban;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChatCompleter implements TabCompleter {

    private final banmanager banManager;
    private final mutemanager muteManager;

    public ChatCompleter(banmanager banManager, mutemanager muteManager) {
        this.banManager = banManager;
        this.muteManager = muteManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("ban")) {
            if (args.length == 1) {
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(player.getName());
                    }
                }
            } else if (args.length == 2) {
                suggestions.add("5min");
                suggestions.add("10min");
                suggestions.add("1h");
                suggestions.add("12h");
                suggestions.add("1d");
                suggestions.add("7d");
            } else if (args.length == 3) {
                suggestions.add("Griefing");
                suggestions.add("Cheating");
                suggestions.add("Inappropriate Language");
                suggestions.add("Spamming");
                suggestions.add("Abusing Exploits");
            }
        } else if (command.getName().equalsIgnoreCase("unban")) {
            if (args.length == 1) {
                Set<String> bannedPlayerIds = banManager.getBannedPlayerIds();
                for (String playerId : bannedPlayerIds) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                    suggestions.add(player.getName());
                }
            }
        } else if (command.getName().equalsIgnoreCase("mute")) {
            if (args.length == 1) {
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(player.getName());
                    }
                }
            } else if (args.length == 2) {
                suggestions.add("5");
                suggestions.add("10");
                suggestions.add("60");
                suggestions.add("180");
                suggestions.add("1440");
                suggestions.add("10080");
            } else if (args.length == 3) {
                suggestions.add("Spamming");
                suggestions.add("Inappropriate Language");
                suggestions.add("Harassment");
                suggestions.add("Advertising");
            }
        } else if (command.getName().equalsIgnoreCase("unmute")) {
            if (args.length == 1) {
                Set<String> mutedPlayerIds = muteManager.getMutedPlayerIds();
                for (String playerId : mutedPlayerIds) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                    //suggestions.add(player.getName());
                }
            }
        }

        return suggestions;
    }
}



