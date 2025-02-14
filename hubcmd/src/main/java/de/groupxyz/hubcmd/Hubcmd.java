package de.groupxyz.hubcmd;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class Hubcmd extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This Command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        String targetServer = "hub";

        if (label.equalsIgnoreCase("lobby") || label.equalsIgnoreCase("l") || label.equalsIgnoreCase("hub")) {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                try {
                    out.writeUTF("Connect");
                    out.writeUTF(targetServer);
                } catch (Exception f) {
                    f.printStackTrace();
                }
                player.sendPluginMessage(Hubcmd.getPlugin(Hubcmd.class), "BungeeCord", b.toByteArray());
            } catch (org.bukkit.plugin.messaging.ChannelNotRegisteredException f) {
                Bukkit.getLogger().warning("ERROR - Usage of bungeecord connect is not possible!");
            }

        }

        return true;
    }
}

