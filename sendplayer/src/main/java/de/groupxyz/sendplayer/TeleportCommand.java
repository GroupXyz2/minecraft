package de.groupxyz.sendplayer;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TeleportCommand implements CommandExecutor {
    private final Sendplayer plugin;

    public TeleportCommand(Sendplayer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tphere")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            showAvailablePlayers(player);
            return true;
        }

        String targetPlayerName = args[0];

        teleportPlayerToYou(player, targetPlayerName);

        return true;
    }

    private void showAvailablePlayers(Player player) {
        List<String> playerNames = new ArrayList<>();

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            playerNames.add(onlinePlayer.getName());
        }

        player.sendMessage(ChatColor.DARK_PURPLE + "Available Player: " + String.join(", ", playerNames));
    }

    private void teleportPlayerToYou(Player sender, String targetPlayerName) {
        Player targetPlayer = findPlayer(targetPlayerName);

        if (targetPlayer != null) {

            String serverName = Bukkit.getServer().getName();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("ConnectOther");
            out.writeUTF(targetPlayer.getName());
            out.writeUTF(serverName);

            sender.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

            targetPlayer.teleport(sender.getLocation());
            targetPlayer.sendMessage(ChatColor.DARK_PURPLE + "You got summoned to " + sender.getName());
            sender.sendMessage(ChatColor.DARK_PURPLE + "Summoned " + targetPlayer.getName() + " to you.");
        } else {
            sender.sendMessage(ChatColor.DARK_PURPLE + "Player not found!");
        }
    }

    private Player findPlayer(String playerName) {
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer.getName().equalsIgnoreCase(playerName)) {
                return onlinePlayer;
            }
        }
        return null;
    }
}

