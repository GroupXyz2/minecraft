package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "startforced", "join", "joinall", "leave",
                    "setctspawn", "settspawn", "setcountdowntime", "setminplayers",
                    "setmaxplayers", "weapons", "setmoney", "weaponshop", "friendlyfire",
                    "setteamdamage", "stats", "topstats", "resetstats", "enchantunsafe",
                    "setlobbyspawn");
        }
        if (args.length > 1) {
            if (args[0].equalsIgnoreCase("stats")) {
                if (sender.isOp()) {
                    String partialName = args[1];
                    return getOfflinePlayerNames(partialName);
                }
            }
            if (args[0].equalsIgnoreCase("topstats")) {
                if (args.length == 2) {
                    return Arrays.asList("wins", "losses", "kills", "deaths", "bombsPlanted",
                            "bombsDefused", "roundsPlayed", "damage");
                } else if (args.length == 3) {
                    return Arrays.asList("<count>");
                }
            }
            else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("enchantunsafe")) {
                    return getEnchantmentNames(args[1]);
                } else if (args.length == 3) {
                    return Arrays.asList("<level>");
                }
            }
            if (args[0].equalsIgnoreCase("resetstats")) {
                return Arrays.asList(Arrays.toString(Bukkit.getOfflinePlayers()));
            }
            if (args[0].equalsIgnoreCase("friendlyfire")) {
                return Arrays.asList("on", "off");
            }
            if (args[0].equalsIgnoreCase("setctspawn") || args[0].equalsIgnoreCase("settspawn") ||
                    args[0].equalsIgnoreCase("setlobbyspawn")) {
                if (args.length == 2) {
                    return Arrays.asList("<x>");
                } else if (args.length == 3) {
                    return Arrays.asList("<y>");
                } else if (args.length == 4) {
                    return Arrays.asList("<z>");
                }
            }
            if (args[0].equalsIgnoreCase("setcountdowntime") ||
                    args[0].equalsIgnoreCase("setminplayers") ||
                    args[0].equalsIgnoreCase("setmaxplayers") ||
                    args[0].equalsIgnoreCase("setteamdamage") ||
                    args[0].equalsIgnoreCase("setmoney")) {
                return Arrays.asList("<value>");
            }
        }
        return Collections.emptyList();
    }

    public List<String> getEnchantmentNames(String current) {
        List<String> enchantments = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.values()) {
            String name = enchantment.getKey().getKey();
            enchantments.add(name);
        }

        if (current.isEmpty()) {
            return enchantments;
        }

        return enchantments.stream()
                .filter(name -> name.toLowerCase().startsWith(current.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getOfflinePlayerNames(String partialName) {
        List<String> playerNames = new ArrayList<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && (partialName.isEmpty() || name.toLowerCase().startsWith(partialName.toLowerCase()))) {
                playerNames.add(name);
            }
        }
        return playerNames;
    }
}
