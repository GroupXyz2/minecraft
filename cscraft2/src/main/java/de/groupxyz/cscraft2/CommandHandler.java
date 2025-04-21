package de.groupxyz.cscraft2;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHandler implements CommandExecutor {
    GameManager gameManager;

    public CommandHandler(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("cs")) {
                if (args.length == 0) {
                    player.sendMessage("Usage: /cs <command>");
                } else {
                    String subCommand = args[0];
                    GameConfig gameConfig = gameManager.getGameConfig();
                    switch (subCommand.toLowerCase()) {
                        case "start":
                            Bukkit.getLogger().info("Starting cs game.");
                            gameManager.startGame(false);
                            break;
                        case "startforced":
                            Bukkit.getLogger().info("Starting cs game forced.");
                            gameManager.startGame(true);
                            break;
                        case "stop":
                            gameManager.stopGame("Stopped by admin!");
                            break;
                        case "join":
                            gameManager.joinGame(player);
                            break;
                        case "joinall":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!gameManager.getPlayers().contains(p)) {
                                    gameManager.joinGame(p);
                                }
                            }
                            break;
                        case "leave":
                            gameManager.leaveGame(player);
                            break;
                        case "setctspawn":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 4) {
                                player.sendMessage("Usage: /cs setctspawn <x> <y> <z>");
                                return false;
                            }
                            try {
                                gameConfig.setCtSpawn(
                                        new Location(player.getWorld(),
                                                Double.parseDouble(args[1]),
                                                Double.parseDouble(args[2]),
                                                Double.parseDouble(args[3])));
                                player.sendMessage("CT-Spawn set.");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid coordinates.");
                                return false;
                            }
                            break;
                        case "settspawn":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 4) {
                                player.sendMessage("Usage: /cs settspawn <x> <y> <z>");
                                return false;
                            }
                            try {
                                gameConfig.setTSpawn(
                                        new Location(player.getWorld(),
                                                Double.parseDouble(args[1]),
                                                Double.parseDouble(args[2]),
                                                Double.parseDouble(args[3])));
                                player.sendMessage("T-Spawn set.");
                            } catch (NumberFormatException e) {
                                player.sendMessage("invalid coordinates.");
                                return false;
                            }
                            break;
                        case "setlobbyspawn":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 4) {
                                player.sendMessage("Usage: /cs setlobbyspawn <x> <y> <z>");
                                return false;
                            }
                            try {
                                gameConfig.setLobbySpawn(
                                        new Location(player.getWorld(),
                                                Double.parseDouble(args[1]),
                                                Double.parseDouble(args[2]),
                                                Double.parseDouble(args[3])));
                                player.sendMessage("Lobby spawn set.");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid coordinates.");
                                return false;
                            }
                            break;
                        case "setcountdowntime":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 2) {
                                player.sendMessage("Usage: /cs setcountdowntime <seconds>");
                                return false;
                            }
                            try {
                                gameConfig.setCountdownTime(Integer.parseInt(args[1]));
                                player.sendMessage("Countdown time set to " + args[1] + " seconds.");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid number of seconds.");
                                return false;
                            }
                            break;
                        case "setbombplacementtime":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 2) {
                                player.sendMessage("Usage: /cs setbombplacementtime <seconds>");
                                return false;
                            }
                            try {
                                gameConfig.setBombPlacementTime(Integer.parseInt(args[1]));
                                player.sendMessage("Bomb placement time set to " + args[1] + " seconds.");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid number of seconds.");
                                return false;
                            }
                            break;
                        case "setminplayers":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 2) {
                                player.sendMessage("Usage: /cs setminplayers <number>");
                                return false;
                            }
                            try {
                                gameConfig.setMinPlayers(Integer.parseInt(args[1]));
                                player.sendMessage("Minimum players set to " + args[1] + ".");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid number of players.");
                                return false;
                            }
                            break;
                        case "setmaxplayers":
                            if (!player.isOp()) {
                                player.sendMessage("You do not have permission to use this command.");
                                return false;
                            }
                            if (args.length != 2) {
                                player.sendMessage("Usage: /cs setmaxplayers <number>");
                                return false;
                            }
                            try {
                                gameConfig.setMaxPlayers(Integer.parseInt(args[1]));
                                player.sendMessage("Maximum players set to " + args[1] + ".");
                            } catch (NumberFormatException e) {
                                player.sendMessage("Invalid number of players.");
                                return false;
                            }
                            break;
                        case "weapons":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
                                return false;
                            }
                            WeaponSelect weaponSelect = Cscraft2.getInstance().getWeaponSelect();
                            weaponSelect.openAdminMenu(player);
                            break;

                        case "setmoney":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
                                return false;
                            }
                            if (args.length != 2) {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs setmoney <betrag>");
                                return false;
                            }
                            try {
                                int amount = Integer.parseInt(args[1]);
                                if (amount < 0) {
                                    player.sendMessage(ChatColor.RED + "Der Betrag muss positiv sein.");
                                    return false;
                                }
                                WeaponSelect moneyManager = Cscraft2.getInstance().getWeaponSelect();
                                moneyManager.setDefaultMoney(amount);
                                player.sendMessage(ChatColor.GREEN + "Startgeld auf $" + amount + " gesetzt.");
                            } catch (NumberFormatException e) {
                                player.sendMessage(ChatColor.RED + "Ungültiger Betrag.");
                                return false;
                            }
                            break;
                        case "weaponshop":
                            gameManager.openWeaponShop(player);
                            break;
                        case "friendlyfire":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
                                return false;
                            }

                            if (args.length < 2) {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs friendlyfire <on|off>");
                                return false;
                            }

                            if (args[1].equalsIgnoreCase("on")) {
                                gameManager.getFriendlyFire().setEnabled(true);
                                player.sendMessage(ChatColor.GREEN + "Friendly Fire wurde aktiviert!");
                            } else if (args[1].equalsIgnoreCase("off")) {
                                gameManager.getFriendlyFire().setEnabled(false);
                                player.sendMessage(ChatColor.GREEN + "Friendly Fire wurde deaktiviert!");
                            } else {
                                player.sendMessage(ChatColor.RED + "Ungültige Option. Verwende 'on' oder 'off'.");
                                return false;
                            }
                            break;
                        case "setteamdamage":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
                                return false;
                            }

                            if (args.length < 2) {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs setteamdamage <anzahl>");
                                return false;
                            }

                            try {
                                int amount = Integer.parseInt(args[1]);
                                if (amount < 1) {
                                    player.sendMessage(ChatColor.RED + "Die Anzahl muss mindestens 1 sein!");
                                    return false;
                                }

                                gameManager.getFriendlyFire().setMaxTeamDamageCount(amount);
                                player.sendMessage(ChatColor.GREEN + "Max. Teamschaden-Anzahl auf " + amount + " gesetzt.");
                            } catch (NumberFormatException e) {
                                player.sendMessage(ChatColor.RED + "Bitte gib eine gültige Zahl ein.");
                                return false;
                            }
                            break;
                        case "stats":
                            if (args.length > 1) {
                                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                                if (target != null) {
                                    Cscraft2.getInstance().getPlayerStats().showStats(player, target);
                                } else {
                                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                                }
                            } else {
                                Cscraft2.getInstance().getPlayerStats().showStats(player, player);
                            }
                            break;

                        case "topstats":
                            if (args.length > 1) {
                                String statType = args[1];
                                int count = 10;
                                if (args.length > 2) {
                                    try {
                                        count = Integer.parseInt(args[2]);
                                    } catch (NumberFormatException e) {
                                        player.sendMessage(ChatColor.RED + "Ungültige Anzahl!");
                                        return false;
                                    }
                                }
                                Cscraft2.getInstance().getPlayerStats().showTopStats(player, statType, count);
                            } else {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs topstats <statistiktyp> [anzahl]");
                            }
                            break;

                        case "resetstats":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
                                return false;
                            }
                            if (args.length > 1) {
                                Player target = Bukkit.getPlayer(args[1]);
                                if (target != null) {
                                    Cscraft2.getInstance().getPlayerStats().resetStats(target);
                                    player.sendMessage(ChatColor.GREEN + "Statistiken von " + target.getName() + " zurückgesetzt.");
                                } else {
                                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs resetstats <spieler>");
                            }
                            break;
                        case "enchantunsafe":
                            if (!player.isOp()) {
                                player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
                                return false;
                            }
                            if (args.length < 2) {
                                player.sendMessage(ChatColor.RED + "Verwendung: /cs enchantunsafe <enchantment> [level]");
                                return false;
                            }
                            ItemStack target = player.getItemInHand();
                            if (target != null) {
                                String enchantmentName = args[1].toLowerCase();
                                int level = 1;
                                if (args.length > 2) {
                                    try {
                                        level = Integer.parseInt(args[2]);
                                    } catch (NumberFormatException e) {
                                        player.sendMessage(ChatColor.RED + "Ungültige Stufe!");
                                        return false;
                                    }
                                }
                                try {
                                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentName));
                                    if (enchantment != null) {
                                        target.addUnsafeEnchantment(enchantment, level);
                                        player.sendMessage(ChatColor.GREEN + "Verzauberung " + enchantmentName + " auf " + target.getType() + " angewendet.");
                                    } else {
                                        player.sendMessage(ChatColor.RED + "Ungültige Verzauberung!");
                                    }
                                } catch (IllegalArgumentException e) {
                                    player.sendMessage(ChatColor.RED + "Ungültige Verzauberung!");
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "TargetItem nicht gefunden!");
                            }
                            break;
                    }
                }
                return true;
            }
        } else {
            sender.sendMessage("This command can only be used by players.");
        }
        return false;
    }
}