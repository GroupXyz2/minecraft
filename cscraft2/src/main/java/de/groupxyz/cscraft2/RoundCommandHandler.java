package de.groupxyz.cscraft2;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RoundCommandHandler implements CommandExecutor {
    private final GameConfig config;
    private final EndRoundGame roundGame;

    public RoundCommandHandler(GameConfig config, EndRoundGame roundGame) {
        this.config = config;
        this.roundGame = roundGame;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("cscraft.admin")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        if (args.length < 1) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                return handleSetCommand(player, args);
            case "info":
                sendRoundInfo(player);
                return true;
            default:
                sendHelpMessage(player);
                return true;
        }
    }

    private boolean handleSetCommand(Player player, String[] args) {
        if (args.length < 3) {
            sendHelpMessage(player);
            return true;
        }

        String option = args[1].toLowerCase();

        try {
            switch (option) {
                case "roundbased":
                    boolean isRoundBased = Boolean.parseBoolean(args[2]);
                    config.setRoundBased(isRoundBased);
                    roundGame.setRoundBasedMode(isRoundBased);
                    player.sendMessage(ChatColor.GREEN + "Rundenbasierter Modus wurde auf " +
                            (isRoundBased ? ChatColor.GOLD + "aktiviert" : ChatColor.RED + "deaktiviert") +
                            ChatColor.GREEN + " gesetzt.");
                    break;
                case "maxrounds":
                    int maxRounds = Integer.parseInt(args[2]);
                    if (maxRounds < 2 || maxRounds > 60) {
                        player.sendMessage(ChatColor.RED + "Maximale Rundenzahl muss zwischen 2 und 60 liegen.");
                        return true;
                    }
                    config.setMaxRounds(maxRounds);
                    roundGame.setMaxRounds(maxRounds);
                    player.sendMessage(ChatColor.GREEN + "Maximale Rundenzahl wurde auf " +
                            ChatColor.GOLD + maxRounds + ChatColor.GREEN + " gesetzt.");
                    break;
                case "moneyround":
                    int moneyPerRound = Integer.parseInt(args[2]);
                    if (moneyPerRound < 0 || moneyPerRound > 10000) {
                        player.sendMessage(ChatColor.RED + "Geld pro Runde muss zwischen 0 und 10000 liegen.");
                        return true;
                    }
                    config.setMoneyPerRound(moneyPerRound);
                    player.sendMessage(ChatColor.GREEN + "Geld pro Runde wurde auf " +
                            ChatColor.GOLD + "$" + moneyPerRound + ChatColor.GREEN + " gesetzt.");
                    break;
                case "moneykill":
                    int moneyPerKill = Integer.parseInt(args[2]);
                    if (moneyPerKill < 0 || moneyPerKill > 5000) {
                        player.sendMessage(ChatColor.RED + "Geld pro Kill muss zwischen 0 und 5000 liegen.");
                        return true;
                    }
                    config.setMoneyPerKill(moneyPerKill);
                    player.sendMessage(ChatColor.GREEN + "Geld pro Kill wurde auf " +
                            ChatColor.GOLD + "$" + moneyPerKill + ChatColor.GREEN + " gesetzt.");
                    break;
                case "moneywin":
                    int winnerBonus = Integer.parseInt(args[2]);
                    if (winnerBonus < 0 || winnerBonus > 10000) {
                        player.sendMessage(ChatColor.RED + "Siegerbonus muss zwischen 0 und 10000 liegen.");
                        return true;
                    }
                    config.setWinnerBonus(winnerBonus);
                    player.sendMessage(ChatColor.GREEN + "Siegerbonus wurde auf " +
                            ChatColor.GOLD + "$" + winnerBonus + ChatColor.GREEN + " gesetzt.");
                    break;
                case "moneylose":
                    int loserBonus = Integer.parseInt(args[2]);
                    if (loserBonus < 0 || loserBonus > 5000) {
                        player.sendMessage(ChatColor.RED + "Verliererbonus muss zwischen 0 und 5000 liegen.");
                        return true;
                    }
                    config.setLoserBonus(loserBonus);
                    player.sendMessage(ChatColor.GREEN + "Verliererbonus wurde auf " +
                            ChatColor.GOLD + "$" + loserBonus + ChatColor.GREEN + " gesetzt.");
                    break;
                default:
                    sendHelpMessage(player);
                    return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Ungültiger Wert. Bitte gib eine gültige Zahl ein.");
            return true;
        }

        return true;
    }

    private void sendRoundInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Runden-Einstellungen ===");
        player.sendMessage(ChatColor.YELLOW + "Rundenbasierter Modus: " +
                (config.isRoundBased() ? ChatColor.GREEN + "Aktiviert" : ChatColor.RED + "Deaktiviert"));
        player.sendMessage(ChatColor.YELLOW + "Maximale Rundenzahl: " + ChatColor.WHITE + config.getMaxRounds());
        player.sendMessage(ChatColor.YELLOW + "Geld pro Runde: " + ChatColor.WHITE + "$" + config.getMoneyPerRound());
        player.sendMessage(ChatColor.YELLOW + "Geld pro Kill: " + ChatColor.WHITE + "$" + config.getMoneyPerKill());
        player.sendMessage(ChatColor.YELLOW + "Geld für Bombe platzieren: " + ChatColor.WHITE + "$" + config.getMoneyPerPlant());
        player.sendMessage(ChatColor.YELLOW + "Geld für Bombe entschärfen: " + ChatColor.WHITE + "$" + config.getMoneyPerDefuse());
        player.sendMessage(ChatColor.YELLOW + "Siegerbonus: " + ChatColor.WHITE + "$" + config.getWinnerBonus());
        player.sendMessage(ChatColor.YELLOW + "Verliererbonus: " + ChatColor.WHITE + "$" + config.getLoserBonus());

        if (roundGame.getCurrentRound() > 0) {
            player.sendMessage(ChatColor.GOLD + "=== Aktuelles Spiel ===");
            player.sendMessage(ChatColor.YELLOW + "Aktuelle Runde: " + ChatColor.WHITE + roundGame.getCurrentRound());
            player.sendMessage(ChatColor.BLUE + "CT: " + roundGame.getCTScore() + ChatColor.WHITE + " : " +
                    ChatColor.RED + roundGame.getTScore() + " T");
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== CSCraft2 Runden-Befehle ===");
        player.sendMessage(ChatColor.YELLOW + "/csround set roundbased <true/false> " +
                ChatColor.GRAY + "- Aktiviert/Deaktiviert den rundenbasierten Modus");
        player.sendMessage(ChatColor.YELLOW + "/csround set maxrounds <Zahl> " +
                ChatColor.GRAY + "- Setzt die maximale Rundenzahl");
        player.sendMessage(ChatColor.YELLOW + "/csround set moneyround <Zahl> " +
                ChatColor.GRAY + "- Setzt das Geld pro Runde");
        player.sendMessage(ChatColor.YELLOW + "/csround set moneykill <Zahl> " +
                ChatColor.GRAY + "- Setzt das Geld pro Kill");
        player.sendMessage(ChatColor.YELLOW + "/csround set moneywin <Zahl> " +
                ChatColor.GRAY + "- Setzt den Siegerbonus");
        player.sendMessage(ChatColor.YELLOW + "/csround set moneylose <Zahl> " +
                ChatColor.GRAY + "- Setzt den Verliererbonus");
        player.sendMessage(ChatColor.YELLOW + "/csround info " +
                ChatColor.GRAY + "- Zeigt alle Runden-Einstellungen");
    }
}