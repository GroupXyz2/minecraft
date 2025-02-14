package de.groupxyz.sendplayer;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class SendCommand implements CommandExecutor {
    private final Sendplayer plugin;

    public SendCommand(Sendplayer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sendplayer")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage(ChatColor.DARK_PURPLE + "Usage: /sendplayer [Playername|@s] [Servername|Hub|Survival|Lifesteal]");
            return true;
        }

        String targetPlayerName = args[0];
        String targetServerName = args[1];

        if (targetPlayerName.equals("@s")) {
            targetPlayerName = player.getName();
        }

        sendPlayerToServer(player, targetPlayerName, targetServerName);

        return true;
    }

    private void sendPlayerToServer(Player sender, String targetPlayerName, String targetServerName) {
        Player targetPlayer = findPlayer(targetPlayerName);

        if (targetPlayer != null) {
            if (isServerOnline(targetServerName)) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("ConnectOther");
                out.writeUTF(targetPlayer.getName());
                out.writeUTF(targetServerName);

                sender.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

                teleportPlayerToSpawn(sender, targetPlayer, targetServerName);
            } else {
                sender.sendMessage(ChatColor.DARK_PURPLE + "Targeted server is offline or doesn't exist!");
            }
        } else {
            sender.sendMessage(ChatColor.DARK_PURPLE + "Player not found!");
        }
    }

    private boolean isServerOnline(String serverName) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equals(serverName)) {
                return true;
            }
            else if (serverName.equals("Hub")) {
                return true;
            }
            else if (serverName.equals("Survival")) {
                return true;
            }
            else if (serverName.equals("Lifesteal")) {
                return true;
            }
        }
        return false;
    }

    private void teleportPlayerToSpawn(Player sender, Player targetPlayer, String serverName) {

        //player.teleport(Bukkit.getWorld(serverName).getSpawnLocation());
        targetPlayer.sendMessage(ChatColor.DARK_PURPLE + sender.getName() + " summoned you to " + serverName + ".");
        sender.sendMessage(ChatColor.DARK_PURPLE + "Summoned " + targetPlayer.getName() + " to " + serverName + ".");
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

